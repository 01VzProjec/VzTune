#!/bin/bash
# VzTune FIX10 - Canal: abas Videos/Shorts/Playlists + cards novos + submenu novo + creditos
# Aplica sobre FIX4..FIX9. Rode na raiz do projeto: bash VzTune-FIX10-CANAL-TABS-CARDS-SUBMENU-CREDITOS.sh
set -e
python3 - << 'PY'
import os, re, pathlib
ROOT = pathlib.Path(".")
def read(p):
    return pathlib.Path(p).read_text(encoding="utf-8")
def write(p, t):
    pathlib.Path(p).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(p).write_text(t, encoding="utf-8")
    print("OK", p)
def edit(p, fn):
    t = read(p)
    nt = fn(t)
    if nt != t:
        write(p, nt)
    else:
        print("SEM MUDANCA", p)
    return nt

# ============ 1) Track: viewCount + publishedText ============
edit("core/src/main/kotlin/com/vztechcore/vztune/core/domain/music/Track.kt", lambda t: t.replace(
    "    // True when the source flags the track as containing explicit lyrics.\n    val isExplicit: Boolean = false,",
    "    // True when the source flags the track as containing explicit lyrics.\n    val isExplicit: Boolean = false,\n    // Canal / video YouTube (NewPipe StreamInfoItem). Null quando nao vem do YouTube\n    // ou quando o NewPipe nao expoe. Usado nos cards de canal e nos Shorts.\n    val viewCount: Long? = null,\n    // Texto ja formatado em pt-BR: hoje, ha 1 dia, ha 3 semanas, ha 2 meses, ha 1 ano.\n    val publishedText: String? = null,"
))

# ============ 2) YouTubeRepository ============
def fix_yt(t):
    if "java.time.OffsetDateTime" not in t:
        t = t.replace("import javax.inject.Inject", "import java.time.OffsetDateTime\nimport java.time.temporal.ChronoUnit\nimport javax.inject.Inject")
    if "fun formatPublished" not in t:
        t = t.replace(
            "    private fun formatSubscribers",
            "    /** \"hoje\", \"ha 1 dia\", \"ha 3 semanas\", \"ha 5 meses\", \"ha 2 anos\" a partir do uploadDate do NewPipe. */\n    fun formatPublished(uploadDate: OffsetDateTime?): String? {\n        if (uploadDate == null) return null\n        val now = OffsetDateTime.now()\n        val days = ChronoUnit.DAYS.between(uploadDate, now)\n        if (days < 0) return null\n        return when {\n            days == 0L -> \"hoje\"\n            days == 1L -> \"ha 1 dia\"\n            days < 7 -> \"ha $days dias\"\n            days < 30 -> { val w = days / 7; if (w == 1L) \"ha 1 semana\" else \"ha $w semanas\" }\n            days < 365 -> { val m = days / 30; if (m == 1L) \"ha 1 mes\" else \"ha $m meses\" }\n            else -> { val y = days / 365; if (y == 1L) \"ha 1 ano\" else \"ha $y anos\" }\n        }\n    }\n\n    private fun formatSubscribers"
        )
    old_track = 'Track(id = vid, title = stream.name ?: "Untitled", artist = channelName, durationSeconds = stream.duration, thumbnailUrl = stream.thumbnails.lastOrNull()?.url, source = Source.YOUTUBE)'
    new_track = 'Track(id = vid, title = stream.name ?: "Untitled", artist = channelName, durationSeconds = stream.duration, thumbnailUrl = stream.thumbnails.lastOrNull()?.url, source = Source.YOUTUBE, viewCount = stream.viewCount.takeIf { it > 0 }, publishedText = formatPublished(stream.uploadDate))'
    t = t.replace(old_track, new_track)
    def add_views(m):
        s = m.group(0)
        if "viewCount" in s:
            return s
        return s[:-1] + ', viewCount = stream.viewCount.takeIf { it > 0 }, publishedText = formatPublished(stream.uploadDate))'
    t = re.sub(r'Track\(id = vid, title = stream\.name[^\n]*?source = Source\.YOUTUBE\)', add_views, t)
    if "channelPlaylists" not in t:
        t = t.replace(
            "        ChannelPage(",
            "        // Playlists do canal: tenta /playlists como pagina, fallback busca por nome do canal.\n        var channelPlaylists: List<Playlist> = emptyList()\n        if (uc.startsWith(\"UC\")) {\n            channelPlaylists = runCatching {\n                val pl = ServiceList.YouTube.getPlaylistExtractor(\"https://www.youtube.com/channel/$uc/playlists\")\n                pl.fetchPage()\n                pl.initialPage.items.filterIsInstance<PlaylistInfoItem>().mapNotNull { item ->\n                    val pid = Regex(\"list=([^&]+)\").find(item.url ?: \"\")?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null\n                    Playlist(id = pid, title = item.name ?: \"Playlist\", owner = channelName, thumbnailUrl = item.thumbnails.lastOrNull()?.url, trackCount = item.streamCount.takeIf { it > 0 }?.toInt(), source = Source.YOUTUBE)\n                }\n            }.getOrDefault(emptyList())\n        }\n        if (channelPlaylists.isEmpty() && channelName.isNotBlank()) {\n            channelPlaylists = runCatching { searchPlaylists(channelName, loadMore = false, pageSize = 12, searchId = \"channelPlaylists:$uc\").map { it.playlist } }.getOrDefault(emptyList())\n        }\n        ChannelPage("
        )
        t = t.replace("            playlists = emptyList(),", "            playlists = channelPlaylists,")
    if "fun getYouTubePlaylistTracks" not in t:
        t = t.replace(
            "    private fun formatSubscribers",
            "    /** Faixas de uma playlist do YouTube (PL..., UU..., OL...). Usado no menu da playlist do canal. */\n    suspend fun getYouTubePlaylistTracks(playlistId: String): List<Track> = withContext(Dispatchers.IO) {\n        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext emptyList()\n        val url = if (playlistId.startsWith(\"http\")) playlistId else \"https://www.youtube.com/playlist?list=$playlistId\"\n        runCatching {\n            val pl = ServiceList.YouTube.getPlaylistExtractor(url)\n            pl.fetchPage()\n            pl.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { stream ->\n                val vid = extractVideoId(stream.url) ?: return@mapNotNull null\n                Track(id = vid, title = stream.name ?: \"Untitled\", artist = stream.uploaderName ?: \"\", durationSeconds = stream.duration, thumbnailUrl = stream.thumbnails.lastOrNull()?.url, source = Source.YOUTUBE, viewCount = stream.viewCount.takeIf { it > 0 }, publishedText = formatPublished(stream.uploadDate))\n            }\n        }.getOrDefault(emptyList())\n    }\n\n    private fun formatSubscribers"
        )
    return t
