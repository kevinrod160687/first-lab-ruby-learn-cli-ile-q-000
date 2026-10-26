package dev.kevin.trail.ui.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.kevin.trail.R

@Composable
fun BackgroundLocationScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onDone() else denied = true
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.background_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.background_body))
        Button(onClick = {
            if (Build.VERSION.SDK_INT < 29) {
                onDone()
            } else {
                val already = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (already) onDone() else launcher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }) {
            Text(stringResource(R.string.background_continue))
        }
        if (denied) {
            TextButton(onClick = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                )
                context.startActivity(intent)
            }) {
                Text(stringResource(R.string.background_settings))
            }
        }
        TextButton(onClick = onDone) { Text(stringResource(R.string.background_not_now)) }
    }
}
