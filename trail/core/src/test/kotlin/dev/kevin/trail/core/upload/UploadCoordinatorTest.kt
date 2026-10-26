package dev.kevin.trail.core.upload

import dev.kevin.trail.core.batch.BatchPlanner
import dev.kevin.trail.core.model.OutboxKind
import dev.kevin.trail.core.model.OutboxRecord
import dev.kevin.trail.core.model.PrivateZone
import dev.kevin.trail.core.model.ZoneAction
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class UploadCoordinatorTest {
    private val server = MockWebServer()
    private val outbox = MemoryOutbox()
    private lateinit var client: OkHttpWebhookClient
    private lateinit var coordinator: UploadCoordinator

    @Before
    fun setUp() {
        val held = HeldCertificate.Builder()
            .addSubjectAlternativeName(InetAddress.getByName("localhost").canonicalHostName)
            .addSubjectAlternativeName("localhost")
            .build()
        val certificates = HandshakeCertificates.Builder()
            .heldCertificate(held)
            .addTrustedCertificate(held.certificate)
            .build()
        server.useHttps(certificates.sslSocketFactory(), false)
        server.start()
        val http = OkHttpClient.Builder()
            .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
        client = OkHttpWebhookClient(http)
        coordinator = UploadCoordinator(outbox, client, BatchPlanner { "batch-fixed" })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun postsOwnTracksJsonAndMarksSentOnlyAfter2xx() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(204))
        outbox.items += location(id = 1, tst = 1_700_000_111)
        val failing = coordinator.upload(config())
        assertTrue(failing is UploadOutcome.Retry)
        assertEquals("batch-fixed", outbox.items.single().value.batchId)
        assertEquals(1, server.requestCount)
        val first = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("Bearer diary-token", first.getHeader("Authorization"))
        assertTrue(first.body.readUtf8().contains("\"_type\":\"location\""))
        assertFalse(outbox.items.single().sent)

        val ok = coordinator.upload(config())
        assertTrue(ok is UploadOutcome.Success)
        assertEquals("batch-fixed", (ok as UploadOutcome.Success).batchId)
        assertTrue(outbox.items.single().sent)
        val secondBody = server.takeRequest(1, TimeUnit.SECONDS)!!.body.readUtf8()
        val batchId = Json.parseToJsonElement(secondBody).jsonObject
            .getValue("trail_meta").jsonObject.getValue("batch_id").jsonPrimitive.content
        assertEquals("batch-fixed", batchId)

        val empty = coordinator.upload(config())
        assertEquals(UploadOutcome.NothingToSend, empty)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun httpUrlIsRejectedBeforeARequest() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        outbox.items += location(1, 50)
        val outcome = coordinator.upload(config(url = "http://127.0.0.1/hook"))
        assertTrue(outcome is UploadOutcome.PermanentFailure)
        assertEquals(0, server.requestCount)
        assertFalse(outbox.items.single().sent)
        assertFalse(outcome.toString().contains("diary-token"))
    }

    @Test
    fun meteredAndLowBatteryDeferUnlessForced() = runBlocking {
        outbox.items += location(1, 50)
        val metered = coordinator.upload(config(metered = true, wifiOnly = true))
        assertEquals(UploadOutcome.Retry("metered"), metered)
        val low = coordinator.upload(config(battery = 5, charging = false))
        assertEquals(UploadOutcome.Retry("low-battery"), low)
        server.enqueue(MockResponse().setResponseCode(200))
        val forced = coordinator.upload(config(battery = 5, charging = false, force = true))
        assertTrue(forced is UploadOutcome.Success)
    }

    @Test
    fun dropZoneSuppressesWithoutUploadingThePoint() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        outbox.items += location(id = 7, tst = 90, latitude = 37.0, longitude = -122.0)
        outbox.items += location(id = 8, tst = 91, latitude = 40.0, longitude = -70.0)
        val outcome = coordinator.upload(
            config(
                zones = listOf(
                    PrivateZone(
                        name = "Home",
                        latitude = 37.0,
                        longitude = -122.0,
                        radiusMeters = 200.0,
                        action = ZoneAction.DROP,
                    ),
                ),
            ),
        )
        assertTrue(outcome is UploadOutcome.Success)
        assertTrue(outbox.items.first { it.id == 7L }.suppressed)
        assertTrue(outbox.items.first { it.id == 8L }.sent)
        val body = server.takeRequest(1, TimeUnit.SECONDS)!!.body.readUtf8()
        assertFalse(body.contains("37.0"))
        assertTrue(body.contains("40.0"))
    }

    @Test
    fun failureReasonDoesNotEchoTheWebhookOrToken() = runBlocking {
        val secretUrl = "https://hooks.example.com/very-private-path"
        val token = "Bearer super-secret-token"
        val leaking = WebhookClient {
            WebhookResult(0, "boom $secretUrl $token")
        }
        val local = UploadCoordinator(outbox, leaking, BatchPlanner { "b" })
        outbox.items += location(1, 10)
        val outcome = local.upload(config(url = secretUrl, authorization = token))
        val reason = (outcome as UploadOutcome.Retry).reason
        assertFalse(reason.contains("very-private-path"))
        assertFalse(reason.contains("super-secret-token"))
    }

    private fun config(
        url: String = serverHttps(),
        authorization: String = "Bearer diary-token",
        metered: Boolean = false,
        wifiOnly: Boolean = false,
        battery: Int? = 80,
        charging: Boolean = false,
        force: Boolean = false,
        zones: List<PrivateZone> = emptyList(),
    ): UploadConfig = UploadConfig(
        webhookUrl = url,
        authorizationHeader = authorization,
        deviceId = "device-1",
        appVersion = "1.0.0",
        trackerId = "KV",
        certificatePinSha256 = null,
        zones = zones,
        wifiOnly = wifiOnly,
        deferOnLowBattery = true,
        batteryPercent = battery,
        charging = charging,
        networkConnected = true,
        networkMetered = metered,
        force = force,
    )

    private fun serverHttps(): String = server.url("/hook").toString().replaceFirst("http://", "https://")

    private fun location(id: Long, tst: Long, latitude: Double = 37.5, longitude: Double = -122.2) = StoredRecord(
        OutboxRecord(
            id = id,
            kind = OutboxKind.LOCATION,
            tst = tst,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = 8,
            altitudeMeters = 10.0,
            speedMps = 1.0,
            bearingDegrees = 20.0,
            batteryPercent = 70,
            connection = "w",
            batchId = null,
        ),
    )
}

private class StoredRecord(
    record: OutboxRecord,
    var sent: Boolean = false,
    var suppressed: Boolean = false,
) {
    var value: OutboxRecord = record
    val id: Long get() = value.id
}

private class MemoryOutbox : Outbox {
    val items = mutableListOf<StoredRecord>()

    override suspend fun pending(): List<OutboxRecord> =
        items.filter { !it.sent && !it.suppressed }.map { it.value }

    override suspend fun assignBatch(records: List<OutboxRecord>, batchId: String) {
        val keys = records.map { it.kind to it.id }.toSet()
        items.filter { (it.value.kind to it.value.id) in keys }.forEach { stored ->
            stored.value = stored.value.copy(batchId = batchId)
        }
    }

    override suspend fun markSent(records: List<OutboxRecord>, batchId: String) {
        val keys = records.map { it.kind to it.id }.toSet()
        items.filter { (it.value.kind to it.value.id) in keys }.forEach {
            it.sent = true
            it.value = it.value.copy(batchId = batchId)
        }
    }

    override suspend fun markSuppressed(records: List<OutboxRecord>) {
        val keys = records.map { it.kind to it.id }.toSet()
        items.filter { (it.value.kind to it.value.id) in keys }.forEach { it.suppressed = true }
    }
}
