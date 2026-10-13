package io.github.realtvop.pixelcardemulator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import kotlin.math.roundToInt

@Composable
fun CardListScreen(
    cards: List<SavedCard>,
    activeId: String?,
    defaultId: String?,
    onToggleActive: (String) -> Unit,
    onToggleDefault: (String) -> Unit,
    onAddCard: () -> Unit,
    onEditCard: (String) -> Unit,
    forceRaw: Boolean = false,
    onToggleForceRaw: () -> Unit = {},
    showPermissionBanner: Boolean = false,
    onPermissionBannerClick: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // The card whose swipe menu is open. Held here so that opening one closes any other.
    var swipedCardId by remember { mutableStateOf<String?>(null) }
    var appMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.cards)) },
                    subtitle = { Text(stringResource(R.string.tap_to_activate)) },
                    actions = {
                        FilledTonalIconButton(
                            onClick = onAddCard,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(
                                    IconButtonDefaults.smallContainerSize(
                                        IconButtonDefaults.IconButtonWidthOption.Wide,
                                    ),
                                ),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_card))
                        }
                        Box {
                            IconButton(onClick = { appMenuOpen = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = appMenuOpen, onDismissRequest = { appMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.raw_nci_mode)) },
                                    onClick = {
                                        appMenuOpen = false
                                        onToggleForceRaw()
                                    },
                                    trailingIcon = {
                                        if (forceRaw) {
                                            Icon(
                                                Icons.Rounded.Check,
                                                contentDescription = stringResource(R.string.enabled),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
                AnimatedVisibility(visible = showPermissionBanner) {
                    PermissionBanner(onClick = onPermissionBannerClick)
                }
            }
        },
    ) { innerPadding ->
        // One column on a phone, more on unfolded or landscape screens.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            contentPadding = innerPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cards, key = { it.id }) { card ->
                CardRow(
                    card = card,
                    active = card.id == activeId,
                    isDefault = card.id == defaultId,
                    swipeMenuOpen = card.id == swipedCardId,
                    onSwipeMenuOpenChange = { open ->
                        if (open) swipedCardId = card.id else if (swipedCardId == card.id) swipedCardId = null
                    },
                    onToggleActive = { onToggleActive(card.id) },
                    onToggleDefault = { onToggleDefault(card.id) },
                    onEdit = { onEditCard(card.id) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (cards.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier.padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.tips),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        stringArrayResource(R.array.card_tips).forEach { tip ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "•",
                                    modifier = Modifier.clearAndSetSemantics {},
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = tip,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        leadingContent = {
            Icon(painterResource(R.drawable.ic_contactless), contentDescription = null)
        },
        trailingContent = {
            TextButton(onClick = onClick) { Text(stringResource(R.string.set_up)) }
        },
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            leadingContentColor = MaterialTheme.colorScheme.onErrorContainer,
            trailingContentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(stringResource(R.string.permission_required))
    }
}

private enum class SwipeMenu { Closed, Open }

private val SwipeActionSize = 56.dp
private val SwipeActionGap = 12.dp

/** How far a swiped card gives way: its two action buttons, each with a gap before it. */
private val SwipeMenuWidth = (SwipeActionSize + SwipeActionGap) * 2

/**
 * Shows the name only; the UID stays hidden. Tap toggles active. The default toggle and Edit are
 * reached by swiping the card aside.
 */
@Composable
private fun CardRow(
    card: SavedCard,
    active: Boolean,
    isDefault: Boolean,
    swipeMenuOpen: Boolean,
    onSwipeMenuOpenChange: (Boolean) -> Unit,
    onToggleActive: () -> Unit,
    onToggleDefault: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme

    val swipeWidthPx = with(LocalDensity.current) { SwipeMenuWidth.toPx() }
    val swipe = remember(swipeWidthPx) {
        AnchoredDraggableState(
            initialValue = SwipeMenu.Closed,
            anchors = DraggableAnchors {
                SwipeMenu.Closed at 0f
                SwipeMenu.Open at -swipeWidthPx
            },
        )
    }
    val swiped by remember(swipe) { derivedStateOf { -swipe.offset >= 1f } }
    // Report where a swipe settles, and close again when told to (another card opened, or a tap).
    LaunchedEffect(swipe.settledValue) { onSwipeMenuOpenChange(swipe.settledValue == SwipeMenu.Open) }
    LaunchedEffect(swipeMenuOpen) { if (!swipeMenuOpen) swipe.animateTo(SwipeMenu.Closed) }

    Box(
        modifier.anchoredDraggable(
            state = swipe,
            orientation = Orientation.Horizontal,
            flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                state = swipe,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            ),
        ),
    ) {
        // Only composed while swiped, so the actions are never reachable underneath a closed card.
        if (swiped) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .graphicsLayer {
                        // Grows into the space the card gives up.
                        val progress = (-swipe.offset / swipeWidthPx).coerceIn(0f, 1f)
                        alpha = progress
                        scaleX = 0.5f + progress / 2
                        scaleY = 0.5f + progress / 2
                    },
                horizontalArrangement = Arrangement.spacedBy(SwipeActionGap),
            ) {
                FilledTonalIconToggleButton(
                    checked = isDefault,
                    onCheckedChange = {
                        onToggleDefault()
                        onSwipeMenuOpenChange(false)
                    },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    modifier = Modifier.size(SwipeActionSize),
                ) {
                    Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.default_card))
                }
                FilledTonalIconButton(
                    onClick = {
                        onSwipeMenuOpenChange(false)
                        onEdit()
                    },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(SwipeActionSize),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit))
                }
            }
        }

        ListItem(
            checked = active,
            onCheckedChange = { nowActive ->
                if (swipeMenuOpen) {
                    // A tap on a swiped card only puts it back.
                    onSwipeMenuOpenChange(false)
                } else {
                    haptics.performHapticFeedback(
                        if (nowActive) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                    )
                    onToggleActive()
                }
            },
            modifier = Modifier
                // Swiping squeezes the card from its end edge rather than sliding it out of the row.
                .layout { measurable, constraints ->
                    val given = (-swipe.offset).roundToInt().coerceIn(0, constraints.maxWidth)
                    val placeable = measurable.measure(constraints.offset(horizontal = -given))
                    layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(0, 0) }
                }
                // Tall enough that showing or hiding the "Default" line never changes the row height.
                .heightIn(min = 88.dp),
            leadingContent = { CardBadge(active) },
            supportingContent = {
                AnimatedVisibility(isDefault) { Text(stringResource(R.string.default_card)) }
            },
            trailingContent = {
                AnimatedVisibility(active, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    Text(stringResource(R.string.active), style = MaterialTheme.typography.labelLarge)
                }
            },
            verticalAlignment = Alignment.CenterVertically,
            // The active card also rounds into a pill, so the state never depends on color alone.
            shapes = ListItemDefaults.shapes(
                shape = MaterialTheme.shapes.extraLarge,
                selectedShape = MaterialTheme.shapes.extraExtraLarge,
                pressedShape = MaterialTheme.shapes.large,
            ),
            colors = ListItemDefaults.colors(
                containerColor = colorScheme.surfaceContainer,
                selectedContainerColor = colorScheme.primaryContainer,
                selectedContentColor = colorScheme.onPrimaryContainer,
                selectedSupportingContentColor = colorScheme.onPrimaryContainer,
                selectedTrailingContentColor = colorScheme.onPrimaryContainer,
            ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = card.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CardBadge(active: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(
        if (active) colorScheme.primary else colorScheme.surfaceContainerHighest,
        effects,
        label = "badgeContainer",
    )
    val content by animateColorAsState(
        if (active) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
        effects,
        label = "badgeContent",
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_contactless), contentDescription = null, tint = content)
    }
}

@PreviewLightDark
@Composable
private fun CardListScreenPreview() {
    PixelCardTheme {
        CardListScreen(
            cards = MockCards,
            activeId = MockCards[0].id,
            defaultId = MockCards[1].id,
            onToggleActive = {},
            onToggleDefault = {},
            onAddCard = {},
            onEditCard = {},
            forceRaw = false,
            onToggleForceRaw = {},
            showPermissionBanner = true,
            onPermissionBannerClick = {},
        )
    }
}
