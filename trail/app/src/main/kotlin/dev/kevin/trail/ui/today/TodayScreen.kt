package dev.kevin.trail.ui.today

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kevin.trail.R
import dev.kevin.trail.ui.map.RouteMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onSettings: () -> Unit,
    onBackgroundRationale: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var fineGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) }
    var backgroundGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 29 || hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        )
    }
    var activityGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 29 || hasPermission(context, Manifest.permission.ACTIVITY_RECOGNITION),
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        activityGranted = Build.VERSION.SDK_INT < 29 ||
            result[Manifest.permission.ACTIVITY_RECOGNITION] == true ||
            hasPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
    }
    if (!fineGranted) {
        PermissionIntro(onContinue = {
            launcher.launch(firstPermissions())
        })
        return
    }
    LaunchedEffect(fineGranted) { viewModel.ensureTracking() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPause by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today_title)) },
                actions = {
                    TextButton(onClick = { if (state.paused) viewModel.resume() else showPause = true }) {
                        Text(stringResource(if (state.paused) R.string.resume else R.string.pause))
                    }
                    IconButton(onClick = onSettings) {
                        Text(stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (state.points.isEmpty()) {
                    Text(stringResource(R.string.no_route_yet), style = MaterialTheme.typography.bodyLarge)
                } else {
                    RouteMap(
                        points = state.points,
                        visits = state.visits,
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                    )
                }
            }
            if (!backgroundGranted) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.background_card))
                            TextButton(onClick = onBackgroundRationale) {
                                Text(stringResource(R.string.background_continue))
                            }
                        }
                    }
                }
            }
            if (!activityGranted) {
                item { Text(stringResource(R.string.activity_card), color = MaterialTheme.colorScheme.secondary) }
            }
            item { StatusCard(state, onUpload = viewModel::uploadNow) }
            if (state.timeline.isEmpty()) {
                item { Text(stringResource(R.string.timeline_empty)) }
            } else {
                items(state.timeline, key = { it.key }) { row ->
                    TimelineCard(row)
                }
            }
        }
    }
    if (showPause) {
        PauseDialog(
            onDismiss = { showPause = false },
            onPause = { hours ->
                showPause = false
                viewModel.pause(hours)
            },
        )
    }
}

@Composable
private fun PermissionIntro(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.permission_body))
        Text(stringResource(R.string.permission_notifications))
        Button(onClick = onContinue) { Text(stringResource(R.string.permission_continue)) }
    }
}

@Composable
private fun StatusCard(state: TodayUiState, onUpload: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.status_last_sync) + ": " + state.lastSyncLabel.ifBlank {
                stringResource(R.string.status_never)
            })
            Text(stringResource(R.string.status_pending) + ": " + state.pendingCount)
            state.lastError?.let {
                Text(stringResource(R.string.status_error) + ": " + it, color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(onClick = onUpload) { Text(stringResource(R.string.upload_now)) }
        }
    }
}

@Composable
private fun TimelineCard(row: TimelineRow) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(titleFor(row.kind), style = MaterialTheme.typography.titleMedium)
            Text(row.detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PauseDialog(onDismiss: () -> Unit, onPause: (Int?) -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pause_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onPause(null) }) { Text(stringResource(R.string.pause_until_resume)) }
                listOf(1, 2, 4, 8).forEach { hours ->
                    TextButton(onClick = { onPause(hours) }) {
                        Text(stringResource(R.string.pause_for_hours, hours))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun titleFor(kind: TimelineKind): String = stringResource(
    when (kind) {
        TimelineKind.STAY -> R.string.stay
        TimelineKind.HERE -> R.string.here
        TimelineKind.WALK -> R.string.walk
        TimelineKind.BIKE -> R.string.bike
        TimelineKind.DRIVE -> R.string.drive
        TimelineKind.OUTING -> R.string.outing
    },
)

private fun firstPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
}.toTypedArray()

private fun hasPermission(context: android.content.Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
