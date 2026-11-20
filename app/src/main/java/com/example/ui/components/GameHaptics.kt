package com.example.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role

/**
 * Shared tactile interaction recipe for the game's tap targets.
 *
 * Adds a subtle haptic "tick" on press so every confirmation (card taps,
 * board targeting, buttons) feels consistent across devices. Falls back to
 * [LocalIndication]'s ripple only; never removes existing visual feedback.
 */
@Composable
fun rememberGameTapFeedback(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { haptic.performHapticFeedback(HapticFeedbackType.TextClick) } }
}

/**
 * A [clickable] wrapper with consistent ripple indication and game-tap haptics.
 * Use for all standard tap targets so press feedback stays uniform UI-wide.
 */
@Composable
fun Modifier.gameClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val onTapWithHaptic = rememberGameTapFeedback()
    return this.then(
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = {
                onTapWithHaptic()
                onClick()
            },
        )
    )
}

/**
 * Invisible touch absorber for content panels that sit inside a dismiss-scrim.
 * Swallows taps without adding any visual indication so the scrim's own
 * feedback stays clean while the panel remains non-interactive.
 */
@Composable
fun Modifier.gameTapBlocker(): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.then(
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = false,
            onClick = {},
        )
    )
}
