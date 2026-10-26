package dev.kevin.trail.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dev.kevin.trail.location.DeviceSignals
import dev.kevin.trail.location.DeviceStatus
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.kevin.trail.core.batch.BatchPlanner
import dev.kevin.trail.core.upload.OkHttpWebhookClient
import dev.kevin.trail.core.upload.UploadCoordinator
import dev.kevin.trail.core.upload.WebhookClient
import dev.kevin.trail.data.db.DatabaseFactory
import dev.kevin.trail.data.db.TrailDatabase
import dev.kevin.trail.data.repo.TrackingRepository
import dev.kevin.trail.data.security.DatabasePassphrase
import dev.kevin.trail.data.security.SecretStore
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun secretStore(@ApplicationContext context: Context): SecretStore = SecretStore(context)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context, secrets: SecretStore): TrailDatabase {
        val passphrase = DatabasePassphrase(secrets).getOrCreate()
        return DatabaseFactory(context, passphrase).create()
    }

    @Provides
    @Singleton
    fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun webhookClient(client: OkHttpClient): WebhookClient = OkHttpWebhookClient(client)

    @Provides
    @Singleton
    fun coordinator(repository: TrackingRepository, client: WebhookClient): UploadCoordinator =
        UploadCoordinator(
            outbox = repository,
            client = client,
            planner = BatchPlanner { UUID.randomUUID().toString() },
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StatusModule {
    @Binds
    abstract fun deviceStatus(signals: DeviceSignals): DeviceStatus
}
