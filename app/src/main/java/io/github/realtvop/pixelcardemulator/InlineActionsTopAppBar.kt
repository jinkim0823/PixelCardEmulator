package io.github.realtvop.pixelcardemulator

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/** The collapsed bar, and the row the expanded title shares with the actions. */
private val CollapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight

/** Puts the title where the Material bars do, 16dp in. */
private val TextStartInset = 16.dp

/** The icon buttons are 48dp wide around a 24dp icon, so this leaves the last icon 16dp from the edge too. */
private val ActionsEndInset = 4.dp
private val TextActionsGap = 8.dp

/** Measured from the subtitle's baseline, as in the Material large bar. */
private val SubtitleBaselineToBottom = 28.dp

/** Brings the collapsed title in late, so it does not pile onto the expanded title on its way out. */
private val CollapsedTitleAlphaEasing = CubicBezierEasing(0.8f, 0f, 0.8f, 0.15f)

private enum class Slot { ExpandedTitle, ExpandedSubtitle, CollapsedTitle, Actions }

/**
 * A large top app bar that keeps its [actions] on the same row as the expanded [title], with the
 * [subtitle] right under the title. [androidx.compose.material3.LargeFlexibleTopAppBar] puts the
 * actions in a row of their own above the title, which leaves that row empty on a screen with no
 * navigation icon.
 *
 * Follows [scrollBehavior] like the Material bars: scrolling shrinks the bar to one row, where the
 * large title gives way to a small one and the subtitle is dropped. The actions never move.
 */
@Composable
fun InlineActionsTopAppBar(
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = TopAppBarDefaults.topAppBarColors()
    val typography = MaterialTheme.typography
    val state = scrollBehavior.state
    // Both sizes of the title are always composed, so only the one showing may be exposed to TalkBack.
    // The subtitle goes with the large one.
    val collapsed by remember(state) { derivedStateOf { state.collapsedFraction >= 0.5f } }

    Box(
        modifier
            .drawBehind {
                val scrolled = FastOutLinearInEasing.transform(state.collapsedFraction)
                drawRect(lerp(colors.containerColor, colors.scrolledContainerColor, scrolled))
            }
            .semantics { isTraversalGroup = true }
            // Keeps touches on the bar from reaching the list scrolled underneath it.
            .pointerInput(Unit) {},
    ) {
        Layout(
            content = {
                Box(
                    Modifier
                        .layoutId(Slot.ExpandedTitle)
                        .hiddenIf(collapsed)
                        .graphicsLayer { alpha = 1f - state.collapsedFraction.coerceIn(0f, 1f) },
                ) {
                    BarText(colors.titleContentColor, typography.displaySmall, title)
                }
                Box(
                    Modifier
                        .layoutId(Slot.ExpandedSubtitle)
                        .hiddenIf(collapsed)
                        .graphicsLayer { alpha = 1f - state.collapsedFraction.coerceIn(0f, 1f) },
                ) {
                    BarText(colors.subtitleContentColor, typography.titleMedium, subtitle)
                }
                Box(
                    Modifier
                        .layoutId(Slot.CollapsedTitle)
                        .hiddenIf(!collapsed)
                        .graphicsLayer {
                            alpha = CollapsedTitleAlphaEasing.transform(state.collapsedFraction.coerceIn(0f, 1f))
                        },
                ) {
                    BarText(colors.titleContentColor, typography.titleLarge, title)
                }
                CompositionLocalProvider(LocalContentColor provides colors.actionIconContentColor) {
                    Row(
                        Modifier.layoutId(Slot.Actions),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                }
            },
            // Clipped after the inset padding, so nothing draws under the status bar.
            modifier = Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets).clipToBounds(),
        ) { measurables, constraints ->
            fun measureSlot(slot: Slot, constraints: Constraints) =
                measurables.first { it.layoutId == slot }.measure(constraints)

            val startInset = TextStartInset.roundToPx()
            val endInset = ActionsEndInset.roundToPx()
            val actionsPlaceable = measureSlot(Slot.Actions, constraints.copy(minWidth = 0, minHeight = 0))
            val textConstraints = Constraints(
                maxWidth = (
                    constraints.maxWidth - startInset - TextActionsGap.roundToPx() -
                        actionsPlaceable.width - endInset
                    ).coerceAtLeast(0),
            )
            val expandedTitle = measureSlot(Slot.ExpandedTitle, textConstraints)
            val expandedSubtitle = measureSlot(Slot.ExpandedSubtitle, textConstraints)
            val collapsedTitle = measureSlot(Slot.CollapsedTitle, textConstraints)

            val collapsedHeight = maxOf(CollapsedHeight.roundToPx(), collapsedTitle.height, actionsPlaceable.height)
            // A large font size can make the title taller than the collapsed row; the row grows to fit it.
            val titleRowHeight = maxOf(collapsedHeight, expandedTitle.height)
            val subtitleTop = (titleRowHeight + expandedTitle.height) / 2
            val subtitleBaseline = expandedSubtitle[LastBaseline]
                .takeIf { it != AlignmentLine.Unspecified } ?: expandedSubtitle.height
            val expandedHeight = maxOf(
                titleRowHeight,
                subtitleTop + subtitleBaseline + SubtitleBaselineToBottom.roundToPx(),
            )

            // The scroll behavior only collapses as far as the bar says it can.
            state.heightOffsetLimit = -(expandedHeight - collapsedHeight).toFloat()
            val height = (expandedHeight + state.heightOffset.roundToInt()).coerceAtLeast(collapsedHeight)

            layout(constraints.maxWidth, height) {
                // Centered on the title's row while expanded and on the collapsed row once collapsed. That
                // is the same line unless a large font size has grown the title row.
                val actionsRowHeight = lerp(
                    titleRowHeight.toFloat(),
                    collapsedHeight.toFloat(),
                    state.collapsedFraction.coerceIn(0f, 1f),
                )
                actionsPlaceable.placeRelative(
                    x = constraints.maxWidth - actionsPlaceable.width - endInset,
                    y = ((actionsRowHeight - actionsPlaceable.height) / 2).toInt(),
                )
                expandedTitle.placeRelative(startInset, (titleRowHeight - expandedTitle.height) / 2)
                expandedSubtitle.placeRelative(startInset, subtitleTop)
                collapsedTitle.placeRelative(startInset, (collapsedHeight - collapsedTitle.height) / 2)
            }
        }
    }
}

@Composable
private fun BarText(color: Color, style: TextStyle, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides color) {
        ProvideTextStyle(style, content)
    }
}

private fun Modifier.hiddenIf(hidden: Boolean) = if (hidden) clearAndSetSemantics {} else this
