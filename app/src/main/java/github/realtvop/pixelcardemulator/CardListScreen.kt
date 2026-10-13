package github.realtvop.pixelcardemulator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp

@Composable
fun CardListScreen(
    cards: List<SavedCard>,
    activeId: String?,
    defaultId: String?,
    onToggleActive: (String) -> Unit,
    onToggleDefault: (String) -> Unit,
    onAddCard: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val activeName = cards.find { it.id == activeId }?.name

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.cards)) },
                subtitle = {
                    Text(
                        if (activeName != null) {
                            stringResource(R.string.card_is_active, activeName)
                        } else {
                            stringResource(R.string.no_active_card)
                        },
                    )
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = onAddCard,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(IconButtonDefaults.smallContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_card))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
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
                    onToggleActive = { onToggleActive(card.id) },
                    onToggleDefault = { onToggleDefault(card.id) },
                    modifier = Modifier.animateItem(),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.list_hint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Shows the name only; the UID stays hidden. Tap toggles active, touch and hold opens the default menu. */
@Composable
private fun CardRow(
    card: SavedCard,
    active: Boolean,
    isDefault: Boolean,
    onToggleActive: () -> Unit,
    onToggleDefault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier) {
        ListItem(
            checked = active,
            onCheckedChange = { nowActive ->
                haptics.performHapticFeedback(
                    if (nowActive) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                )
                onToggleActive()
            },
            // Tall enough that showing or hiding the "Default" line never changes the row height.
            modifier = Modifier.heightIn(min = 88.dp),
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
            onLongClick = { menuOpen = true },
            onLongClickLabel = stringResource(R.string.card_options),
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

        DropdownMenuPopup(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                DropdownMenuItem(
                    onClick = {
                        menuOpen = false
                        onToggleDefault()
                    },
                    text = {
                        Text(stringResource(if (isDefault) R.string.remove_default else R.string.set_as_default))
                    },
                    shape = MenuDefaults.standaloneItemShape,
                    leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null) },
                )
            }
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
        )
    }
}
