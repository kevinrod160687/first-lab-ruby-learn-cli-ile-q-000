package dev.kevin.trail.location

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kevin.trail.core.model.ActivityMode
import dev.kevin.trail.core.sampling.SamplingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SamplingModeStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val modeState = MutableStateFlow(readMode())
    private val activityState = MutableStateFlow(readActivity())

    val mode: StateFlow<SamplingMode> = modeState.asStateFlow()

    fun currentMode(): SamplingMode = modeState.value

    fun currentActivity(): ActivityMode = activityState.value

    fun update(activity: ActivityMode, sampling: SamplingMode) {
        activityState.value = activity
        modeState.value = sampling
        preferences.edit()
            .putString(KEY_ACTIVITY, activity.name)
            .putString(KEY_MODE, sampling.name)
            .apply()
    }

    private fun readMode(): SamplingMode =
        preferences.getString(KEY_MODE, null)?.let { runCatching { SamplingMode.valueOf(it) }.getOrNull() }
            ?: SamplingMode.MOVING

    private fun readActivity(): ActivityMode =
        preferences.getString(KEY_ACTIVITY, null)?.let { runCatching { ActivityMode.valueOf(it) }.getOrNull() }
            ?: ActivityMode.UNKNOWN

    companion object {
        private const val PREFS = "trail_sampling"
        private const val KEY_MODE = "mode"
        private const val KEY_ACTIVITY = "activity"
    }
}

object ActivityTransitionMapper {
    const val ENTER = 0
    const val EXIT = 1
    const val IN_VEHICLE = 0
    const val ON_BICYCLE = 1
    const val ON_FOOT = 2
    const val STILL = 3
    const val WALKING = 7
    const val RUNNING = 8

    fun map(activityType: Int, transition: Int): Pair<ActivityMode, SamplingMode>? {
        if (transition == EXIT && activityType == STILL) {
            return ActivityMode.UNKNOWN to SamplingMode.MOVING
        }
        if (transition != ENTER) return null
        return when (activityType) {
            STILL -> ActivityMode.STILL to SamplingMode.STILL
            WALKING, ON_FOOT, RUNNING -> ActivityMode.WALK to SamplingMode.MOVING
            ON_BICYCLE -> ActivityMode.BIKE to SamplingMode.MOVING
            IN_VEHICLE -> ActivityMode.DRIVE to SamplingMode.MOVING
            else -> null
        }
    }
}