edit("data/src/main/kotlin/com/vztechcore/vztune/data/youtube/YouTubeRepository.kt", fix_yt)

# ============ 3) YTMusicRepository: TrackCredits ============
def fix_ytmusic(t):
    if "data class TrackCredits" in t:
        return t
    credits = "\n    /** Creditos que o InnerTube disponibiliza: artista(s), album, ano, views, descricao + compositores/produtores/letra extraidos da descricao. */\n    data class TrackCredits(\n        val artists: List<String>,\n        val albumTitle: String?,\n        val albumYear: String?,\n        val viewCount: String?,\n        val description: String?,\n        val composers: List<String>,\n        val producers: List<String>,\n        val lyricists: List<String>,\n    )\n\n    suspend fun getTrackCredits(track: Track): TrackCredits? = withContext(Dispatchers.IO) {\n        val details = runCatching { getVideoDetails(track.id) }.getOrNull()\n        val album = runCatching { findAlbumForTrack(track) }.getOrNull()\n        val albumRes = if (album != null) runCatching { browseAlbum(album.id) }.getOrNull() else null\n        val desc = details?.description ?: albumRes?.description\n        val artists = track.artist.split(\",\", \"&\", \"feat.\", \"ft.\", \" e \").map { it.trim() }.filter { it.isNotBlank() }.distinct()\n        fun parseRole(keywords: List<String>): List<String> {\n            if (desc.isNullOrBlank()) return emptyList()\n            val out = mutableListOf<String>()\n            for (line in desc.lines()) {\n                val low = line.lowercase()\n                if (keywords.any { low.contains(it) }) {\n                    val after = line.substringAfter(\":\", line).substringAfter(\"-\", line).trim()\n                    if (after.isNotBlank() && after != line) {\n                        after.split(\",\", \"&\", \";\").map { it.trim() }.filter { it.isNotBlank() }.forEach { out.add(it) }\n                    }\n                }\n            }\n            return out.distinct()\n        }\n        TrackCredits(\n            artists = artists,\n            albumTitle = album?.title ?: track.album,\n            albumYear = albumRes?.year,\n            viewCount = details?.viewCount,\n            description = desc,\n            composers = parseRole(listOf(\"compositor\", \"composer\", \"escrito por\", \"written by\")),\n            producers = parseRole(listOf(\"produtor\", \"producer\", \"produzido por\")),\n            lyricists = parseRole(listOf(\"letra\", \"lyricist\", \"letrista\")),\n        )\n    }\n\n"
    if "    // ================= HELPERS =================" in t:
        t = t.replace("    // ================= HELPERS =================", credits + "    // ================= HELPERS =================", 1)
    else:
        t = t.rstrip()
        if t.endswith("}"):
            t = t[:-1] + credits + "}\n"
    return t
