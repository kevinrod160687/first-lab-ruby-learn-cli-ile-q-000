package dev.kevin.trail.upload

import dev.kevin.trail.core.upload.UploadOutcome
import dev.kevin.trail.location.ActivityTransitionMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class UploadWorkerLogicTest {
    @Test
    fun workerResultFollowsHttpDisposition() {
        assertEquals(
            androidx.work.ListenableWorker.Result.success(),
            UploadWorker.resultFor(UploadOutcome.Success("batch", 2)),
        )
        assertEquals(
            androidx.work.ListenableWorker.Result.success(),
            UploadWorker.resultFor(UploadOutcome.NothingToSend),
        )
        assertEquals(
            androidx.work.ListenableWorker.Result.success(),
            UploadWorker.resultFor(UploadOutcome.NotConfigured),
        )
        assertEquals(
            androidx.work.ListenableWorker.Result.retry(),
            UploadWorker.resultFor(UploadOutcome.Retry("http 503")),
        )
        assertEquals(
            androidx.work.ListenableWorker.Result.failure(),
            UploadWorker.resultFor(UploadOutcome.PermanentFailure("http 401")),
        )
    }

    @Test
    fun activityTransitionsSelectSampling() {
        val still = ActivityTransitionMapper.map(ActivityTransitionMapper.STILL, ActivityTransitionMapper.ENTER)
        assertEquals(dev.kevin.trail.core.sampling.SamplingMode.STILL, still?.second)
        val walk = ActivityTransitionMapper.map(ActivityTransitionMapper.WALKING, ActivityTransitionMapper.ENTER)
        assertEquals(dev.kevin.trail.core.model.ActivityMode.WALK, walk?.first)
        assertEquals(dev.kevin.trail.core.sampling.SamplingMode.MOVING, walk?.second)
        val drive = ActivityTransitionMapper.map(ActivityTransitionMapper.IN_VEHICLE, ActivityTransitionMapper.ENTER)
        assertEquals(dev.kevin.trail.core.model.ActivityMode.DRIVE, drive?.first)
        val resumed = ActivityTransitionMapper.map(ActivityTransitionMapper.STILL, ActivityTransitionMapper.EXIT)
        assertEquals(dev.kevin.trail.core.sampling.SamplingMode.MOVING, resumed?.second)
    }
}
