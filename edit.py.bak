#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
edit.py - VzTune: Shorts (melhor qualidade + HLS fix + UI) e Cards (views/data/layout).
Uso (na raiz do projeto):
    python3 edit.py --dry   # so confere, NAO grava
    python3 edit.py         # aplica (sem .bak)
Tudo-ou-nada: se qualquer ancora nao bater, nada e gravado. Idempotente.
"""
import os, re, sys

ROOT = os.path.dirname(os.path.abspath(__file__))
APP = "app/src/main/kotlin/com/vztechcore/vztune"
DATA = "data/src/main/kotlin/com/vztechcore/vztune/data"

class PatchError(Exception): pass

def _flex_regex(anchor):
    lines = anchor.strip("\n").split("\n")
    if not lines or lines[0].strip() == "":
        raise PatchError("ancora invalida")
    parts = [r"(?m)^[ \t]*" + re.escape(lines[0].strip())]
    for ln in lines[1:]:
        t = ln.strip()
        parts.append(r"\n[ \t]*" + (re.escape(t) if t else ""))
    return re.compile("".join(parts))

def _find_all(s, a): return list(_flex_regex(a).finditer(s))

class Replace:
    def __init__(self, old, new, marker=None, all=False, optional=False):
        self.old, self.new = old, new
        self.marker = marker if marker is not None else new.strip("\n")
        self.all = all
        self.optional = optional
    def apply(self, s):
        if self.marker and self.marker in s: return s, "ja aplicado"
        ms = _find_all(s, self.old)
        if not ms or (not self.all and len(ms) != 1):
            if self.optional: return s, "pulando"
            raise PatchError("trecho %dx (esperado %s): %r" % (len(ms), ">=1" if self.all else "1", self.old.strip()[:80]))
        return _flex_regex(self.old).sub(lambda m: self.new, s), "ok"

class ReplaceAny:
    """Tenta varias ancoras alternativas; aplica a primeira que existir 1x."""
    def __init__(self, candidates, new, marker):
        self.candidates, self.new, self.marker = candidates, new, marker
    def apply(self, s):
        if self.marker in s: return s, "ja aplicado"
        for cand in self.candidates:
            ms = _find_all(s, cand)
            if len(ms) == 1:
                return _flex_regex(cand).sub(lambda m: self.new, s), "ok"
        raise PatchError("nenhuma variante encontrada: %r" % self.candidates[0].strip()[:80])

class InsertBefore:
    def __init__(self, anchor, text, marker=None):
        self.anchor, self.text = anchor, text
        self.marker = marker if marker is not None else text.strip("\n")
    def apply(self, s):
        if self.marker and self.marker in s: return s, "ja aplicado"
        ms = _find_all(s, self.anchor)
        if len(ms) != 1:
            raise PatchError("ancora %dx (esperado 1): %r" % (len(ms), self.anchor.strip()[:80]))
        i = ms[0].start()
        return s[:i] + self.text.rstrip("\n") + "\n" + s[i:], "ok"

class ReplaceBetween:
    def __init__(self, start, end, new, marker):
        self.start, self.end, self.new, self.marker = start, end, new, marker
    def apply(self, s):
        if self.marker in s: return s, "ja aplicado"
        ms = _find_all(s, self.start)
        if len(ms) != 1: raise PatchError("inicio %dx: %r" % (len(ms), self.start.strip()[:80]))
        a = ms[0]
        me = _flex_regex(self.end).search(s, a.end())
        if me is None: raise PatchError("fim nao encontrado: %r" % self.end.strip()[:80])
        return s[:a.start()] + self.new.rstrip("\n") + "\n" + s[me.start():], "ok"

NEW_FILES = {}
PATCHES = {}

# ================= Novo VzVideoCard (meta acompanha o titulo) =================
NEW_FILES[f"{APP}/ui/components/cards/VzVideoCard.kt"] = r'''package com.vztechcore.vztune.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.MoreVert
import com.vztechcore.vztune.ui.icons.media.Artist
import com.vztechcore.vztune.ui.icons.player.PlayArrow

/**
 * UNICO card de video 16:9 do app.
 * A linha de meta fica DENTRO da coluna do titulo, entao acompanha o titulo:
 * titulo de 1 linha -> meta na linha 2; titulo de 2 linhas -> meta na linha 3.
 * [showChannelRow]=true  -> [foto][nome][play_arrow views data]
 * [showChannelRow]=false -> [nome][play_arrow views data]  ("Para voce")
 */
@Composable
fun VzVideoCard(
    track: Track,
    isPlaying: Boolean,
    isPaused: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 12.dp,
    showChannelRow: Boolean = true,
) {
    val viewsDate = videoViewsDate(track)
    Column(
        modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = horizontalPadding, vertical = 8.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            MediaThumbnail(
                imageUrl = track.thumbnailUrl,
                shape = ThumbnailShape.WIDE,
                cornerRadius = 10.dp,
                isPlaying = isPlaying,
                isPaused = isPaused,
            )
            DurationBubble(
                seconds = track.durationSeconds,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showChannelRow) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!track.channelAvatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = track.channelAvatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    imageVector = VzIcons.Artist,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(11.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!viewsDate.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = VzIcons.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = viewsDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = VzIcons.MoreVert,
                    contentDescription = "Mais",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
'''

# ================= Repository: SHORTS com video-only + audio merge =================
_SHORTS_BLOCK = r'''    // ================= SHORTS (tela cheia) =================
    /** Stream de video pra tela Shorts. [audioUrl] != null = video-only que precisa merge com audio. */
    data class ShortVideoStream(val url: String, val qualityLabel: String, val height: Int, val audioUrl: String? = null)

    /** Tudo que a tela Shorts precisa de um video: streams + detalhes. */
    data class ShortPlaybackInfo(
        val streams: List<ShortVideoStream>,
        val viewCount: Long?,
        val likeCount: Long?,
        val description: String?,
        val publishedText: String?,
        val channelAvatarUrl: String?,
        val durationSeconds: Long,
        val publishDateText: String? = null,
    )

    private val shortMonths = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")

    /**
     * Busca streams de VIDEO + detalhes do Short.
     * NewPipe: videoStreams (muxed, com audio) + videoOnlyStreams (video-only, merge com audio).
     * InnerTube: adaptiveFormats (video-only + audio) e formats (muxed) como extra/fallback.
     * Ordena da MAIOR qualidade pra menor; HLS (height 0) fica por ultimo.
     */
    suspend fun getShortPlaybackInfo(videoId: String): ShortPlaybackInfo? = withContext(Dispatchers.IO) {
        if (!NewPipeInitializer.ensureInitialized(downloader)) return@withContext null
        val np = runCatching {
            val ex = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            ex.fetchPage()
            ex
        }.getOrNull()
        val streams = mutableListOf<ShortVideoStream>()
        var npAudio: String? = null
        if (np != null) {
            npAudio = runCatching {
                np.audioStreams.filter { !it.content.isNullOrBlank() }.maxByOrNull { it.averageBitrate }?.content
            }.getOrNull()
            runCatching {
                np.videoStreams.forEach { s ->
                    val u = s.content
                    if (!u.isNullOrBlank()) {
                        val res = s.resolution.orEmpty()
                        val h = res.removeSuffix("p").toIntOrNull() ?: 0
                        streams += ShortVideoStream(u, res.ifBlank { "Auto" }, h)
                    }
                }
            }
            runCatching {
                np.videoOnlyStreams.forEach { s ->
                    val u = s.content
                    if (!u.isNullOrBlank() && npAudio != null) {
                        val res = s.resolution.orEmpty()
                        val h = res.removeSuffix("p").toIntOrNull() ?: 0
                        streams += ShortVideoStream(u, res.ifBlank { "Auto" }, h, npAudio)
                    }
                }
            }
        }
        var itDesc: String? = null
        var itViews: Long? = null
        runCatching {
            val player = InnerTubeYouTube.player(videoId)
            itDesc = player.optJSONObject("videoDetails")?.optString("shortDescription")?.takeIf { it.isNotBlank() }
            itViews = player.optJSONObject("videoDetails")?.optString("viewCount")?.toLongOrNull()?.takeIf { it > 0 }
            val streaming = player.optJSONObject("streamingData")
            val adaptive = streaming?.optJSONArray("adaptiveFormats")
            var itAudio: String? = null
            var itAudioBr = -1L
            if (adaptive != null) {
                for (i in 0 until adaptive.length()) {
                    val f = adaptive.optJSONObject(i) ?: continue
                    if (!f.optString("mimeType").contains("audio")) continue
                    val url = f.optString("url").takeIf { it.isNotBlank() } ?: continue
                    val br = f.optLong("bitrate", 0)
                    if (br > itAudioBr) { itAudioBr = br; itAudio = url }
                }
                for (i in 0 until adaptive.length()) {
                    val f = adaptive.optJSONObject(i) ?: continue
                    if (!f.optString("mimeType").contains("video")) continue
                    val url = f.optString("url").takeIf { it.isNotBlank() } ?: continue
                    val h = f.optInt("height", 0)
                    val label = f.optString("qualityLabel").takeIf { it.isNotBlank() } ?: (h.toString() + "p")
                    val audio = itAudio ?: npAudio
                    if (h > 0 && streams.none { it.height == h } && audio != null) {
                        streams += ShortVideoStream(url, label, h, audio)
                    }
                }
            }
            val formats = streaming?.optJSONArray("formats")
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val f = formats.optJSONObject(i) ?: continue
                    val url = f.optString("url").takeIf { it.isNotBlank() } ?: continue
                    val h = f.optInt("height", 0)
                    val label = f.optString("qualityLabel").takeIf { it.isNotBlank() } ?: f.optString("quality").ifBlank { "Auto" }
                    if (h <= 0 || streams.none { it.height == h }) streams += ShortVideoStream(url, label, h)
                }
            }
        }.onFailure { Log.w(TAG, "InnerTube short formats failed: ${it.message}") }
        runCatching { np?.hlsUrl?.takeIf { it.isNotBlank() }?.let { streams += ShortVideoStream(it, "Auto (HLS)", 0) } }
        if (streams.isEmpty()) return@withContext null
        val sorted = streams.sortedByDescending { it.height }
        val dateText = runCatching { np?.uploadDate?.offsetDateTime() }.getOrNull()?.let { d ->
            "%02d, %s, %d".format(d.dayOfMonth, shortMonths[(d.monthValue - 1).coerceIn(0, 11)], d.year)
        }
        ShortPlaybackInfo(
            streams = sorted,
            viewCount = runCatching { np?.viewCount?.takeIf { it > 0 } }.getOrNull() ?: itViews,
            likeCount = runCatching { np?.likeCount?.takeIf { it > 0 } }.getOrNull(),
            description = runCatching { np?.description?.content }.getOrNull()?.takeIf { it.isNotBlank() } ?: itDesc,
            publishedText = formatPublished(runCatching { np?.uploadDate }.getOrNull()),
            channelAvatarUrl = runCatching { np?.uploaderAvatars?.lastOrNull()?.url }.getOrNull(),
            durationSeconds = runCatching { np?.length ?: 0L }.getOrDefault(0L),
            publishDateText = dateText,
        )
    }
'''
PATCHES[f"{DATA}/youtube/YouTubeRepository.kt"] = [
    ReplaceBetween("    // ================= SHORTS (tela cheia) =================", "    private companion object {", _SHORTS_BLOCK, marker="publishDateText"),
]

# ================= ShortsViewModel: merge video+audio =================
_PLAYSTREAM = r'''    private fun playStream(stream: YouTubeRepository.ShortVideoStream?, startPosMs: Long) {
        if (stream == null) return
        val audioUrl = stream.audioUrl
        if (audioUrl != null) {
            val factory = ProgressiveMediaSource.Factory(DefaultHttpDataSource.Factory())
            val video = factory.createMediaSource(MediaItem.fromUri(stream.url))
            val audio = factory.createMediaSource(MediaItem.fromUri(audioUrl))
            player.setMediaSource(MergingMediaSource(true, true, video, audio))
            if (startPosMs > 0) player.seekTo(startPosMs)
        } else {
            player.setMediaItem(MediaItem.fromUri(stream.url), startPosMs)
        }
        player.prepare()
        player.playWhenReady = true
        _state.update { it.copy(selectedQuality = stream.qualityLabel, isLoading = true) }
    }
'''
PATCHES[f"{APP}/ui/screens/shorts/ShortsViewModel.kt"] = [
    InsertBefore("import androidx.media3.exoplayer.ExoPlayer",
        "import androidx.media3.datasource.DefaultHttpDataSource\nimport androidx.media3.exoplayer.source.MergingMediaSource\nimport androidx.media3.exoplayer.source.ProgressiveMediaSource",
        marker="import androidx.media3.exoplayer.source.MergingMediaSource"),
    ReplaceBetween("    private fun playStream(stream: YouTubeRepository.ShortVideoStream?, startPosMs: Long) {", "    fun selectQuality(label: String) {", _PLAYSTREAM, marker="MergingMediaSource(true, true"),
]

# ================= ShortsScreen: UI =================
_PROGRESS_BAR = r'''/** Barrinha inferior de progresso do Short: mostra e permite arrastar pra buscar. */
@Composable
private fun ShortsProgressBar(player: ExoPlayer, modifier: Modifier = Modifier) {
    var progress by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player) {
        while (true) {
            if (!dragging) {
                val d = player.duration
                progress = if (d > 0) (player.currentPosition.toFloat() / d.toFloat()).coerceIn(0f, 1f) else 0f
            }
            delay(200)
        }
    }
    val shown = if (dragging) dragProgress else progress
    Box(
        modifier
            .fillMaxWidth()
            .height(16.dp)
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    val d = player.duration
                    if (d > 0) player.seekTo((off.x / size.width.toFloat() * d).toLong())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        dragging = true
                        dragProgress = (off.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragging = false
                        val d = player.duration
                        if (d > 0) player.seekTo((dragProgress * d).toLong())
                    },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    dragProgress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                }
            },
        contentAlignment = Alignment.BottomStart,
    ) {
        Box(Modifier.fillMaxWidth().height(if (dragging) 6.dp else 3.dp).background(Color.White.copy(alpha = 0.25f)))
        if (shown > 0f) {
            Box(Modifier.fillMaxWidth(shown).height(if (dragging) 6.dp else 3.dp).background(Color.White))
        }
    }
}
'''
PATCHES[f"{APP}/ui/screens/shorts/ShortsScreen.kt"] = [
    InsertBefore("import androidx.compose.foundation.gestures.detectTapGestures",
        "import androidx.compose.foundation.gestures.detectHorizontalDragGestures",
        marker="detectHorizontalDragGestures"),
    Replace("Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { onClick() },",
            "Modifier.size(40.dp).clip(CircleShape).clickable { onClick() },",
            marker="Modifier.size(40.dp).clip(CircleShape).clickable { onClick() },"),
    Replace("Modifier.size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).clickable { onClick() },",
            "Modifier.size(46.dp).clip(CircleShape).clickable { onClick() },",
            marker="Modifier.size(46.dp).clip(CircleShape).clickable { onClick() },"),
    Replace("Modifier.align(Alignment.CenterEnd).padding(end = 10.dp, top = 90.dp),",
            "Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 24.dp),",
            marker="Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 24.dp),"),
    Replace('''Text("Mostrar interface", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
}
}
}''',
            '''Text("Mostrar interface", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
}
}
ShortsProgressBar(player = player, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
}''',
            marker="ShortsProgressBar(player = player"),
    # Data: aceita a versao sem acento (fix_shorts) e a acentuada
    ReplaceAny([
        'DescStatRow("Data em que foi lancado:", state.info?.publishedText ?: currentTrack.publishedText ?: "-")',
        'DescStatRow("Data em que foi lançado:", state.info?.publishedText ?: currentTrack.publishedText ?: "-")',
        'DescStatRow("Data em que foi lançado:", state.info?.publishedText ?: currentTrack.publishedText ?: "—")',
        'DescStatRow("Data em que foi lancado:", state.info?.publishedText ?: currentTrack.publishedText ?: "—")',
    ],
        'DescStatRow("Data:", state.info?.publishDateText ?: state.info?.publishedText ?: currentTrack.publishedText ?: "-")',
        marker='DescStatRow("Data:",'),
    Replace('DescStatRow("Marcacoes gostei:", formatPlainCount(state.info?.likeCount))',
            'DescStatRow("Marcações gostei:", formatPlainCount(state.info?.likeCount))',
            marker="Marcações gostei:", optional=True),
    Replace('DescStatRow("Visualizacoes:", formatPlainCount(state.info?.viewCount))',
            'DescStatRow("Visualizações:", formatPlainCount(state.info?.viewCount))',
            marker="Visualizações:", optional=True),
    InsertBefore('''@Composable
private fun ShortsCircleButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {''',
        _PROGRESS_BAR, marker="private fun ShortsProgressBar("),
]

# ================= Parsers: views + data nos lockups =================
_VIEWS_HELPERS = r'''    /** Views + data dos metadataRows do lockup ("1,2 mi de visualizações • há 2 dias"). */
    fun lockupViewsDate(lvm: JSONObject): Pair<Long?, String?> {
        val rows = lvm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
            ?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")
            ?.optJSONArray("metadataRows") ?: return null to null
        var views: Long? = null
        var date: String? = null
        for (i in 0 until rows.length()) {
            val parts = rows.optJSONObject(i)?.optJSONArray("metadataParts") ?: continue
            for (j in 0 until parts.length()) {
                val txt = parts.optJSONObject(j)?.optJSONObject("text")?.optString("content")?.takeIf { it.isNotBlank() } ?: continue
                for (seg in txt.split("•", "·")) {
                    val s = seg.trim()
                    val low = s.lowercase()
                    if (views == null && (low.contains("visualiza") || low.contains("view"))) views = parseViewCount(s)
                    if (date == null && (low.contains("há ") || low.contains("ago") || low.contains("atrás"))) date = s
                }
            }
        }
        return views to date
    }

    /** "1,2 mi de visualizações" -> 1200000; "534 mil" -> 534000; "1.234" -> 1234. */
    fun parseViewCount(text: String): Long? {
        val m = Regex("([\\d.,]+)\\s*(milh(?:ão|ões|oes)|mil|mi|bi|k|m|b)?", RegexOption.IGNORE_CASE).find(text) ?: return null
        val numRaw = m.groupValues[1]
        val suffix = m.groupValues[2].lowercase()
        val num = (if (numRaw.contains(",")) numRaw.replace(".", "").replace(",", ".").toDoubleOrNull() else numRaw.replace(".", "").toDoubleOrNull()) ?: return null
        val mult = when {
            suffix == "mil" || suffix == "k" -> 1_000.0
            suffix.startsWith("milh") || suffix == "mi" || suffix == "m" -> 1_000_000.0
            suffix == "bi" || suffix == "b" -> 1_000_000_000.0
            else -> 1.0
        }
        return (num * mult).toLong().takeIf { it > 0 }
    }
'''
# Prefixo de linha (funciona com OU sem channelAvatarUrl no final)
_CH_VR_PREFIX = 'out += Track(id = videoId, title = title, artist = channel, durationSeconds = duration, thumbnailUrl = thumb, source = Source.YOUTUBE, publishedText = published'
_SP_VR_PREFIX = 'Track(id = videoId, title = title, artist = channel, durationSeconds = duration, thumbnailUrl = thumb, source = Source.YOUTUBE, publishedText = published'
PATCHES[f"{DATA}/youtube/YouTubeChannelParser.kt"] = [
    InsertBefore("    private fun collect(node: Any?, key: String, out: MutableList<JSONObject>) {", _VIEWS_HELPERS, marker="fun lockupViewsDate("),
    Replace('out += Track(id = videoId, title = title, artist = "", durationSeconds = duration, thumbnailUrl = thumb, source = Source.YOUTUBE, publishedText = published)',
            'val (lv, ld) = lockupViewsDate(lvm)\nout += Track(id = videoId, title = title, artist = "", durationSeconds = duration, thumbnailUrl = thumb, source = Source.YOUTUBE, publishedText = published ?: ld, viewCount = lv)',
            marker="publishedText = published ?: ld, viewCount = lv"),
    Replace(_CH_VR_PREFIX,
            _CH_VR_PREFIX + ', viewCount = YouTubeSearchParser.textOf(r.optJSONObject("viewCountText"))?.let { parseViewCount(it) }',
            marker='viewCount = YouTubeSearchParser.textOf(r.optJSONObject("viewCountText"))'),
]
PATCHES[f"{DATA}/youtube/YouTubePlaylistParser.kt"] = [
    Replace('''walk(lvm.optJSONObject("contentImage"))
out += Track(id = videoId, title = title, artist = channel, durationSeconds = duration, thumbnailUrl = thumb, source = Source.YOUTUBE)''',
            '''walk(lvm.optJSONObject("contentImage"))
val (lv, ld) = YouTubeChannelParser.lockupViewsDate(lvm)
out += Track(id = videoId, title = title, artist = channel, durationSeconds = duration, thumbnailUrl = thumb, source = Source.YOUTUBE, viewCount = lv, publishedText = ld)''',
            marker="YouTubeChannelParser.lockupViewsDate(lvm)"),
]
PATCHES[f"{DATA}/youtube/YouTubeSearchParser.kt"] = [
    Replace(_SP_VR_PREFIX,
            _SP_VR_PREFIX + ', viewCount = textOf(r.optJSONObject("viewCountText"))?.let { YouTubeChannelParser.parseViewCount(it) }',
            marker='viewCount = textOf(r.optJSONObject("viewCountText"))'),
]

# ================= ChannelCards: card menor com nome + play + views + data =================
PATCHES[f"{APP}/ui/components/cards/ChannelCards.kt"] = [
    InsertBefore("import com.vztechcore.vztune.ui.icons.media.Lists",
        "import com.vztechcore.vztune.ui.icons.player.PlayArrow",
        marker="import com.vztechcore.vztune.ui.icons.player.PlayArrow"),
    Replace('''val meta = videoViewsDate(track)
if (!meta.isNullOrBlank()) Text(text = meta, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis)''',
            '''val viewsDate = videoViewsDate(track)
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
}''',
            marker="val viewsDate = videoViewsDate(track)"),
]

# ================= Dependencia HLS (fix do crash) =================
PATCHES["gradle/libs.versions.toml"] = [
    InsertBefore("# ---- DataStore (preferences storage) ----",
        'androidx-media3-hls = { group = "androidx.media3", name = "media3-exoplayer-hls", version.ref = "media3" }',
        marker='name = "media3-exoplayer-hls"'),
]
PATCHES["app/build.gradle.kts"] = [
    InsertBefore("implementation(libs.androidx.media3.exoplayer)",
        "implementation(libs.androidx.media3.hls)",
        marker="implementation(libs.androidx.media3.hls)"),
]

def main():
    dry = "--dry" in sys.argv
    results, errors, staged = [], [], {}
    for rel, ops in PATCHES.items():
        path = os.path.join(ROOT, rel)
        if not os.path.exists(path):
            errors.append("[%s] arquivo nao existe" % rel); continue
        with open(path, encoding="utf-8") as f: s = f.read()
        for n, op in enumerate(ops, 1):
            try:
                s, st = op.apply(s)
                results.append("  %-12s %s #%d" % (st, rel.split("/")[-1], n))
            except PatchError as e:
                errors.append("[%s] op #%d: %s" % (rel, n, e))
        staged[rel] = s
    print("\n".join(results))
    if errors:
        print("\nERROS (nada foi gravado):")
        print("\n".join("  - " + e for e in errors))
        sys.exit(1)
    if dry:
        print("\nDRY-RUN ok. Rode sem --dry para aplicar.")
        return
    changed = 0
    for rel, s in staged.items():
        path = os.path.join(ROOT, rel)
        with open(path, encoding="utf-8") as f: old = f.read()
        if old != s:
            with open(path, "w", encoding="utf-8", newline="\n") as f: f.write(s)
            print("alterado:", rel); changed += 1
    for rel, content in NEW_FILES.items():
        path = os.path.join(ROOT, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                if f.read() == content: continue
        with open(path, "w", encoding="utf-8", newline="\n") as f: f.write(content)
        print("criado/atualizado:", rel); changed += 1
    print("\nPronto (%d arquivos). Compile o projeto." % changed)

if __name__ == "__main__":
    main()