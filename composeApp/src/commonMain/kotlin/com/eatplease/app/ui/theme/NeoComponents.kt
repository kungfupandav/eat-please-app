package com.eatplease.app.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The signature neo-brutalist surface: a bordered block sitting above a hard
 * offset ink shadow. When [onClick] is set, pressing slides the face down-right
 * by exactly the shadow offset while the shadow is covered, so the box reads as
 * physically pressed flush into the page, then springs back on release.
 *
 * Non-clickable blocks (pure display cards) keep the shadow but never move.
 */
@Composable
fun NeoBox(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    cornerRadius: Dp = 16.dp,
    borderWidth: Dp = 3.dp,
    shadowOffset: Dp = 4.dp,
    borderColor: Color = NeoColors.Ink,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val shape: Shape = RoundedCornerShape(cornerRadius)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shift by animateDpAsState(
        targetValue = if (onClick != null && pressed) shadowOffset else 0.dp,
        animationSpec = tween(durationMillis = 70),
        label = "neo-press",
    )

    // Outer padding reserves room for the shadow so it never draws outside bounds.
    Box(modifier = modifier.padding(end = shadowOffset, bottom = shadowOffset)) {
        // Hard shadow, revealed at bottom-right while the face is at rest.
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .background(NeoColors.Ink, shape),
        )
        // Face — the size-defining child.
        Box(
            modifier = Modifier
                .offset(x = shift, y = shift)
                .clip(shape)
                .background(backgroundColor, shape)
                .border(borderWidth, borderColor, shape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(contentPadding),
            content = content,
        )
    }
}

/** Full-width chunky action button (Start/Stop, Record, etc.). */
@Composable
fun NeoButton(
    text: String,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NeoBox(
        modifier = modifier,
        backgroundColor = backgroundColor,
        onClick = onClick,
        contentPadding = PaddingValues(vertical = 15.dp, horizontal = 20.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = NeoColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
        )
    }
}

/** Small highlighted token, e.g. the "WATCHING" pill next to the wordmark. */
@Composable
fun NeoPill(
    text: String,
    backgroundColor: Color = NeoColors.Yellow,
    modifier: Modifier = Modifier,
) {
    NeoBox(
        modifier = modifier,
        backgroundColor = backgroundColor,
        cornerRadius = 9.dp,
        borderWidth = 3.dp,
        shadowOffset = 3.dp,
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = NeoColors.Ink)
    }
}

/**
 * A color-block stat card: an uppercase header strip over a big value and a
 * small caption, matching the 2x2 grid on Home and the reference tiles.
 */
@Composable
fun NeoStatCard(
    label: String,
    value: String,
    caption: String,
    headerColor: Color,
    bodyColor: Color,
    modifier: Modifier = Modifier,
    headerTextColor: Color = NeoColors.Ink,
) {
    NeoBox(
        modifier = modifier,
        backgroundColor = bodyColor,
        cornerRadius = 14.dp,
        shadowOffset = 3.dp,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = headerTextColor,
                    modifier = Modifier.padding(vertical = 5.dp),
                )
            }
            // Divider echoes the hard border between header and body.
            Box(Modifier.fillMaxWidth().height(3.dp).background(NeoColors.Ink))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = NeoColors.Ink,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = NeoColors.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}
