package dev.kevin.trail.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransitionResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ActivityTransitionReceiver : BroadcastReceiver() {
    @Inject lateinit var sampling: SamplingModeStore

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val event = result.transitionEvents.lastOrNull() ?: return
        val mapped = ActivityTransitionMapper.map(event.activityType, event.transitionType) ?: return
        sampling.update(mapped.first, mapped.second)
    }
}
