package dev.kevin.trail.core.upload

import dev.kevin.trail.core.batch.BatchPlanner
import dev.kevin.trail.core.batch.HttpDisposition
import dev.kevin.trail.core.batch.RetryPolicy
import dev.kevin.trail.core.model.OutboxRecord
import dev.kevin.trail.core.model.PrivateZone
import dev.kevin.trail.core.payload.OwnTracksPayload
import dev.kevin.trail.core.payload.TrailMeta
import dev.kevin.trail.core.retention.RetentionPolicy
import dev.kevin.trail.core.security.Redactor
import dev.kevin.trail.core.security.UrlValidation
import dev.kevin.trail.core.security.WebhookUrlValidator
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI

data class UploadConfig(
    val webhookUrl: String,
    val authorizationHeader: String,
    val deviceId: String,
    val appVersion: String,
    val trackerId: String,
    val certificatePinSha256: String?,
    val zones: List<PrivateZone>,
    val wifiOnly: Boolean,
    val deferOnLowBattery: Boolean,
    val lowBatteryPercent: Int = RetentionPolicy.LOW_BATTERY_PERCENT,
    val batteryPercent: Int?,
    val charging: Boolean,
    val networkConnected: Boolean,
    val networkMetered: Boolean,
    val force: Boolean = false,
) {
    override fun toString(): String =
        "UploadConfig(webhook=$REDACTED, authorization=$REDACTED, deviceId=$deviceId, " +
            "intervalForce=$force, wifiOnly=$wifiOnly)"

    private companion object {
        const val REDACTED = "[redacted]"
    }
}

sealed interface UploadOutcome {
    data class Success(val batchId: String, val count: Int) : UploadOutcome

    data object NothingToSend : UploadOutcome

    data object NotConfigured : UploadOutcome

    data class Retry(val reason: String) : UploadOutcome

    data class PermanentFailure(val reason: String) : UploadOutcome
}

interface Outbox {
    suspend fun pending(): List<OutboxRecord>
    suspend fun assignBatch(records: List<OutboxRecord>, batchId: String)
    suspend fun markSent(records: List<OutboxRecord>, batchId: String)
    suspend fun markSuppressed(records: List<OutboxRecord>)
}

data class WebhookRequest(
    val url: String,
    val authorization: String,
    val jsonBody: String,
    val certificatePinSha256: String?,
)

data class WebhookResult(
    val code: Int,
    val errorMessage: String? = null,
)

fun interface WebhookClient {
    fun post(request: WebhookRequest): WebhookResult
}

class OkHttpWebhookClient(
    private val baseClient: OkHttpClient,
) : WebhookClient {
    override fun post(request: WebhookRequest): WebhookResult {
        when (val validation = WebhookUrlValidator.validate(request.url)) {
            is UrlValidation.Invalid -> return WebhookResult(0, validation.reason)
            is UrlValidation.Ok -> Unit
        }
        val client = clientFor(request)
        val body = request.jsonBody.toRequestBody(JSON)
        val builder = Request.Builder()
            .url(request.url)
            .header("Content-Type", "application/json")
            .post(body)
        if (request.authorization.isNotBlank()) {
            builder.header("Authorization", request.authorization)
        }
        return try {
            client.newCall(builder.build()).execute().use { response ->
                WebhookResult(response.code)
            }
        } catch (io: IOException) {
            WebhookResult(
                code = 0,
                errorMessage = Redactor.scrub(
                    io.message ?: "network error",
                    listOf(request.url, request.authorization),
                ),
            )
        }
    }

    private fun clientFor(request: WebhookRequest): OkHttpClient {
        val pin = WebhookUrlValidator.normalizePin(request.certificatePinSha256) ?: return baseClient
        val host = URI(request.url).host ?: return baseClient
        val pinner = CertificatePinner.Builder().add(host, pin).build()
        return baseClient.newBuilder().certificatePinner(pinner).build()
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

class UploadCoordinator(
    private val outbox: Outbox,
    private val client: WebhookClient,
    private val planner: BatchPlanner,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) {
    suspend fun upload(config: UploadConfig): UploadOutcome {
        if (config.webhookUrl.isBlank()) return UploadOutcome.NotConfigured
        when (val validation = WebhookUrlValidator.validate(config.webhookUrl)) {
            is UrlValidation.Invalid -> return UploadOutcome.PermanentFailure(validation.reason)
            is UrlValidation.Ok -> Unit
        }
        if (WebhookUrlValidator.pinIsPresentButInvalid(config.certificatePinSha256)) {
            return UploadOutcome.PermanentFailure("Certificate pin must look like sha256/…")
        }
        if (!config.force) {
            if (!config.networkConnected) return UploadOutcome.Retry("offline")
            if (config.wifiOnly && config.networkMetered) return UploadOutcome.Retry("metered")
            val battery = config.batteryPercent
            val lowBattery = battery != null && battery < config.lowBatteryPercent
            if (config.deferOnLowBattery && !config.charging && lowBattery) {
                return UploadOutcome.Retry("low-battery")
            }
        }
        var last: UploadOutcome = UploadOutcome.NothingToSend
        repeat(MAX_BATCHES_PER_RUN) {
            val pending = outbox.pending()
            if (pending.isEmpty()) return last
            val plan = planner.plan(pending, config.zones) ?: return last
            if (plan.suppressed.isNotEmpty()) outbox.markSuppressed(plan.suppressed)
            if (plan.toSend.isEmpty()) {
                last = UploadOutcome.NothingToSend
                return@repeat
            }
            outbox.assignBatch(plan.toSend, plan.batchId)
            val payload = OwnTracksPayload.encode(
                meta = TrailMeta(
                    deviceId = config.deviceId,
                    batchId = plan.batchId,
                    appVersion = config.appVersion,
                ),
                records = plan.toSend,
                trackerId = RetentionPolicy.normalizeTrackerId(config.trackerId),
            )
            val result = client.post(
                WebhookRequest(
                    url = config.webhookUrl,
                    authorization = config.authorizationHeader,
                    jsonBody = payload,
                    certificatePinSha256 = WebhookUrlValidator.normalizePin(config.certificatePinSha256),
                ),
            )
            val outcome = when (retryPolicy.disposition(result.code)) {
                HttpDisposition.SUCCESS -> {
                    outbox.markSent(plan.toSend, plan.batchId)
                    UploadOutcome.Success(plan.batchId, plan.toSend.size)
                }
                HttpDisposition.RETRY -> UploadOutcome.Retry(safeReason(result, config))
                HttpDisposition.PERMANENT -> UploadOutcome.PermanentFailure(safeReason(result, config))
            }
            last = outcome
            if (outcome !is UploadOutcome.Success) return outcome
        }
        return last
    }

    private fun safeReason(result: WebhookResult, config: UploadConfig): String {
        val raw = result.errorMessage ?: "http ${result.code}"
        return Redactor.scrub(raw, listOf(config.webhookUrl, config.authorizationHeader)).take(MAX_REASON)
    }

    private companion object {
        const val MAX_BATCHES_PER_RUN = 20
        const val MAX_REASON = 200
    }
}
