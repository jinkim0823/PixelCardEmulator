package io.github.realtvop.pixelcardemulator

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

/** Editor target for a card that does not exist yet. Never collides with an id, which is a UUID. */
private const val NewCard = "new"

class MainActivity : ComponentActivity() {
    private val viewModel: CardsViewModel by viewModels()
    private val wirelessPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (requiredWirelessPermissions().all { result[it] == true || checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            beginWirelessDebuggingSetup()
        } else {
            viewModel.reportPermissionSetupError(R.string.wireless_permissions_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelCardTheme {
                // Two screens only, so one value stands in for a navigation library: null shows the
                // list, anything else opens the editor on the card with that id, or on a new card.
                var editorTarget by rememberSaveable { mutableStateOf<String?>(null) }
                BackHandler(enabled = editorTarget != null) { editorTarget = null }

                val slide = MaterialTheme.motionScheme.defaultEffectsSpec<IntOffset>()
                val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                AnimatedContent(
                    targetState = editorTarget,
                    transitionSpec = {
                        // The editor enters from the end edge and leaves the way it came.
                        val direction = if (targetState != null) 1 else -1
                        (slideInHorizontally(slide) { direction * it / 6 } + fadeIn(fade)) togetherWith
                            (slideOutHorizontally(slide) { -direction * it / 6 } + fadeOut(fade))
                    },
                    label = "screen",
                ) { target ->
                    if (target == null) {
                        CardListScreen(
                            cards = viewModel.cards,
                            activeId = viewModel.activeId,
                            defaultId = viewModel.defaultId,
                            onToggleActive = viewModel::toggleActive,
                            onToggleDefault = viewModel::toggleDefault,
                            onAddCard = { editorTarget = NewCard },
                            onEditCard = { editorTarget = it },
                            forceRaw = viewModel.forceRaw,
                            onToggleForceRaw = viewModel::toggleForceRaw,
                            showPermissionBanner = !viewModel.secureSettingsGranted && !viewModel.forceRaw,
                            onPermissionBannerClick = viewModel::showPermissionPrompt,
                        )
                    } else {
                        // Remembered so the screen still shows the card while it animates out after a delete.
                        val card = remember { viewModel.cards.find { it.id == target } }
                        CardEditorScreen(
                            card = card,
                            onBack = { editorTarget = null },
                            onSave = { name, uid ->
                                if (card == null) viewModel.add(name, uid) else viewModel.update(card.id, name, uid)
                                editorTarget = null
                            },
                            onDelete = {
                                card?.let { viewModel.delete(it.id) }
                                editorTarget = null
                            },
                        )
                    }
                }

                val emulatorState = viewModel.emulatorUiState
                if (emulatorState.permissionRequired) {
                    SecureSettingsPermissionDialog(
                        command = viewModel.grantCommand,
                        busy = emulatorState.permissionGrantBusy,
                        status = emulatorState.permissionGrantStatus,
                        error = emulatorState.permissionGrantError,
                        onShizuku = viewModel::grantPermissionWithShizuku,
                        onWirelessDebugging = ::requestWirelessDebuggingSetup,
                        onDismiss = viewModel::dismissPermissionPrompt,
                    )
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(1000)
                            viewModel.refreshPermission()
                        }
                    }
                }
                emulatorState.error?.let { message ->
                    AlertDialog(
                        onDismissRequest = viewModel::dismissError,
                        title = { Text(getString(R.string.nfc_operation_failed)) },
                        text = { Text(message) },
                        confirmButton = {
                            TextButton(onClick = viewModel::dismissError) { Text(getString(R.string.ok)) }
                        },
                    )
                }
                if (emulatorState.experimentalRawConfirmationRequired) {
                    AlertDialog(
                        onDismissRequest = viewModel::dismissExperimentalRawConfirmation,
                        title = { Text(getString(R.string.experimental_raw_title)) },
                        text = { Text(getString(R.string.experimental_raw_message)) },
                        confirmButton = {
                            TextButton(onClick = viewModel::confirmExperimentalRaw) {
                                Text(getString(R.string.continue_action))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = viewModel::dismissExperimentalRawConfirmation) {
                                Text(getString(R.string.cancel))
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.attachActivity(this)
        viewModel.refreshPermission()
    }

    override fun onPause() {
        viewModel.detachActivity(this)
        super.onPause()
    }

    private fun requestWirelessDebuggingSetup() {
        val missing = requiredWirelessPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            beginWirelessDebuggingSetup()
        } else {
            wirelessPermissions.launch(missing.toTypedArray())
        }
    }

    private fun requiredWirelessPermissions(): List<String> = buildList {
        add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 37) add(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }

    private fun beginWirelessDebuggingSetup() {
        WirelessDebuggingService.begin(this)
        startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    }
}

@androidx.compose.runtime.Composable
private fun SecureSettingsPermissionDialog(
    command: String,
    busy: Boolean,
    status: String?,
    error: String?,
    onShizuku: () -> Unit,
    onWirelessDebugging: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permission_required)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.permission_explanation))
                FilledTonalButton(
                    onClick = onShizuku,
                    enabled = !busy,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.grant_with_shizuku))
                }
                FilledTonalButton(
                    onClick = onWirelessDebugging,
                    enabled = !busy,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.grant_with_wireless_debugging))
                }
                status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                HorizontalDivider(modifier = androidx.compose.ui.Modifier.padding(top = 4.dp))
                Text(stringResource(R.string.computer_adb_explanation, command))
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ADB command", command))
                    },
                    enabled = !busy,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.copy_command))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        },
    )
}
