package io.github.realtvop.pixelcardemulator

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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

private val UidSizes = listOf(4, 7, 10)
private const val MaxNameLength = 32

/** Keeps the form from stretching across unfolded or landscape screens. */
private val FormMaxWidth = 560.dp

/** Adds a new card when [card] is null; otherwise shows that saved card so it can be changed or deleted. */
@Composable
fun CardEditorScreen(
    card: SavedCard?,
    onBack: () -> Unit,
    onSave: (name: String, uid: String) -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val name = rememberTextFieldState(card?.name.orEmpty())
    val typedUid = rememberTextFieldState(card?.uid.orEmpty())
    var uidSize by rememberSaveable { mutableIntStateOf(card?.uidSize ?: UidSizes.first()) }
    // A saved UID stays hidden until it is asked for; a new card starts shown by default.
    var uidShown by rememberSaveable { mutableStateOf(card == null) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var lastScannedUid by rememberSaveable { mutableStateOf<String?>(null) }
    var scanError by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    // Complete UID when entered/scanned size matches.
    val uid = typedUid.text.toString().takeIf { it.length == uidSize * 2 }

    NfcUidReader(
        enabled = scanning && lastScannedUid == null,
        onUid = { uidHex ->
            lastScannedUid = uidHex
            uidSize = uidHex.length / 2
            typedUid.edit { replace(0, length, uidHex) }
            scanError = null
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        },
        onError = { scanError = it },
    )

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (card == null) R.string.add_card else R.string.edit_card)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (card != null) {
                        IconButton(onClick = { confirmingDelete = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete_card))
                        }
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
                    Text(
                        text = stringResource(if (card == null) R.string.add_card else R.string.save),
                        style = ButtonDefaults.textStyleFor(height),
                    )
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

                OutlinedButton(
                    onClick = {
                        scanning = true
                        lastScannedUid = null
                        scanError = null
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_contactless),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.scan_card))
                }

                AnimatedContent(targetState = uidShown, label = "uid") { shown ->
                    if (shown) {
                        ManualUidEntry(
                            uid = typedUid,
                            size = uidSize,
                            onSizeChange = { newSize ->
                                uidSize = newSize
                                typedUid.edit { if (length > newSize * 2) delete(newSize * 2, length) }
                            },
                            onHide = { uidShown = false },
                        )
                    } else {
                        HiddenUid(
                            size = uidSize,
                            hasUid = uid != null,
                            onShow = { uidShown = true },
                        )
                    }
                }
            }
        }
    }

    if (scanning) {
        ScanDialog(
            scannedUid = lastScannedUid,
            error = scanError,
            onScanAgain = {
                lastScannedUid = null
                scanError = null
            },
            onDismiss = {
                scanning = false
                lastScannedUid = null
                scanError = null
            },
        )
    }

    if (card != null && confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_card_title, card.name)) },
            text = { Text(stringResource(R.string.delete_card_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** Stands in for a saved or entered UID until the user asks to see it. */
@Composable
private fun HiddenUid(
    size: Int,
    hasUid: Boolean,
    onShow: () -> Unit,
) {
    ListItem(
        supportingContent = { Text(stringResource(R.string.uid_hidden)) },
        trailingContent = {
            TextButton(onClick = onShow, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.show))
            }
        },
        verticalAlignment = Alignment.CenterVertically,
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Text(if (hasUid) stringResource(R.string.uid_size, size) else stringResource(R.string.enter_uid))
    }
}

@Composable
private fun ManualUidEntry(
    uid: TextFieldState,
    size: Int,
    onSizeChange: (Int) -> Unit,
    onHide: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.enter_uid),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onHide, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.hide))
            }
        }

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

/** Dialog that waits for an NFC card, then confirms the read without displaying the raw UID. */
@Composable
private fun ScanDialog(
    scannedUid: String?,
    error: String?,
    onScanAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            if (scannedUid == null) {
                Icon(
                    painterResource(R.drawable.ic_contactless),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, MaterialShapes.Cookie9Sided.toShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
        title = {
            Text(stringResource(if (scannedUid == null) R.string.scan_ready else R.string.scan_done))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (scannedUid == null) {
                    LoadingIndicator(Modifier.size(48.dp))
                    Text(
                        text = error ?: stringResource(R.string.scan_instruction),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (error == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.uid_size, scannedUid.length / 2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(if (scannedUid != null) R.string.ok else R.string.cancel))
            }
        },
        dismissButton = if (scannedUid != null) {
            {
                TextButton(onClick = onScanAgain, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.scan_again))
                }
            }
        } else {
            null
        },
    )
}

/** Enables Android's NFC-A reader mode only while scanning is active and waiting for a card. */
@Composable
private fun NfcUidReader(
    enabled: Boolean,
    onUid: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val adapter = remember(context) { context.getSystemService(NfcManager::class.java)?.defaultAdapter }
    val unavailableMessage = stringResource(R.string.nfc_unavailable)
    val disabledMessage = stringResource(R.string.nfc_disabled)
    val invalidUidMessage = stringResource(R.string.unsupported_uid_length)

    DisposableEffect(enabled, activity, adapter) {
        if (!enabled) return@DisposableEffect onDispose {}
        if (activity == null || adapter == null) {
            onError(unavailableMessage)
            return@DisposableEffect onDispose {}
        }
        if (!adapter.isEnabled) {
            onError(disabledMessage)
            return@DisposableEffect onDispose {}
        }

        val callback = NfcAdapter.ReaderCallback { tag ->
            val uid = tag.id
            activity.runOnUiThread {
                if (uid.size in SupportedUidSizes) onUid(uid.toHex()) else onError(invalidUidMessage)
            }
        }
        adapter.enableReaderMode(
            activity,
            callback,
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null,
        )
        onDispose { runCatching { adapter.disableReaderMode(activity) } }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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

@PreviewLightDark
@Composable
private fun AddCardPreview() {
    PixelCardTheme {
        CardEditorScreen(card = null, onBack = {}, onSave = { _, _ -> }, onDelete = {})
    }
}

@PreviewLightDark
@Composable
private fun EditCardPreview() {
    PixelCardTheme {
        CardEditorScreen(card = MockCards[1], onBack = {}, onSave = { _, _ -> }, onDelete = {})
    }
}
