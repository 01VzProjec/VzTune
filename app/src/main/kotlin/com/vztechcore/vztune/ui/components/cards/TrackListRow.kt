package com.vztechcore.vztune.ui.components.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vztechcore.vztune.core.domain.music.Track

/**
 * Atalho fino, mantido pra nao quebrar as telas que ja usam TrackListRow.
 * Logica mora em VzTrackRow (mesma pasta). Codigo novo: use VzTrackRow direto.
 */
@Composable
fun TrackListRow(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
) {
    VzTrackRow(track = track, onClick = onClick, onMoreClick = onMoreClick, number = number, modifier = modifier)
}