edit("data/src/main/kotlin/com/vztechcore/vztune/data/ytmusic/YTMusicRepository.kt", fix_ytmusic)
print("FIX10 parte 1 OK")
PY
python3 - << 'PY'
import pathlib
def read2(p): return pathlib.Path(p).read_text(encoding="utf-8")
def write2(p, t):
    pathlib.Path(p).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(p).write_text(t, encoding="utf-8")
    print("OK", p)
CHANNEL_CARDS = """package com.vztechcore.vztune.ui.components.cards

import android.content.Intent
import androidx.compose.foundation.background
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
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.playbackOf
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.FavoriteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteOutline
import com.vztechcore.vztune.ui.icons.actions.MoreVert
import com.vztechcore.vztune.ui.icons.actions.Share
import com.vztechcore.vztune.ui.icons.media.Lists
import com.vztechcore.vztune.ui.icons.media.PlaylistAdd
import com.vztechcore.vztune.ui.icons.media.QueueMusic
import com.vztechcore.vztune.ui.icons.player.PlayCircleFilled

fun formatViewCount(count: Long?): String? {
    if (count == null || count <= 0) return null
    if (count == 1L) return "1 visualizacao"
    return when {
        count < 1_000 -> "$count visualizacoes"
        count < 1_000_000 -> formatOneDecimal(count / 1_000.0) + " mil visualizacoes"
        count < 1_000_000_000 -> {
            val v = count / 1_000_000.0
            val base = formatOneDecimal(v)
            if (v < 1.05) base + " milhao visualizacoes" else base + " milhoes visualizacoes"
        }
        else -> formatOneDecimal(count / 1_000_000_000.0) + " bilhoes visualizacoes"
    }
}
private fun formatOneDecimal(v: Double): String {
    val s = "%.1f".format(v).replace(".", ",")
    return if (s.endsWith(",0")) s.dropLast(2) else s
}
fun videoMetaLine(track: Track): String? {
    val views = formatViewCount(track.viewCount)
    return listOfNotNull(views, track.publishedText?.takeIf { it.isNotBlank() }).joinToString(" - ").takeIf { it.isNotBlank() }
}
@Composable
fun ChannelVideoCard(track: Track, onClick: () -> Unit, onMoreClick: () -> Unit, modifier: Modifier = Modifier) {
    val nowPlaying = LocalNowPlaying.current
    val playback = nowPlaying.playbackOf(track)
    val isCurrent = playback.isPlaying || playback.isPaused
    Row(modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(160.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))) {
            if (track.thumbnailUrl != null) AsyncImage(model = track.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (isCurrent) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Text(text = if (playback.isPlaying) "Tocando" else "Pausado", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = track.title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val meta = videoMetaLine(track)
            if (!meta.isNullOrBlank()) Text(text = meta, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
            Icon(imageVector = VzIcons.MoreVert, contentDescription = "Mais", tint = Color.White.copy(alpha = 0.75f))
        }
    }
}
@Composable
fun ChannelPlaylistCard(playlist: Playlist, onClick: () -> Unit, onMoreClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(160.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))) {
            val thumb = playlist.thumbnailUrl ?: playlist.mosaicThumbnails.firstOrNull()
            if (thumb != null) AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.78f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(imageVector = VzIcons.Lists, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text(text = if ((playlist.trackCount ?: 0) > 0) playlist.trackCount.toString() + " faixas" else "Playlist", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
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
@Composable
fun ShortCard(track: Track, onClick: () -> Unit, onMoreClick: () -> Unit, modifier: Modifier = Modifier) {
    val nowPlaying = LocalNowPlaying.current
    val playback = nowPlaying.playbackOf(track)
    val isCurrent = playback.isPlaying || playback.isPaused
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }) {
        Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f))) {
            if (track.thumbnailUrl != null) AsyncImage(model = track.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxWidth().height(64.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))))
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable { onMoreClick() }.padding(6.dp)) {
                Icon(imageVector = VzIcons.MoreVert, contentDescription = "Mais", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            val views = formatViewCount(track.viewCount)
            if (!views.isNullOrBlank()) {
                Text(text = views, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 8.dp))
            }
            if (isCurrent) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                Text(text = if (playback.isPlaying) "Tocando" else "Pausado", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
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
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Assiste esse Short no VzTune\\n" + track.title + "\\n" + url) }
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
                Icon(imageVector = VzIcons.PlayCircleFilled, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(text = "Adicionar como proxima da fila", style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Medium)
            }
            Row(Modifier.fillMaxWidth().clickable { onAddToQueue(playlist); onDismiss() }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = VzIcons.QueueMusic, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
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
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Confere essa playlist no VzTune\\n" + playlist.title + "\\n" + url) }
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
"""
write2("app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/ChannelCards.kt", CHANNEL_CARDS)
print("FIX10 ChannelCards OK")
PY
python3 - << 'PY'
import pathlib
def read3(p): return pathlib.Path(p).read_text(encoding="utf-8")
def write3(p, t):
    pathlib.Path(p).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(p).write_text(t, encoding="utf-8")
    print("OK", p)
