package dev.kevin.trail.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kevin.trail.R
import dev.kevin.trail.core.model.Retention
import dev.kevin.trail.core.model.ZoneAction
import dev.kevin.trail.export.shareTextFile
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember(state.webhookUrl) { mutableStateOf(state.webhookUrl) }
    var auth by remember(state.authorizationHeader) { mutableStateOf(state.authorizationHeader) }
    var pin by remember(state.certificatePin) { mutableStateOf(state.certificatePin) }
    var showAuth by remember { mutableStateOf(false) }
    var webhookMessage by remember { mutableStateOf<String?>(null) }
    var showZone by remember { mutableStateOf(false) }
    var showWipe by remember { mutableStateOf(false) }
    val power = context.getSystemService(PowerManager::class.java)
    val unrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.webhook_url)) },
                    placeholder = { Text(stringResource(R.string.webhook_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
            item {
                OutlinedTextField(
                    value = auth,
                    onValueChange = { auth = it },
                    label = { Text(stringResource(R.string.webhook_auth)) },
                    placeholder = { Text(stringResource(R.string.auth_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showAuth) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showAuth = !showAuth }) {
                            Text(if (showAuth) "Hide" else "Show")
                        }
                    },
                )
            }
            item {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text(stringResource(R.string.pin_title)) },
                    placeholder = { Text(stringResource(R.string.pin_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val saved = viewModel.saveWebhook(url, auth, pin)
                        webhookMessage = if (saved) null else "Use an https:// address. The pin, if set, starts with sha256/."
                    }) { Text(stringResource(R.string.webhook_save)) }
                    OutlinedButton(onClick = {
                        url = ""
                        auth = ""
                        pin = ""
                        viewModel.forgetWebhook()
                    }) { Text(stringResource(R.string.webhook_forget)) }
                }
                webhookMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            item {
                Text(stringResource(R.string.interval_title))
                Text(stringResource(R.string.interval_value, state.uploadIntervalMinutes))
                Slider(
                    value = state.uploadIntervalMinutes.toFloat(),
                    onValueChange = { viewModel.setInterval(it.roundToInt()) },
                    valueRange = 15f..120f,
                    steps = 6,
                )
            }
            item { ToggleRow(stringResource(R.string.wifi_only), state.wifiOnly, viewModel::setWifiOnly) }
            item { ToggleRow(stringResource(R.string.defer_battery), state.deferOnLowBattery, viewModel::setDeferBattery) }
            item {
                Text(stringResource(R.string.retention_title))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RetentionChip(Retention.DAYS_30, R.string.retention_30, state.retention, viewModel::setRetention)
                    RetentionChip(Retention.DAYS_90, R.string.retention_90, state.retention, viewModel::setRetention)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RetentionChip(Retention.DAYS_365, R.string.retention_365, state.retention, viewModel::setRetention)
                    RetentionChip(Retention.FOREVER, R.string.retention_forever, state.retention, viewModel::setRetention)
                }
            }
            item {
                var tid by remember(state.trackerId) { mutableStateOf(state.trackerId) }
                OutlinedTextField(
                    value = tid,
                    onValueChange = {
                        tid = it.take(2)
                        if (tid.length == 2) viewModel.setTrackerId(tid)
                    },
                    label = { Text(stringResource(R.string.tracker_id)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                Text(stringResource(R.string.zones_title), style = MaterialTheme.typography.titleMedium)
                if (state.zones.isEmpty()) Text(stringResource(R.string.zones_empty))
                state.zones.forEach { zone ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${zone.name} · ${zone.action.name.lowercase()} · ${zone.radiusMeters.toInt()} m")
                        TextButton(onClick = { viewModel.deleteZone(zone.id) }) { Text("Remove") }
                    }
                }
                OutlinedButton(onClick = { showZone = true }) { Text(stringResource(R.string.zones_add)) }
            }
            item {
                Text(stringResource(R.string.export_note))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val body = viewModel.exportGpx()
                            shareTextFile(context, "trail-${LocalDate.now()}.gpx", "application/gpx+xml", body)
                        }
                    }) { Text(stringResource(R.string.export_gpx)) }
                    OutlinedButton(onClick = {
                        scope.launch {
                            val body = viewModel.exportJson()
                            shareTextFile(context, "trail-${LocalDate.now()}.json", "application/json", body)
                        }
                    }) { Text(stringResource(R.string.export_json)) }
                }
            }
            item {
                Text(stringResource(R.string.battery_title), style = MaterialTheme.typography.titleMedium)
                Text(if (unrestricted) stringResource(R.string.battery_done) else stringResource(R.string.battery_body))
                if (!unrestricted) {
                    Button(onClick = {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        )
                        context.startActivity(intent)
                    }) { Text(stringResource(R.string.battery_action)) }
                }
            }
            item {
                OutlinedButton(onClick = { showWipe = true }) {
                    Text(stringResource(R.string.wipe), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (showZone) {
        ZoneDialog(
            latestLatitude = state.latestLatitude,
            latestLongitude = state.latestLongitude,
            onDismiss = { showZone = false },
            onSave = { name, lat, lon, radius, action ->
                viewModel.addZone(name, lat, lon, radius, action)
                showZone = false
            },
        )
    }
    if (showWipe) {
        AlertDialog(
            onDismissRequest = { showWipe = false },
            title = { Text(stringResource(R.string.wipe_title)) },
            text = { Text(stringResource(R.string.wipe_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showWipe = false
                    viewModel.wipe()
                }) { Text(stringResource(R.string.wipe_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showWipe = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun RetentionChip(
    value: Retention,
    label: Int,
    selected: Retention,
    onSelect: (Retention) -> Unit,
) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(stringResource(label)) },
    )
}

@Composable
private fun ZoneDialog(
    latestLatitude: Double?,
    latestLongitude: Double?,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Double, ZoneAction) -> Unit,
) {
    var name by remember { mutableStateOf("Home") }
    var lat by remember { mutableStateOf(latestLatitude?.toString().orEmpty()) }
    var lon by remember { mutableStateOf(latestLongitude?.toString().orEmpty()) }
    var radius by remember { mutableStateOf("150") }
    var drop by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.zones_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.zone_name)) })
                OutlinedTextField(lat, { lat = it }, label = { Text(stringResource(R.string.zone_lat)) })
                OutlinedTextField(lon, { lon = it }, label = { Text(stringResource(R.string.zone_lon)) })
                OutlinedTextField(radius, { radius = it }, label = { Text(stringResource(R.string.zone_radius)) })
                if (latestLatitude != null && latestLongitude != null) {
                    TextButton(onClick = {
                        lat = latestLatitude.toString()
                        lon = latestLongitude.toString()
                    }) { Text(stringResource(R.string.zone_use_latest)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(drop, { drop = true }, label = { Text(stringResource(R.string.zone_drop)) })
                    FilterChip(!drop, { drop = false }, label = { Text(stringResource(R.string.zone_coarsen)) })
                }
                if (error) Text("Enter a latitude, longitude, and radius.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedLat = lat.toDoubleOrNull()
                val parsedLon = lon.toDoubleOrNull()
                val parsedRadius = radius.toDoubleOrNull()
                if (parsedLat == null || parsedLon == null || parsedRadius == null) {
                    error = true
                } else {
                    onSave(
                        name,
                        parsedLat,
                        parsedLon,
                        parsedRadius,
                        if (drop) ZoneAction.DROP else ZoneAction.COARSEN,
                    )
                }
            }) { Text(stringResource(R.string.zone_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
