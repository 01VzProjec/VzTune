package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.cards.VzTrackRow
import com.vztechcore.vztune.ui.components.cards.VzVideoCard
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.playbackOf
import com.vztechcore.vztune.ui.components.text.VzChipRow

/**
 * Atalhos finos. Logica mora em VzTrackRow / VzVideoCard (components/cards).
 * Mesma assinatura pra nao quebrar MediaListScreen, ArtistScreen e overlays.
 */
@Composable
internal fun MediaListTrackRow(
    track: Track,
    index: Int?,
    isPlaying: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onPlayNext: () -> Unit,
    showDragHandle: Boolean,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    VzTrackRow(track = track, onClick = onClick, onMoreClick = onMoreClick, onPlayNext = onPlayNext, index = index, isPlaying = isPlaying, isPaused = isPaused, showDragHandle = showDragHandle, dragHandleModifier = dragHandleModifier, modifier = modifier)
}

@Composable
internal fun MediaListVideoCard(
    track: Track,
    isPlaying: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 12.dp,
) {
    VzVideoCard(track = track, isPlaying = isPlaying, isPaused = isPaused, onClick = onClick, onMoreClick = onMoreClick, modifier = modifier, horizontalPadding = horizontalPadding)
}

@Composable
internal fun MediaListTabs(selected: MediaListTab, onSelect: (MediaListTab) -> Unit) {
    VzChipRow(items = MediaListTab.entries, selected = selected, label = { it.label }, onSelect = onSelect)
}

@Composable
internal fun PlayableTrackRow(
    track: Track,
    index: Int?,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = LocalNowPlaying.current.playbackOf(track)
    MediaListTrackRow(track = track, index = index, isPlaying = playback.isPlaying, isPaused = playback.isPaused, onClick = onClick, onMoreClick = onMoreClick, onPlayNext = onPlayNext, showDragHandle = false, dragHandleModifier = Modifier, modifier = modifier)
}

@Composable
internal fun PlayableVideoCard(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = LocalNowPlaying.current.playbackOf(track)
    MediaListVideoCard(track = track, isPlaying = playback.isPlaying, isPaused = playback.isPaused, onClick = onClick, onMoreClick = onMoreClick, modifier = modifier)
}
