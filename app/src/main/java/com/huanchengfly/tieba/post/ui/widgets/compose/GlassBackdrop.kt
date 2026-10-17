package com.huanchengfly.tieba.post.ui.widgets.compose

import android.annotation.SuppressLint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import kotlin.math.ceil

@RequiresApi(Build.VERSION_CODES.S)
internal class GlassBackdrop {
    // ponytail: native blur only; add a refraction shader if optical distortion is needed.
    val content = RenderNode("Glass backdrop content")
    var contentPosition by mutableStateOf(Offset.Zero)
}

@Composable
internal fun rememberGlassBackdrop(enabled: Boolean = true): GlassBackdrop? {
    val view = LocalView.current
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        LocalInspectionMode.current || !view.isHardwareAccelerated
    ) {
        return null
    }
    val backdrop = remember(view) { GlassBackdrop() }
    DisposableEffect(backdrop) {
        onDispose {
            backdrop.content.discardDisplayList()
        }
    }
    return backdrop
}

// A non-null backdrop is only created on hardware-accelerated Android 12+.
@SuppressLint("NewApi")
internal fun Modifier.glassBackdropSource(
    backdrop: GlassBackdrop?,
    backgroundColor: Color = Color.Transparent,
): Modifier {
    if (backdrop == null) return this
    return onGloballyPositioned { backdrop.contentPosition = it.positionInWindow() }
        .drawWithContent {
            val canvas = drawContext.canvas
            if (!canvas.nativeCanvas.isHardwareAccelerated || size.isEmpty()) {
                drawContent()
                return@drawWithContent
            }
            backdrop.content.setPosition(0, 0, size.width.toInt(), size.height.toInt())
            val recording = backdrop.content.beginRecording()
            try {
                drawContext.canvas = Canvas(recording)
                // Opaque page backgrounds must be sampled too; leave wallpaper themes untouched.
                if (backgroundColor.alpha == 1f) drawRect(backgroundColor)
                drawContent()
            } finally {
                drawContext.canvas = canvas
                backdrop.content.endRecording()
            }
            canvas.nativeCanvas.drawRenderNode(backdrop.content)
        }
}

@SuppressLint("NewApi")
internal fun Modifier.glassBackdrop(backdrop: GlassBackdrop?): Modifier {
    if (backdrop == null) return this
    return composed {
        // Each capsule needs its own target node and position, but shares the page source.
        val glass = remember(backdrop) { RenderNode("Glass capsule") }
        var glassPosition by remember(backdrop) { mutableStateOf(Offset.Zero) }
        DisposableEffect(glass) {
            onDispose { glass.discardDisplayList() }
        }
        onGloballyPositioned { glassPosition = it.positionInWindow() }
            .drawWithCache {
                val radius = 12.dp.toPx()
                val overscan = ceil(radius * 2).toInt()
                glass.setRenderEffect(
                    RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                )
                onDrawBehind {
                    val canvas = drawContext.canvas.nativeCanvas
                    if (canvas.isHardwareAccelerated && backdrop.content.hasDisplayList()) {
                        val offset = glassPosition - backdrop.contentPosition
                        // Blur only the capsule region, with extra pixels to avoid hard blur edges.
                        glass.setPosition(
                            0, 0, ceil(size.width).toInt() + overscan * 2,
                            ceil(size.height).toInt() + overscan * 2
                        )
                        val recording = glass.beginRecording()
                        try {
                            recording.translate(overscan - offset.x, overscan - offset.y)
                            recording.drawRenderNode(backdrop.content)
                        } finally {
                            glass.endRecording()
                        }
                        canvas.save()
                        canvas.translate(-overscan.toFloat(), -overscan.toFloat())
                        canvas.drawRenderNode(glass)
                        canvas.restore()
                    }
                }
            }
    }
}

internal fun Modifier.floatingGlassSurface(
    backdrop: GlassBackdrop?,
    themeColors: ExtendedColors,
    surfaceColor: Color = themeColors.bottomBar,
    shape: Shape = CircleShape,
): Modifier {
    val opacity = if (backdrop == null) 0.66f else if (themeColors.isNightMode) 0.34f else 0.40f
    return shadow(4.dp, shape, clip = false)
        .clip(shape)
        .glassBackdrop(backdrop)
        .background(surfaceColor.copy(alpha = opacity))
        .background(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.04f), Color.Transparent))
        )
        .border(
            BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (themeColors.isNightMode) 0.26f else 0.70f),
                        themeColors.text.copy(alpha = 0.12f),
                    )
                ),
            ),
            shape,
        )
}