CREDITS = """package com.vztechcore.vztune.ui.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import com.vztechcore.vztune.ui.components.feedback.VzLoading
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackCreditsViewModel @Inject constructor(
    private val ytMusicRepository: YTMusicRepository,
) : ViewModel() {
    private val _credits = MutableStateFlow<YTMusicRepository.TrackCredits?>(null)
    val credits: StateFlow<YTMusicRepository.TrackCredits?> = _credits.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private var loadedFor: String? = null
    fun load(track: Track) {
        if (loadedFor == track.id) return
        loadedFor = track.id
        _isLoading.value = true
        _credits.value = null
        viewModelScope.launch {
            _credits.value = runCatching { ytMusicRepository.getTrackCredits(track) }.getOrNull()
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackCreditsSheet(track: Track, onDismiss: () -> Unit, viewModel: TrackCreditsViewModel = hiltViewModel()) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val credits by viewModel.credits.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    LaunchedEffect(track.id) { viewModel.load(track) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF121829), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))) {
                    if (track.thumbnailUrl != null) AsyncImage(model = track.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(text = track.title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(text = track.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(text = "Creditos da musica", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (isLoading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { VzLoading(Modifier.size(96.dp)) }
            } else if (credits == null) {
                Text(text = "Creditos nao disponiveis para esta faixa.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
            } else {
                val c = credits!!
                CreditRow(label = "Artista", value = c.artists.joinToString(", ").takeIf { it.isNotBlank() })
                CreditRow(label = "Album", value = c.albumTitle)
                CreditRow(label = "Ano", value = c.albumYear)
                CreditRow(label = "Visualizacoes", value = c.viewCount)
                CreditRow(label = "Compositores", value = c.composers.joinToString(", ").takeIf { it.isNotBlank() })
                CreditRow(label = "Produtores", value = c.producers.joinToString(", ").takeIf { it.isNotBlank() })
                CreditRow(label = "Letra", value = c.lyricists.joinToString(", ").takeIf { it.isNotBlank() })
                if (!c.description.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(text = "Sobre", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(text = c.description, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
@Composable
private fun CreditRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.width(120.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}
"""
write3("app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackCreditsSheet.kt", CREDITS)
print("FIX10 Creditos OK")
PY
python3 - << 'PY'
import pathlib, re
def read4(p): return pathlib.Path(p).read_text(encoding="utf-8")
def write4(p, t):
    pathlib.Path(p).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(p).write_text(t, encoding="utf-8")
    print("OK", p)
