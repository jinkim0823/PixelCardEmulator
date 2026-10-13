package github.realtvop.pixelcardemulator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/** Editor target for a card that does not exist yet. Never collides with an id, which is a UUID. */
private const val NewCard = "new"

class MainActivity : ComponentActivity() {
    private val viewModel: CardsViewModel by viewModels()

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
                            operationStatus = viewModel.emulatorUiState.status,
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
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermission()
    }
}

@androidx.compose.runtime.Composable
private fun SecureSettingsPermissionDialog(
    command: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.permission_required)) },
        text = { Text(context.getString(R.string.permission_explanation, command)) },
        confirmButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ADB command", command))
                },
            ) {
                Text(context.getString(R.string.copy_command))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(context.getString(R.string.cancel)) }
        },
    )
}
