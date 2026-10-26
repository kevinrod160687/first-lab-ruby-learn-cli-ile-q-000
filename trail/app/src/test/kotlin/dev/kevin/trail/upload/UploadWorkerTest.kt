package dev.kevin.trail.upload

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import dev.kevin.trail.core.batch.BatchPlanner
import dev.kevin.trail.core.upload.OkHttpWebhookClient
import dev.kevin.trail.core.upload.UploadCoordinator
import dev.kevin.trail.data.TestTrailApplication
import dev.kevin.trail.data.db.PointEntity
import dev.kevin.trail.data.db.TrailDatabase
import dev.kevin.trail.data.db.UploadStatus
import dev.kevin.trail.data.repo.SettingsRepository
import dev.kevin.trail.data.repo.TrackingRepository
import dev.kevin.trail.data.security.SecretStore
import dev.kevin.trail.location.DeviceStatus
import kotlinx.coroutines.runBlocking
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Drives [UploadWorker] against a local HTTPS server. Room is the real DAO
 * layer (in memory, unencrypted — Robolectric cannot load SQLCipher). The
 * webhook URL and token live in ordinary preferences here so the test does
 * not need the Android Keystore; production uses [SecretStore]'s encrypted file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestTrailApplication::class)
class UploadWorkerTest {
    private val server = MockWebServer()
    private lateinit var database: TrailDatabase
    private lateinit var repository: TrackingRepository
    private lateinit var settings: SettingsRepository

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
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, TrailDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TrackingRepository(database)
        val preferences = context.getSharedPreferences("trail-test-secrets", Context.MODE_PRIVATE)
        settings = SettingsRepository(SecretStore(preferences))
        settings.update {
            it.copy(
                webhookUrl = server.url("/hook").toString().replaceFirst("http://", "https://"),
                authorizationHeader = "Bearer diary-token",
                wifiOnly = false,
                deferOnLowBattery = false,
            )
        }
        httpClient = OkHttpClient.Builder()
            .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    private lateinit var httpClient: OkHttpClient

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
    }

    @Test
    fun marksSentOnlyAfter2xxAndReusesTheBatchId() = runBlocking {
        repository.record(samplePoint())
        server.enqueue(MockResponse().setResponseCode(500))
        val worker = worker()
        val retry = worker.doWork()
        assertEquals(ListenableWorker.Result.retry(), retry)
        assertEquals(UploadStatus.PENDING, database.points().all().single().uploadStatus)
        assertEquals("batch-worker", database.points().all().single().batchId)
        val first = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("Bearer diary-token", first.getHeader("Authorization"))
        val firstBody = first.body.readUtf8()
        assertTrue(firstBody.contains("\"_type\":\"location\""))
        assertTrue(firstBody.contains("\"batch_id\":\"batch-worker\""))
        assertFalse(firstBody.contains("diary-token"))

        server.enqueue(MockResponse().setResponseCode(204))
        val success = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), success)
        assertEquals(UploadStatus.SENT, database.points().all().single().uploadStatus)
        val secondBody = server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8()
        assertTrue(secondBody.contains("\"batch_id\":\"batch-worker\""))
        assertEquals(2, server.requestCount)
    }

    private fun worker(): UploadWorker {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val coordinator = UploadCoordinator(
            outbox = repository,
            client = OkHttpWebhookClient(httpClient),
            planner = BatchPlanner { "batch-worker" },
        )
        val signals = object : DeviceStatus {
            override fun batteryPercent(): Int = 80
            override fun charging(): Boolean = false
            override fun connected(): Boolean = true
            override fun metered(): Boolean = false
        }
        return TestListenableWorkerBuilder<UploadWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = UploadWorker(
                    appContext,
                    workerParameters,
                    coordinator,
                    settings,
                    repository,
                    signals,
                )
            })
            .setInputData(workDataOf(UploadWorker.KEY_FORCE to false))
            .build()
    }

    private fun samplePoint() = PointEntity(
        tst = 1_700_000_111,
        latitude = 37.5,
        longitude = -122.2,
        accuracyMeters = 8,
        altitudeMeters = 10.0,
        speedMps = 1.2,
        bearingDegrees = 20.0,
        batteryPercent = 70,
        connection = "w",
        activity = "WALK",
    )
}