def edit4(p, fn):
    t = read4(p); nt = fn(t)
    if nt != t: write4(p, nt)
    else: print("SEM MUDANCA", p)
    return nt

# ============ 6) TrackOptionsSheet: 3 cards no topo + lista reordenada + creditos ============
def fix_sheet(t):
    if "TopActionCard" not in t:
        if "import com.vztechcore.vztune.ui.icons.media.MusicNote" not in t:
            t = t.replace("import com.vztechcore.vztune.ui.icons.media.Download", "import com.vztechcore.vztune.ui.icons.media.Download\nimport com.vztechcore.vztune.ui.icons.media.MusicNote")
        if "import androidx.compose.ui.text.style.TextAlign" not in t:
            t = t.replace("import androidx.compose.ui.text.style.TextOverflow", "import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.text.style.TextOverflow")
    if "var showCredits" not in t:
        t = t.replace("var showPlaylistPicker by remember", "var showCredits by remember { mutableStateOf(false) }\n    var showPlaylistPicker by remember")
    if "if (showCredits)" not in t:
        t = t.replace("    ModalBottomSheet(", "    if (showCredits) {\n        TrackCreditsSheet(track = track, onDismiss = { showCredits = false })\n        return\n    }\n    ModalBottomSheet(", 1)
    # Substitui bloco Context-specific .. ate 9. Track details por novo layout
    pattern = re.compile(r"    // Context-specific top removal actions.*?    // 9\. Track details", re.DOTALL)
    new_block = """    // FIX10: 3 cards no topo (retangulos medios lado a lado, nome abaixo, icone dentro)
    val isYouTubeVideoTop = track.source == Source.YOUTUBE
    val downloadProgressTop = downloadItem?.progress ?: 0f
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val isLocal = menuContext == TrackMenuContext.LOCAL_PLAYLIST
        val isRecent = menuContext == TrackMenuContext.RECENT_HISTORY
        if (isLocal) {
            TopActionCard(icon = VzIcons.DeleteFilled, label = "Excluir da playlist", iconTint = Color(0xFFFF6B6B), onClick = { onRemoveFromLocalPlaylist(track); onDismiss() })
        } else if (isRecent) {
            TopActionCard(icon = VzIcons.DeleteFilled, label = "Apagar", iconTint = Color(0xFFFF6B6B), onClick = { onRemoveFromRecentHistory(track); onDismiss() })
        } else if (isDownloading) {
            TopActionCard(icon = VzIcons.Download, label = "Baixando...", iconTint = DownloadGreen, onClick = { onOpenDownloadsScreen(); onDismiss() })
        } else if (isDownloaded) {
            TopActionCard(icon = VzIcons.DownloadDone, label = "Baixada", iconTint = DownloadGreen, onClick = { showRemoveDownloadSheet = true })
        } else {
            TopActionCard(icon = VzIcons.Download, label = "Baixar faixa", iconTint = Color.White, onClick = { onDownload(track); onDismiss() })
        }
        TopActionCard(icon = VzIcons.PlaylistAdd, label = "Adicionar a uma playlist", iconTint = Color.White, onClick = { showPlaylistPicker = true })
        TopActionCard(icon = VzIcons.Share, label = "Compartilhar", iconTint = Color.White, onClick = { shareTrack(context, track); onDismiss() })
    }
    // 1. Baixar (somente playlist local / recentemente tocadas: vira primeiro da lista)
    if (menuContext == TrackMenuContext.LOCAL_PLAYLIST || menuContext == TrackMenuContext.RECENT_HISTORY) {
        if (isDownloading) {
            SheetDownloadProgressRow(progress = downloadProgressTop, label = "Baixando...", onClick = { onOpenDownloadsScreen(); onDismiss() })
        } else if (isDownloaded) {
            SheetOptionRow(icon = VzIcons.DownloadDone, label = "Baixada", subtitle = "Ver em Downloads", iconTint = DownloadGreen, onClick = { onOpenDownloadsScreen(); onDismiss() })
        } else {
            SheetOptionRow(icon = VzIcons.Download, label = "Baixar faixa", subtitle = "Ouvir offline", onClick = { onDownload(track); onDismiss() })
        }
    }
    // 2. Salvar aos favoritos
    SheetOptionRow(
        icon = if (isFavorite) VzIcons.FavoriteFilled else VzIcons.FavoriteOutline,
        label = if (isFavorite) "Remover dos favoritos" else "Salvar aos favoritos",
        iconTint = if (isFavorite) Color(0xFFEC4899) else Color.White,
        onClick = { onToggleFavorite(track); onDismiss() },
    )
    // 3. Reproduzir como proxima faixa
    SheetOptionRow(icon = VzIcons.PlayCircleFilled, label = "Reproduzir como proxima faixa", subtitle = "Toca logo depois da atual", onClick = { onPlayNext(track); onDismiss() })
    // 4. Adicionar a fila
    SheetOptionRow(icon = VzIcons.QueueMusic, label = "Adicionar a fila", subtitle = "Toca no final da fila", onClick = { onAddToQueue(track); onDismiss() })
    // 5. Iniciar radio
    SheetOptionRow(icon = VzIcons.Radio, label = "Iniciar radio", subtitle = "Baseada nesta faixa", onClick = { onStartRadio(track); onDismiss() })
    // 6. Ir para o album
    if (resolvedAlbum != null || !track.album.isNullOrBlank()) {
        SheetOptionRow(
            icon = VzIcons.Album,
            label = "Ir para o album",
            subtitle = resolvedAlbum?.title ?: track.album,
            onClick = {
                if (resolvedAlbum != null) onOpenAlbum(resolvedAlbum) else onOpenAlbumForTrack(track)
                onDismiss()
            },
        )
    }
    // 7. Ir para a pagina do artista (1 artista / varios artistas / video = canal)
    if (artists.isNotEmpty()) {
        val hasMultipleArtists = artists.size > 1 && !isYouTubeVideoTop
        SheetOptionRow(
            icon = if (hasMultipleArtists) VzIcons.Groups else VzIcons.Artist,
            label = when {
                isYouTubeVideoTop -> "Ir para o canal"
                hasMultipleArtists -> "Ir para a pagina do artista..."
                else -> "Ir para a pagina do artista"
            },
            subtitle = if (hasMultipleArtists) artists.joinToString(", ") else artists.first(),
            onClick = {
                if (hasMultipleArtists) {
                    showArtistPicker = true
                } else {
                    onDismiss()
                    onOpenArtistForTrack(track, artists.first())
                }
            },
        )
    }
    // 8. Mostrar creditos (somente musica ou video clipe da musica)
    val showCreditsOption = track.source == Source.YOUTUBE_MUSIC || (track.source == Source.YOUTUBE && !track.album.isNullOrBlank())
    if (showCreditsOption) {
        SheetOptionRow(icon = VzIcons.MusicNote, label = "Mostrar creditos da musica", subtitle = "Compositores, produtores e mais", onClick = { showCredits = true })
    }
    // 9. Track details"""
    if pattern.search(t):
        t = pattern.sub(new_block, t, count=1)
    else:
        print("AVISO: bloco Context-specific nao encontrado em TrackOptionsSheet")
    # TopActionCard no fim do arquivo
    if "fun TopActionCard" not in t:
        t = t.rstrip() + "\n\n@Composable\nprivate fun TopActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, iconTint: Color = Color.White, onClick: () -> Unit) {\n    Column(Modifier.weight(1f).clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {\n        Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {\n            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))\n        }\n        Spacer(Modifier.height(6.dp))\n        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)\n    }\n}\n"
    return t
