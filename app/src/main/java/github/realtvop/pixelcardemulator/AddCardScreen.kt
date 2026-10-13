package github.realtvop.pixelcardemulator

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.coroutines.delay

private val UidSizes = listOf(4, 7, 10)
private const val MaxNameLength = 32
private const val SimulatedScanMillis = 2500L

/** Keeps the form from stretching across unfolded or landscape screens. */
private val FormMaxWidth = 560.dp

@Composable
fun AddCardScreen(
    onBack: () -> Unit,
    onSave: (name: String, uid: String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val name = rememberTextFieldState()
    val typedUid = rememberTextFieldState()
    var scanMode by rememberSaveable { mutableStateOf(false) }
    var uidSize by rememberSaveable { mutableIntStateOf(UidSizes.first()) }
    var scannedUid by rememberSaveable { mutableStateOf<String?>(null) }

    // Null until the chosen source has produced a complete UID.
    val uid = if (scanMode) scannedUid else typedUid.text.toString().takeIf { it.length == uidSize * 2 }

    // Stands in for a real NFC read: a card "arrives" shortly after scanning starts.
    if (scanMode && scannedUid == null) {
        LaunchedEffect(Unit) {
            delay(SimulatedScanMillis)
            scannedUid = randomUid()
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_card)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            val height = ButtonDefaults.MediumContainerHeight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = { uid?.let { onSave(name.text.toString().trim(), it) } },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .widthIn(max = FormMaxWidth)
                        .fillMaxWidth()
                        .heightIn(min = height),
                    enabled = name.text.isNotBlank() && uid != null,
                    contentPadding = ButtonDefaults.contentPaddingFor(height),
                ) {
                    Text(stringResource(R.string.add_card), style = ButtonDefaults.textStyleFor(height))
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = FormMaxWidth),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                OutlinedTextField(
                    state = name,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.name)) },
                    inputTransformation = InputTransformation.maxLength(MaxNameLength),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    shape = MaterialTheme.shapes.large,
                )

                ConnectedChoice(
                    options = listOf(stringResource(R.string.enter_uid), stringResource(R.string.scan_card)),
                    selectedIndex = if (scanMode) 1 else 0,
                    onSelect = { scanMode = it == 1 },
                )

                AnimatedContent(targetState = scanMode, label = "uidSource") { scanning ->
                    if (scanning) {
                        ScanPanel(scannedUid = scannedUid, onScanAgain = { scannedUid = null })
                    } else {
                        ManualUidEntry(
                            uid = typedUid,
                            size = uidSize,
                            onSizeChange = { newSize ->
                                uidSize = newSize
                                typedUid.edit { if (length > newSize * 2) delete(newSize * 2, length) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualUidEntry(
    uid: TextFieldState,
    size: Int,
    onSizeChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConnectedChoice(
            options = UidSizes.map { pluralStringResource(R.plurals.bytes, it, it) },
            selectedIndex = UidSizes.indexOf(size),
            onSelect = { onSizeChange(UidSizes[it]) },
            // Quieter than the source switch above it, which is the primary choice on this screen.
            colors = ToggleButtonDefaults.colors(
                checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        )

        val complete = uid.text.length == size * 2
        OutlinedTextField(
            state = uid,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            label = { Text(stringResource(R.string.uid_hex)) },
            trailingIcon = if (complete) {
                { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.uid_complete)) }
            } else {
                null
            },
            supportingText = {
                Text(pluralStringResource(R.plurals.uid_progress, size, uid.text.length / 2, size))
            },
            inputTransformation = hexDigits(max = size * 2),
            outputTransformation = BytePairs,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            lineLimits = TextFieldLineLimits.SingleLine,
            shape = MaterialTheme.shapes.large,
        )
    }
}

/** Waits for a card, then confirms the read. The scanned UID is never displayed. */
@Composable
private fun ScanPanel(
    scannedUid: String?,
    onScanAgain: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        AnimatedContent(
            targetState = scannedUid,
            contentAlignment = Alignment.Center,
            label = "scan",
        ) { uid ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Fits either state, so the panel keeps its size when the card is read.
                    .heightIn(min = 264.dp)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                if (uid == null) {
                    LoadingIndicator(Modifier.size(64.dp))
                    Text(stringResource(R.string.scan_ready), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.scan_instruction),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primary, MaterialShapes.Cookie9Sided.toShape()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Text(stringResource(R.string.scan_done), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.uid_size, uid.length / 2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onScanAgain, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.scan_again))
                    }
                }
            }
        }
    }
}

/** A single-choice row of connected toggle buttons. */
@Composable
private fun ConnectedChoice(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    colors: ToggleButtonColors = ToggleButtonDefaults.colors(),
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, label ->
            ToggleButton(
                checked = index == selectedIndex,
                onCheckedChange = { onSelect(index) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = colors,
            ) {
                Text(label, maxLines = 1)
            }
        }
    }
}

/** Keeps only hex digits, so a pasted "04:A2:24" still works, uppercased and capped at [max] digits. */
private fun hexDigits(max: Int) = InputTransformation {
    val typed = asCharSequence().toString()
    val hex = typed.toHex().take(max)
    if (hex != typed) {
        // Replacing the text moves the cursor to the end, so put it back where the user was editing.
        val cursor = typed.take(selection.end).toHex().length.coerceAtMost(hex.length)
        replace(0, length, hex)
        selection = TextRange(cursor)
    }
}

private fun String.toHex() = uppercase().filter { it in "0123456789ABCDEF" }

/** Displays the digits as space-separated bytes without changing the stored text. */
private val BytePairs = OutputTransformation {
    for (index in (length - 1) / 2 * 2 downTo 2 step 2) insert(index, " ")
}

private fun randomUid(): String =
    Random.nextBytes(UidSizes.random()).joinToString("") { "%02X".format(it) }

@PreviewLightDark
@Composable
private fun AddCardScreenPreview() {
    PixelCardTheme {
        AddCardScreen(onBack = {}, onSave = { _, _ -> })
    }
}
