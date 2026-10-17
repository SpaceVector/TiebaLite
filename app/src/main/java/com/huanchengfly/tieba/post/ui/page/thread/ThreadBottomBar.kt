package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
) {
    val actionColor = themeColors.text.copy(alpha = 0.86f)
    val agreeColor by animateColorAsState(
        if (hasAgreed) themeColors.accent else actionColor,
        label = "threadAgreeColor",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showReply) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .floatingGlassSurface(backdrop, themeColors, themeColors.threadBottomBar)
                        .clickable(onClick = onClickReply)
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
            } else {
                Spacer(Modifier.weight(1f))
            }
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .floatingGlassSurface(backdrop, themeColors, themeColors.threadBottomBar),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onAgree,
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(min = 56.dp)
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
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.btn_more),
                        tint = actionColor,
                    )
                }
            }
        }
        // Preserve the existing gesture-bar inset and "lift up bottom bar" preference.
        Box(Modifier.requiredHeightIn(min = if (liftUpBottomBar) 16.dp else 0.dp)) {
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
