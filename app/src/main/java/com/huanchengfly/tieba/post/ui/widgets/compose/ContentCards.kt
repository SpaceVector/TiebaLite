package com.huanchengfly.tieba.post.ui.widgets.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.LocalExtendedColors
import com.huanchengfly.tieba.post.utils.ThemeUtil
import com.huanchengfly.tieba.post.utils.compose.calcStatusBarColor

internal val ExtendedColors.contentPageBackground: Color
    get() = if (isNightMode || ThemeUtil.isTranslucentTheme(theme)) {
        background
    } else {
        // Separate light cards from the page without changing custom theme hues.
        lerp(background, text.copy(alpha = 1f), 0.055f)
    }

internal val ExtendedColors.contentInsetBackground: Color
    get() = when {
        isNightMode -> lerp(floorCard, Color.White.copy(alpha = floorCard.alpha), 0.08f)
        floorCard == card -> lerp(floorCard, text.copy(alpha = 1f), 0.04f)
        else -> floorCard
    }

internal val ExtendedColors.contentCardBackground: Color
    get() = if (isNightMode) {
        lerp(card, Color.White.copy(alpha = card.alpha), 0.04f)
    } else {
        card
    }

private val ContentCardShape = RoundedCornerShape(20.dp)
internal val ExtendedColors.contentCardHighlightColors: List<Color>
    get() = if (isNightMode) {
        listOf(
            Color.White.copy(alpha = 0.16f),
            Color.White.copy(alpha = 0.07f),
            Color.White.copy(alpha = 0.045f),
        )
    } else {
        listOf(
            Color.White.copy(alpha = 0.8f * card.alpha),
            text.copy(alpha = 0.045f * card.alpha),
            text.copy(alpha = 0.09f * card.alpha),
        )
    }

@Composable
internal fun Modifier.contentCard(): Modifier {
    val colors = ExtendedTheme.colors
    return clip(ContentCardShape)
        .background(colors.contentCardBackground)
        .border(1.dp, Brush.verticalGradient(colors.contentCardHighlightColors), ContentCardShape)
}

@Composable
internal fun ContentCardTopBar(floating: Boolean = false, content: @Composable () -> Unit) {
    val colors = ExtendedTheme.colors
    Column(
        Modifier.fillMaxWidth()
            .then(if (floating) Modifier else Modifier.background(colors.contentPageBackground))
    ) {
        Spacer(
            Modifier
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .fillMaxWidth()
                .background(colors.topBar.calcStatusBarColor())
        )
        Column(
            Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .contentCard()
        ) {
            // Keep toolbar actions and tabs readable even with a primary-colored toolbar theme.
            CompositionLocalProvider(
                LocalExtendedColors provides colors.copy(
                    topBar = Color.Transparent,
                    onTopBar = colors.text,
                    onTopBarSecondary = colors.textSecondary,
                    onTopBarActive = colors.text,
                    topBarSurface = colors.contentInsetBackground,
                    onTopBarSurface = colors.textSecondary,
                )
            ) {
                content()
            }
        }
    }
}

internal val LocalContentCardTopPadding = compositionLocalOf { 0.dp }

@Composable
internal fun FloatingContentCardLayout(
    topBar: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var topBarHeight by remember { mutableStateOf(0.dp) }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalContentCardTopPadding provides topBarHeight) {
            content()
        }
        // Keep the list's viewport full-height, as on Home; only the rounded card occludes it.
        Box(
            Modifier.align(Alignment.TopCenter).onSizeChanged {
                topBarHeight = with(density) { it.height.toDp() }
            }
        ) {
            ContentCardTopBar(floating = true, content = topBar)
        }
    }
}
