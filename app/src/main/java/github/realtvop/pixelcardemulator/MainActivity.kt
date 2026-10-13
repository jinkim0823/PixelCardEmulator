package github.realtvop.pixelcardemulator

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset

class MainActivity : ComponentActivity() {
    private val viewModel: CardsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelCardTheme {
                // Two screens only, so a flag stands in for a navigation library.
                var addingCard by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = addingCard) { addingCard = false }

                val slide = MaterialTheme.motionScheme.defaultEffectsSpec<IntOffset>()
                val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                AnimatedContent(
                    targetState = addingCard,
                    transitionSpec = {
                        // The add screen enters from the end edge and leaves the way it came.
                        val direction = if (targetState) 1 else -1
                        (slideInHorizontally(slide) { direction * it / 6 } + fadeIn(fade)) togetherWith
                            (slideOutHorizontally(slide) { -direction * it / 6 } + fadeOut(fade))
                    },
                    label = "screen",
                ) { adding ->
                    if (adding) {
                        AddCardScreen(
                            onBack = { addingCard = false },
                            onSave = { name, uid ->
                                viewModel.add(name, uid)
                                addingCard = false
                            },
                        )
                    } else {
                        CardListScreen(
                            cards = viewModel.cards,
                            activeId = viewModel.activeId,
                            defaultId = viewModel.defaultId,
                            onToggleActive = viewModel::toggleActive,
                            onToggleDefault = viewModel::toggleDefault,
                            onAddCard = { addingCard = true },
                        )
                    }
                }
            }
        }
    }
}