edit4("app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackOptionsSheet.kt", fix_sheet)
print("FIX10 TrackOptionsSheet OK")
PY
python3 - << 'PY'
import pathlib, re
def read5(p): return pathlib.Path(p).read_text(encoding="utf-8")
def write5(p, t):
    pathlib.Path(p).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(p).write_text(t, encoding="utf-8")
    print("OK", p)
def edit5(p, fn):
    t = read5(p); nt = fn(t)
    if nt != t: write5(p, nt)
    else: print("SEM MUDANCA", p)
    return nt

# ============ 7) ArtistViewModel: shorts/playlists/banner + acoes de playlist + onShortClick ============
def fix_artist_vm(t):
    if "LocalPlaylistsStore" not in t:
        t = t.replace("import com.vztechcore.vztune.data.prefs.store.FollowedArtistsStore", "import com.vztechcore.vztune.data.prefs.models.LocalPlaylist\nimport com.vztechcore.vztune.data.prefs.models.StoredTrack\nimport com.vztechcore.vztune.data.prefs.store.FollowedArtistsStore\nimport com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore\nimport com.vztechcore.vztune.core.domain.music.Playlist")
    if "localPlaylistsStore" not in t:
        t = t.replace("    private val followedStore: FollowedArtistsStore,", "    private val followedStore: FollowedArtistsStore,\n    private val localPlaylistsStore: LocalPlaylistsStore,")
    # Canal: preenche shorts/playlists/banner em todos os branches de canal
    # Branch 1: mesmo artista nos dois mundos (music + canal)
    t = t.replace(
        "                            videos = if (channel.videos.isNotEmpty()) channel.videos else music.videos,",
        "                            videos = if (channel.videos.isNotEmpty()) channel.videos else music.videos,\n                            shorts = channel.shorts, channelPlaylists = channel.playlists, bannerUrl = channel.bannerUrl ?: it.bannerUrl, isYouTubeChannel = true,"
    )
    # Branch 2: canal real existe (ignora YT Music)
    t = t.replace(
        "                            videos = channel.videos,\n                        )\n                    }\n                    return@launch\n                }\n            }\n            if (!musicEmpty",
        "                            videos = channel.videos, shorts = channel.shorts, channelPlaylists = channel.playlists, bannerUrl = channel.bannerUrl ?: it.bannerUrl, isYouTubeChannel = true,\n                        )\n                    }\n                    return@launch\n                }\n            }\n            if (!musicEmpty"
    )
    # Branch 3: fallback channel != null (so canal)
    t = t.replace(
        "it.copy(isLoading = false, errorMessage = null, name = chName ?: it.name, thumbnailUrl = channel.avatarUrl ?: it.thumbnailUrl, subscribers = channel.subscriberCount ?: it.subscribers, videos = channel.videos)",
        "it.copy(isLoading = false, errorMessage = null, name = chName ?: it.name, thumbnailUrl = channel.avatarUrl ?: it.thumbnailUrl, subscribers = channel.subscriberCount ?: it.subscribers, videos = channel.videos, shorts = channel.shorts, channelPlaylists = channel.playlists, bannerUrl = channel.bannerUrl ?: it.bannerUrl, isYouTubeChannel = true, description = channel.description ?: it.description)"
    )
    # Metodos novos no fim da classe (antes do ultimo })
    if "fun onShortClick" not in t:
        t = t.rstrip()
        assert t.endswith("}")
        t = t[:t.rfind("}")] + """
    fun onShortClick(track: Track) {
        val shorts = _uiState.value.shorts
        val idx = shorts.indexOfFirst { it.id == track.id }
        if (idx >= 0) playerController.playQueue(shorts, idx) else playerController.playQueue(listOf(track), 0)
    }

    private suspend fun loadChannelPlaylistTracks(playlist: Playlist): List<Track> {
        return runCatching { ytMusicRepository.browsePlaylist(playlist.id).tracks }.getOrDefault(emptyList())
    }

    fun onPlaylistPlayNext(playlist: Playlist) {
        viewModelScope.launch {
            val tracks = loadChannelPlaylistTracks(playlist)
            if (tracks.isEmpty()) return@launch
            for (track in tracks.asReversed()) playerController.addToQueueNext(track)
        }
    }

    fun onPlaylistAddToQueue(playlist: Playlist) {
        viewModelScope.launch {
            val tracks = loadChannelPlaylistTracks(playlist)
            for (track in tracks) playerController.addToQueueEnd(track)
        }
    }

    fun onSavePlaylistToLibrary(playlist: Playlist) {
        viewModelScope.launch {
            val tracks = loadChannelPlaylistTracks(playlist)
            val stored = tracks.map { StoredTrack.from(it) }
            localPlaylistsStore.addFirst(LocalPlaylist(id = playlist.id, name = playlist.title, coverPath = playlist.thumbnailUrl, tracks = stored))
        }
    }
}
"""
    return t
edit5("app/src/main/kotlin/com/vztechcore/vztune/ui/screens/artist/ArtistViewModel.kt", fix_artist_vm)
print("FIX10 ArtistViewModel OK")
PY
