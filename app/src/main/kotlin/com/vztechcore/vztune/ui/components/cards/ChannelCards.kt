package com.vztechcore.vztune.ui.components.cards

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.R
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import com.vztechcore.vztune.ui.components.player.playbackOf
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.FavoriteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteOutline
import com.vztechcore.vztune.ui.icons.actions.MoreVert
import com.vztechcore.vztune.ui.icons.actions.Share
import com.vztechcore.vztune.ui.icons.player.PlayArrow
import com.vztechcore.vztune.ui.icons.media.Lists
import com.vztechcore.vztune.ui.icons.media.PlaylistAdd
import com.vztechcore.vztune.ui.icons.actions.PlayNext
import com.vztechcore.vztune.ui.icons.actions.AddToQueue

fun formatViewCount(count: Long?): String? {
    if (count == null || count <= 0) return null
    if (count == 1L) return "1 visualização"
    return when {
        count < 1_000 -> "$count visualizações"
        count < 1_000_000 -> formatOneDecimal(count / 1_000.0) + " mil visualizações"
        count < 1_000_000_000 -> {
            val v = count / 1_000_000.0
            val base = formatOneDecimal(v)
            if (v < 1.05) base + " milhão visualizações" else base + " milhões visualizações"
        }
        else -> formatOneDecimal(count / 1_000_000_000.0) + " bilhões visualizações"
    }
}

private fun formatOneDecimal(v: Double): String {
    val s = "%.1f".format(v).replace(".", ",")
    return if (s.endsWith(",0")) s.dropLast(2) else s
}

