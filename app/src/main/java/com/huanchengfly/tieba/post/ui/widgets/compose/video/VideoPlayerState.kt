package com.huanchengfly.tieba.post.ui.widgets.compose.video

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class VideoPlayerState(
    val thumbnailUrl: String? = null,
    val startedPlay: Boolean = false,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1f,
    val controlsVisible: Boolean = false,
    val controlsEnabled: Boolean = true,
    val gesturesEnabled: Boolean = true,
    val isLongPressSpeeding: Boolean = false,
    val duration: Long = 1L,
    val currentPosition: Long = 1L,
    val secondaryProgress: Long = 1L,
    val videoSize: Pair<Float, Float> = 1920f to 1080f,
    val draggingProgress: DraggingProgress? = null,
    val playbackState: PlaybackState = PlaybackState.IDLE,
    val quickSeekAction: QuickSeekAction = QuickSeekAction.none(),
    val isFullScreen: Boolean = false
) : Parcelable
