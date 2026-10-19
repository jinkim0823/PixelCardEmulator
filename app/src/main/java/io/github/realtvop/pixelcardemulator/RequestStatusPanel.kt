package io.github.realtvop.pixelcardemulator

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun RequestStatusPanel(history: String) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.request_status),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(onClick = {
                val device = "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}"
                val text = "PixelCard SAK Test 0.1-sak08.1\n$device\nRF not measured\n\n$history"
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("NFC request status", text))
            }, enabled = history.isNotBlank()) {
                Text(stringResource(R.string.copy_status))
            }
        }
        Text(
            history.substringBefore("\n\n").ifBlank { stringResource(R.string.no_requests) },
            modifier = Modifier.heightIn(max = 130.dp).verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            stringResource(R.string.saved_status_notice),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