/** Duração "m:ss" / "h:mm:ss" pra bolha da thumbnail. Null quando não sabe. */
fun formatDurationLabel(seconds: Long): String? {
    if (seconds <= 0) return null
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "1,2 mi visualizações • há 2 dias" (o que existir dos dois). */
fun videoViewsDate(track: Track): String? {
    val views = formatViewCount(track.viewCount)
    val date = track.publishedText?.takeIf { it.isNotBlank() }
    return listOfNotNull(views, date).joinToString(" • ").takeIf { it.isNotBlank() }
}

fun videoMetaLine(track: Track): String? = videoViewsDate(track)

/** Bolha preta com a duração total do vídeo (canto da thumbnail decidido pelo pai). */
@Composable
fun DurationBubble(seconds: Long, modifier: Modifier = Modifier) {
    val label = formatDurationLabel(seconds) ?: return
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun ChannelVideoCard(track: Track, onClick: () -> Unit, onMoreClick: () -> Unit, modifier: Modifier = Modifier) {
    val nowPlaying = LocalNowPlaying.current
    val playback = nowPlaying.playbackOf(track)
    Row(modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        // Thumbnail na esquerda + bolha de duração no canto inferior esquerdo.
        Box(Modifier.width(160.dp)) {
            MediaThumbnail(
                imageUrl = track.thumbnailUrl,
                shape = ThumbnailShape.WIDE,
                cornerRadius = 10.dp,
                isPlaying = playback.isPlaying,
                isPaused = playback.isPaused,
                placeholderRes = R.drawable.holder_video,
            )
            DurationBubble(
                seconds = track.durationSeconds,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = track.title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
val viewsDate = videoViewsDate(track)
Row(verticalAlignment = Alignment.CenterVertically) {
if (track.artist.isNotBlank()) {
Text(text = track.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
}
if (!viewsDate.isNullOrBlank()) {
if (track.artist.isNotBlank()) Spacer(Modifier.width(6.dp))
Icon(imageVector = VzIcons.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
Spacer(Modifier.width(2.dp))
Text(text = viewsDate, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
}
}
        }
        IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
            Icon(imageVector = VzIcons.MoreVert, contentDescription = "Mais", tint = Color.White.copy(alpha = 0.75f))
        }
    }
}

@Composable
fun ChannelPlaylistCard(playlist: Playlist, onClick: () -> Unit, onMoreClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(160.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))) {
            val thumb = playlist.thumbnailUrl ?: playlist.mosaicThumbnails.firstOrNull()
            if (thumb != null) AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.78f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(imageVector = VzIcons.Lists, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text(text = if ((playlist.trackCount ?: 0) > 0) playlist.trackCount.toString() else "Playlist", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = playlist.title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val owner = playlist.owner?.takeIf { it.isNotBlank() }
            val sub = listOfNotNull(owner, "Playlist").joinToString(" - ")
            Text(text = sub, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
            Icon(imageVector = VzIcons.MoreVert, contentDescription = "Mais", tint = Color.White.copy(alpha = 0.75f))
        }
    }
}

/**
 * Card do Short (grade 9:16). SEM overlay "Tocando/Pausado" - o playback
 * agora mora na tela Shorts. Bolha de duração no canto SUPERIOR ESQUERDO
 * (quando a API informa a duração; a listagem de Shorts nem sempre traz).
 */
@Composable
fun ShortCard(track: Track, onClick: () -> Unit, onMoreClick: () -> Unit, modifier: Modifier = Modifier) {
    val thumb = track.thumbnailUrl?.takeIf { it.isNotBlank() } ?: "https://i.ytimg.com/vi/${track.id}/hqdefault.jpg"
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }) {
        Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))) {
            AsyncImage(model = thumb, contentDescription = track.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxWidth().height(96.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))))
            DurationBubble(
                seconds = track.durationSeconds,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            )
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable { onMoreClick() }.padding(6.dp)) {
                Icon(imageVector = VzIcons.MoreVert, contentDescription = "Mais", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 8.dp)) {
                if (track.title.isNotBlank()) {
                    Text(text = track.title, style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val views = formatViewCount(track.viewCount)
                if (!views.isNullOrBlank()) {
                    Text(text = views, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun ShortsGridRows(tracks: List<Track>, onClick: (Track) -> Unit, onMoreClick: (Track) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tracks.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { track ->
                    Box(Modifier.weight(1f)) { ShortCard(track = track, onClick = { onClick(track) }, onMoreClick = { onMoreClick(track) }) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Menu do Short: SOMENTE favoritos + compartilhar (pedido da Parte 1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsOptionsSheet(track: Track, isFavorite: Boolean, onToggleFavorite: (Track) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF121829), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))) {
                    if (track.thumbnailUrl != null) AsyncImage(model = track.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(text = track.title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = track.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth().clickable { onToggleFavorite(track); onDismiss() }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = if (isFavorite) VzIcons.FavoriteFilled else VzIcons.FavoriteOutline, contentDescription = null, tint = if (isFavorite) Color(0xFFEC4899) else Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(text = if (isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos", style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Medium)
            }
            Row(Modifier.fillMaxWidth().clickable {
                val url = if (track.source == Source.YOUTUBE) "https://www.youtube.com/shorts/" + track.id else "https://music.youtube.com/watch?v=" + track.id
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Assiste esse Short no VzTune\n" + track.title + "\n" + url) }
                context.startActivity(Intent.createChooser(send, "Compartilhar Short"))
                onDismiss()
            }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = VzIcons.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(text = "Compartilhar", style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistOptionsSheet(playlist: Playlist, onDismiss: () -> Unit, onPlayNext: (Playlist) -> Unit, onAddToQueue: (Playlist) -> Unit, onSaveToLibrary: (Playlist) -> Unit, onShare: (Playlist) -> Unit = {}) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF121829), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))) {
                    val thumb = playlist.thumbnailUrl ?: playlist.mosaicThumbnails.firstOrNull()
                    if (thumb != null) AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(text = playlist.title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = listOfNotNull(playlist.owner, "Playlist").joinToString(" - "), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth().clickable { onPlayNext(playlist); onDismiss() }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = VzIcons.PlayNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(text = "Adicionar como proxima da fila", style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Medium)
            }
            Row(Modifier.fillMaxWidth().clickable { onAddToQueue(playlist); onDismiss() }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = VzIcons.AddToQueue, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(text = "Adicionar ao final da fila", style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Medium)
            }
            Row(Modifier.fillMaxWidth().clickable { onSaveToLibrary(playlist); onDismiss() }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = VzIcons.PlaylistAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(text = "Salvar na biblioteca", style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Medium)
            }
            Row(Modifier.fillMaxWidth().clickable {
                val url = if (playlist.source == Source.YOUTUBE) "https://www.youtube.com/playlist?list=" + playlist.id else "https://music.youtube.com/playlist?list=" + playlist.id
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Confere essa playlist no VzTune\n" + playlist.title + "\n" + url) }
                context.startActivity(Intent.createChooser(send, "Compartilhar playlist"))
                onShare(playlist)
                onDismiss()
            }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = VzIcons.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(text = "Compartilhar", style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}
