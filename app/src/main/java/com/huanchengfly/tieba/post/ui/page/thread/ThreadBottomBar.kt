package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.common.theme.compose.threadBottomBar
import com.huanchengfly.tieba.post.ui.widgets.compose.Avatar
import com.huanchengfly.tieba.post.ui.widgets.compose.GlassBackdrop
import com.huanchengfly.tieba.post.ui.widgets.compose.floatingGlassSurface
import com.huanchengfly.tieba.post.utils.StringUtil.getShortNumString

// Reserve only the collapsed height in Scaffold, so opening the menu never moves the article.
@Composable
internal fun ThreadBottomBarSpacer(liftUpBottomBar: Boolean) {
    Column {
        Spacer(Modifier.height(72.dp))
        ThreadNavigationInset(liftUpBottomBar)
    }
}

@Composable
private fun ThreadNavigationInset(liftUpBottomBar: Boolean) {
    // Preserve the existing gesture-bar inset and "lift up bottom bar" preference.
    Box(Modifier.requiredHeightIn(min = if (liftUpBottomBar) 16.dp else 0.dp)) {
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
internal fun ThreadBottomBar(
    onClickReply: () -> Unit,
    onAgree: () -> Unit,
    onClickMore: () -> Unit,
    modifier: Modifier = Modifier,
    showReply: Boolean = true,
    avatarUrl: String? = null,
    hasAgreed: Boolean = false,
    agreeNum: Long = 0,
    liftUpBottomBar: Boolean = false,
    themeColors: ExtendedColors = ExtendedTheme.colors,
    backdrop: GlassBackdrop? = null,
    menuExpanded: Boolean = false,
    moreContent: @Composable () -> Unit = {},
) {
    val actionColor = themeColors.text.copy(alpha = 0.86f)
    val agreeColor by animateColorAsState(
        if (hasAgreed) themeColors.accent else actionColor,
        label = "threadAgreeColor",
    )
    val extraTint by animateFloatAsState(
        targetValue = if (menuExpanded) (if (themeColors.isNightMode) 0.38f else 0.22f) else 0f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "threadMenuTint",
    )
    val density = LocalDensity.current
    val navigationInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val bottomInset = maxOf(navigationInset, if (liftUpBottomBar) 16.dp else 0.dp)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val menuMaxHeight = minOf(360.dp, (maxHeight - bottomInset - 72.dp).coerceAtLeast(0.dp))
        Column {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val actionWidth by animateDpAsState(
                    targetValue = if (menuExpanded) minOf(maxWidth, 280.dp) else 112.dp,
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    label = "threadMenuWidth",
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = showReply && !menuExpanded,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(end = 124.dp),
                    // Follow the expanding capsule instead of blinking out and reappearing after a delay.
                    enter = fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                            scaleIn(
                                tween(260, easing = FastOutSlowInEasing),
                                initialScale = 0.94f,
                                transformOrigin = TransformOrigin(1f, 0.5f),
                            ) +
                            slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 16 },
                    exit = fadeOut(tween(240, easing = FastOutSlowInEasing)) +
                            scaleOut(
                                tween(240, easing = FastOutSlowInEasing),
                                targetScale = 0.94f,
                                transformOrigin = TransformOrigin(1f, 0.5f),
                            ) +
                            slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 16 },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .floatingGlassSurface(backdrop, themeColors, themeColors.threadBottomBar)
                            .clickable(enabled = showReply && !menuExpanded, onClick = onClickReply)
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (avatarUrl != null) {
                            Avatar(data = avatarUrl, size = 28.dp, contentDescription = null)
                        } else {
                            Icon(Icons.Rounded.Edit, contentDescription = null, tint = actionColor)
                        }
                        Text(
                            text = stringResource(R.string.tip_reply_thread),
                            style = MaterialTheme.typography.body2,
                            color = themeColors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .width(actionWidth)
                        .floatingGlassSurface(
                            backdrop,
                            themeColors,
                            themeColors.threadBottomBar,
                            shape = RoundedCornerShape(28.dp),
                        )
                        .background(themeColors.threadBottomBar.copy(alpha = extraTint)),
                ) {
                    AnimatedVisibility(
                        visible = menuExpanded,
                        enter = expandVertically(tween(280, easing = FastOutSlowInEasing), expandFrom = Alignment.Bottom) +
                                fadeIn(tween(160, delayMillis = 80)),
                        exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Bottom) +
                                fadeOut(tween(100)),
                    ) {
                        Box(Modifier.heightIn(max = menuMaxHeight)) {
                            moreContent()
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                            if (menuExpanded) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(enabled = showReply, onClick = onClickReply)
                                        .padding(start = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (showReply) {
                                        Icon(Icons.Rounded.Edit, contentDescription = null, tint = actionColor)
                                    }
                                    Text(
                                        text = stringResource(if (showReply) R.string.tip_reply_thread else R.string.btn_more),
                                        color = themeColors.text,
                                        style = MaterialTheme.typography.body2,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onAgree,
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(56.dp)
                                .semantics { selected = hasAgreed },
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (hasAgreed) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = stringResource(R.string.title_agree),
                                    tint = agreeColor,
                                )
                                if (agreeNum > 0) {
                                    Text(
                                        text = agreeNum.getShortNumString(),
                                        color = agreeColor,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onClickMore, modifier = Modifier.size(56.dp)) {
                            Crossfade(targetState = menuExpanded, animationSpec = tween(160), label = "threadMoreIcon") { expanded ->
                                Icon(
                                    imageVector = if (expanded) Icons.Rounded.Close else Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(if (expanded) R.string.btn_close else R.string.btn_more),
                                    tint = actionColor,
                                )
                            }
                        }
                    }
                }
            }
            ThreadNavigationInset(liftUpBottomBar)
        }
    }
}
