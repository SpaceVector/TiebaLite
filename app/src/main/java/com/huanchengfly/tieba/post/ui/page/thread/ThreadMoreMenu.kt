package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.automirrored.rounded.ChromeReaderMode
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Face6
import androidx.compose.material.icons.rounded.FaceRetouchingOff
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedColors
import com.huanchengfly.tieba.post.ui.common.theme.compose.ExtendedTheme
import com.huanchengfly.tieba.post.ui.widgets.compose.VerticalGrid

@Composable
internal fun ThreadMoreMenu(
    isSeeLz: Boolean,
    isCollected: Boolean,
    isImmersiveMode: Boolean,
    isDesc: Boolean,
    canDelete: () -> Boolean,
    onSeeLzClick: () -> Unit,
    onCollectClick: () -> Unit,
    onImmersiveModeClick: () -> Unit,
    onDescClick: () -> Unit,
    onJumpPageClick: () -> Unit,
    onShareClick: () -> Unit,
    onCopyLinkClick: () -> Unit,
    onReportClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    themeColors: ExtendedColors = ExtendedTheme.colors,
) {
    val showDelete = canDelete()
    // No sheet handle or separate panel background: these actions live inside the expanding glass.
    VerticalGrid(
        column = 3,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)),
    ) {
        item {
            ThreadMenuAction(
                stringResource(R.string.title_see_lz),
                if (isSeeLz) Icons.Rounded.Face6 else Icons.Rounded.FaceRetouchingOff,
                onSeeLzClick, themeColors, selected = isSeeLz,
            )
        }
        item {
            ThreadMenuAction(
                stringResource(if (isCollected) R.string.title_collected else R.string.title_uncollected),
                if (isCollected) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                onCollectClick, themeColors, selected = isCollected,
            )
        }
        item {
            ThreadMenuAction(
                stringResource(R.string.title_pure_read),
                if (isImmersiveMode) Icons.AutoMirrored.Rounded.ChromeReaderMode else Icons.AutoMirrored.Outlined.ChromeReaderMode,
                onImmersiveModeClick, themeColors, selected = isImmersiveMode,
            )
        }
        item {
            ThreadMenuAction(
                stringResource(R.string.title_sort), Icons.AutoMirrored.Rounded.Sort,
                onDescClick, themeColors, selected = isDesc,
            )
        }
        item {
            ThreadMenuAction(
                stringResource(R.string.title_jump_page), Icons.Rounded.RocketLaunch,
                onJumpPageClick, themeColors,
            )
        }
        item {
            ThreadMenuAction(
                stringResource(R.string.title_share), Icons.Rounded.Share,
                onShareClick, themeColors,
            )
        }
        item {
            ThreadMenuAction(
                stringResource(R.string.title_copy_link), Icons.Rounded.ContentCopy,
                onCopyLinkClick, themeColors,
            )
        }
        item {
            ThreadMenuAction(
                stringResource(R.string.title_report), Icons.Rounded.Report,
                onReportClick, themeColors,
            )
        }
        if (showDelete) {
            item {
                ThreadMenuAction(
                    stringResource(R.string.title_delete), Icons.Rounded.Delete,
                    onDeleteClick, themeColors, tint = MaterialTheme.colors.error,
                )
            }
        }
    }
}

@Composable
private fun ThreadMenuAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    themeColors: ExtendedColors,
    selected: Boolean = false,
    tint: Color = if (selected) themeColors.accent else themeColors.text,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) themeColors.accent.copy(alpha = 0.16f) else Color.Transparent)
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.height(6.dp))
        Text(
            text = text,
            color = tint,
            style = MaterialTheme.typography.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
