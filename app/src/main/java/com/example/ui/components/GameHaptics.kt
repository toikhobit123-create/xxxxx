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
 * A [clickable] wrapper with game-tap haptics. Card tiles and board slots use
 * [ripple] = false so they keep only the tactile tick plus their own border /
 * scale animations (card-game feel); buttons keep the Material ripple.
 */
@Composable
fun Modifier.gameClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    ripple: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val onTapWithHaptic = rememberGameTapFeedback()
    return this.then(
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = if (ripple) ripple() else null,
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
