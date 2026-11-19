package com.vztechcore.vztune.ui.components.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.player.PlayingEqualizer
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import com.vztechcore.vztune.ui.components.player.playbackOf
import com.vztechcore.vztune.ui.components.rows.SwipeableRow
import com.vztechcore.vztune.ui.components.text.TrackSubtitle
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.DragHandle
import com.vztechcore.vztune.ui.icons.actions.MoreVert

/**
 * UNICO card de faixa do app. Single source of truth.
 * Antes: TrackListRow (Library/Artista) + MediaListTrackRow (Album/Playlist) + SearchResultCard (Search).
 * Agora todas chamam este. Visual canonico: thumbnail 52dp, titulo SemiBold,
 * subtitulo TrackSubtitle (ja mostra download + "E" + "Song — artista"), ••• 40dp, padding 6dp.
 * Library passa a mostrar "Song — artista" igual MediaList/Search, em vez de so artista.
 * - [number]: numero pequeno a esquerda + thumbnail (Artista)
 * - [index]: modo album, numero GRANDE / equalizador / ••• NO LUGAR da thumbnail
 * - [onPlayNext]: se nao for null, liga o arrastar pra tocar em seguida
 * - [isPlaying]/[isPaused]: se null, resolve sozinho via playbackOf(track)
 */
@Composable
fun VzTrackRow(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    number: Int? = null,
    index: Int? = null,
    isPlaying: Boolean? = null,
    isPaused: Boolean? = null,
    durationLabel: String? = null,
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val playback = LocalNowPlaying.current.playbackOf(track)
    val resolvedIsPlaying = isPlaying ?: playback.isPlaying
    val resolvedIsPaused = isPaused ?: playback.isPaused
    val rowContent: @Composable () -> Unit = {
        Row(modifier.fillMaxWidth().clickable { onClick() }.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (index != null) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    when {
                        resolvedIsPlaying -> PlayingEqualizer(color = Color.White, barCount = 3, maxHeight = 20.dp, barWidth = 3.dp, gap = 3.dp)
                        resolvedIsPaused -> Text(text = "•••", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        else -> Text(text = "$index", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                if (number != null) {
                    Text(text = number.toString(), style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center, modifier = Modifier.width(26.dp))
                }
                MediaThumbnail(imageUrl = track.thumbnailUrl, shape = ThumbnailShape.SQUARE, size = 52.dp, cornerRadius = 6.dp, isPlaying = resolvedIsPlaying, isPaused = resolvedIsPaused)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text = track.title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                TrackSubtitle(track = track, style = MaterialTheme.typography.bodySmall, prefixColor = Color.White.copy(alpha = 0.55f), artistColor = Color.White.copy(alpha = 0.6f))
            }
            if (durationLabel != null) {
                Spacer(Modifier.width(8.dp))
                Text(text = durationLabel, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
                Icon(imageVector = VzIcons.MoreVert, contentDescription = "Mais", tint = Color.White.copy(alpha = 0.7f))
            }
            if (showDragHandle) {
                Icon(imageVector = VzIcons.DragHandle, contentDescription = "Arrastar", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(32.dp).padding(4.dp).then(dragHandleModifier))
            }
        }
    }
    if (onPlayNext != null) { SwipeableRow(onPlayNext = onPlayNext) { rowContent() } } else { rowContent() }
}
