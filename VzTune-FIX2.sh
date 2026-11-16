#!/bin/bash
# VzTune FIX4 - equalizador album = cards normais + artista certo (SimpMusic Levenshtein + verifica catalogo)
# Roda dentro da pasta do projeto: cd ~/VzTune && bash VzTune-FIX4.sh
# IMPORTANTE: rode FIX3 antes, FIX4 so muda esses 8 arquivos em cima do FIX3
set -e
echo "== VzTune FIX4 - 8 arquivos =="

echo "[1/8] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListTrackRow.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListTrackRow.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListTrackRow.kt" <<'VZ_EOF_1_FIX4'
package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.player.PlayingEqualizer
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import com.vztechcore.vztune.ui.components.player.playbackOf
import com.vztechcore.vztune.ui.components.text.TrackSubtitle
import com.vztechcore.vztune.ui.components.text.VzChipRow
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.DragHandle
import com.vztechcore.vztune.ui.icons.actions.MoreVert
import com.vztechcore.vztune.ui.icons.media.QueueMusic

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
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Box(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        if (dragOffset > 0f) {
            val iconAlpha = (dragOffset / 100f).coerceIn(0.4f, 1f)
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(dragOffset.dp.coerceAtMost(80.dp))
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    imageVector = VzIcons.QueueMusic,
                    contentDescription = "Tocar em seguida",
                    tint = Color.White.copy(alpha = iconAlpha),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = dragOffset }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragOffset > 30f) onPlayNext()
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f },
                    ) { change, dragAmount ->
                        if (dragAmount > 0 || dragOffset > 0) {
                            dragOffset = (dragOffset + dragAmount).coerceIn(0f, 300f)
                            change.consume()
                        }
                    }
                },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Álbum: sem fundo cinza, só o número grande onde ficaria a thumbnail.
                // Tocando: equalizador subindo e descendo, sem fundo. Pausado: só •••.
                // Playlist: mostra a thumbnail normal.
                if (index != null) {
                    Box(
                        Modifier.size(52.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            // EXATAMENTE igual ao dos cards normais (MediaThumbnail.PlayingOverlayLarge):
                            // mesmos 3 barras, 20dp, 3dp, 3dp, mesma animação. Não é parecido, é o mesmo call.
                            isPlaying -> PlayingEqualizer(
                                color = Color.White,
                                barCount = 3,
                                maxHeight = 20.dp,
                                barWidth = 3.dp,
                                gap = 3.dp,
                            )
                            isPaused -> Text(
                                text = "•••",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            else -> Text(
                                text = "$index",
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                } else {
                    MediaThumbnail(
                        imageUrl = track.thumbnailUrl,
                        shape = ThumbnailShape.SQUARE,
                        size = 52.dp,
                        cornerRadius = 6.dp,
                        isPlaying = isPlaying,
                        isPaused = isPaused,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    TrackSubtitle(
                        track = track,
                        style = MaterialTheme.typography.bodySmall,
                        prefixColor = Color.White.copy(alpha = 0.55f),
                        artistColor = Color.White.copy(alpha = 0.6f),
                    )
                }
                IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = VzIcons.MoreVert,
                        contentDescription = "Mais",
                        tint = Color.White.copy(alpha = 0.7f),
                    )
                }
                if (showDragHandle) {
                    Icon(
                        imageVector = VzIcons.DragHandle,
                        contentDescription = "Arrastar",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(32.dp)
                            .padding(4.dp)
                            .then(dragHandleModifier),
                    )
                }
            }
        }
    }
}

/**
 * 16:9 video card with title, channel and the "more" button.
 * [horizontalPadding] is the card's inner side inset; pass 0.dp when the parent already
 * provides the edge padding (e.g. horizontal rows on the artist screen).
 */
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
    Column(
        modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = horizontalPadding, vertical = 8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            MediaThumbnail(
                imageUrl = track.thumbnailUrl,
                shape = ThumbnailShape.WIDE,
                cornerRadius = 10.dp,
                isPlaying = isPlaying,
                isPaused = isPaused,
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
                Spacer(Modifier.height(2.dp))
                TrackSubtitle(
                    track = track,
                    style = MaterialTheme.typography.bodySmall,
                    prefixColor = Color.White.copy(alpha = 0.55f),
                    artistColor = Color.White.copy(alpha = 0.6f),
                )
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = VzIcons.MoreVert,
                    contentDescription = "Mais",
                    tint = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
internal fun MediaListTabs(selected: MediaListTab, onSelect: (MediaListTab) -> Unit) {
    VzChipRow(
        items = MediaListTab.entries,
        selected = selected,
        label = { it.label },
        onSelect = onSelect,
    )
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
    MediaListTrackRow(
        track = track,
        index = index,
        isPlaying = playback.isPlaying,
        isPaused = playback.isPaused,
        onClick = onClick,
        onMoreClick = onMoreClick,
        onPlayNext = onPlayNext,
        showDragHandle = false,
        dragHandleModifier = Modifier,
        modifier = modifier,
    )
}

@Composable
internal fun PlayableVideoCard(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = LocalNowPlaying.current.playbackOf(track)
    MediaListVideoCard(
        track = track,
        isPlaying = playback.isPlaying,
        isPaused = playback.isPaused,
        onClick = onClick,
        onMoreClick = onMoreClick,
        modifier = modifier,
    )
}
VZ_EOF_1_FIX4

echo "[2/8] data/src/main/kotlin/com/vztechcore/vztune/data/ytmusic/ArtistResolver.kt"
mkdir -p "$(dirname "data/src/main/kotlin/com/vztechcore/vztune/data/ytmusic/ArtistResolver.kt")"
cat > "data/src/main/kotlin/com/vztechcore/vztune/data/ytmusic/ArtistResolver.kt" <<'VZ_EOF_2_FIX4'
package com.vztechcore.vztune.data.ytmusic

import java.text.Normalizer

/**
 * Resolve o artista certo quando só temos o NOME (clique em artista dentro de álbum/faixa).
 *
 * Bug antigo: searchArtists(nome).firstOrNull() pegava o primeiro da busca.
 * Com nomes repetidos (ex: "MC Ryan", "Henrique e Juliano", "Matheus") o primeiro
 * quase sempre era o artista errado ou o canal "- Topic".
 *
 * Sistema do SimpMusic (StringMatcher.kt no TXT):
 * - Levenshtein pra pegar o nome MAIS PARECIDO, não o primeiro.
 * - Navegação usa channelId (UC...) direto quando já tem, nunca re-busca por nome.
 *
 * Aqui + a verificação que você pediu:
 * - 1. Rankeia candidatos por Levenshtein no nome normalizado (sem acento, sem " - Topic").
 * - 2. Desempata Topic: mesmo nome, o oficial (sem " - Topic") ganha.
 * - 3. Verifica catálogo: abre a página dos 3 mais parecidos e só aceita quem tem
 *    aquela faixa / aquele álbum no catálogo. Quem não tem, não é ele.
 * - 4. Sem contexto de faixa/álbum (ex: clique no dono da playlist), cai pro mais parecido.
 */
internal object ArtistResolver {

    /** Normaliza pra comparar: minúsculo, sem acento, sem " - Topic", sem pontuação, espaços únicos. */
    fun normalize(raw: String): String {
        var s = raw.trim().lowercase()
        s = s.removeSuffix(" - topic").removeSuffix("- topic").trim()
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        s = s.replace(Regex("[^a-z0-9 ]"), " ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    fun isTopicName(raw: String): Boolean =
        raw.contains(" - topic", ignoreCase = true) || raw.trim().endsWith("topic", ignoreCase = true)

    // Levenshtein VERBATIM do SimpMusic (extension/StringMatcher.kt). Não mudei nada.
    fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1) { 0 }
        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val editCost = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + editCost
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength]
    }

    fun distance(query: String, candidateName: String): Int =
        levenshtein(normalize(query), normalize(candidateName))

    /** Título de faixa/álbum bate? Igual normalizado, ou quase igual, ou um contém o outro. */
    fun titlesMatch(catalogTitle: String, targetTitle: String): Boolean {
        val a = normalize(catalogTitle)
        val b = normalize(targetTitle)
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        // "Nome (feat. X)" / "Nome (Deluxe)" no catálogo vs "Nome" limpo no clique
        if (a.contains(b) || b.contains(a)) return true
        // Erro de digitação / acento que sobrou: distância pequena
        val maxDist = maxOf(2, b.length / 5)
        return levenshtein(a, b) <= maxDist
    }

    /**
     * O candidato tem essa faixa / esse álbum no catálogo?
     * topSongs + videos provam a faixa, albums + singles provam o álbum.
     */
    fun catalogContains(
        topSongTitles: List<String>,
        videoTitles: List<String>,
        albumTitles: List<String>,
        singleTitles: List<String>,
        trackTitle: String?,
        albumTitle: String?,
    ): Boolean {
        if (!trackTitle.isNullOrBlank()) {
            val hit = (topSongTitles + videoTitles).any { titlesMatch(it, trackTitle) }
            if (hit) return true
        }
        if (!albumTitle.isNullOrBlank()) {
            val hit = (albumTitles + singleTitles).any { titlesMatch(it, albumTitle) }
            if (hit) return true
        }
        return false
    }
}
VZ_EOF_2_FIX4

echo "[3/8] data/src/main/kotlin/com/vztechcore/vztune/data/ytmusic/YTMusicRepository.kt"
mkdir -p "$(dirname "data/src/main/kotlin/com/vztechcore/vztune/data/ytmusic/YTMusicRepository.kt")"
cat > "data/src/main/kotlin/com/vztechcore/vztune/data/ytmusic/YTMusicRepository.kt" <<'VZ_EOF_3_FIX4'
package com.vztechcore.vztune.data.ytmusic

import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.ArtistListKind
import com.vztechcore.vztune.core.domain.music.BrowseRef
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.innertube.InnerTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YTMusicRepository @Inject constructor(
    private val enricher: YTMusicEnricher,
) {

    data class SearchPage(val items: List<SearchHit>, val continuation: String?)

    data class AlbumResult(
        val coverUrl: String?,
        val artist: String?,
        val title: String?,
        val description: String?,
        val views: String?,
        val tracks: List<Track>,
        val artistId: String? = null,
        val year: String? = null,
    )

    data class PlaylistResult(
        val coverUrl: String?,
        val owner: String?,
        val ownerAvatarUrl: String?,
        val title: String?,
        val description: String?,
        val trackCount: Int,
        val year: String?,
        val views: String?,
        val tracks: List<Track>,
    )

    data class ArtistResult(
        val browseId: String,
        val name: String,
        val thumbnailUrl: String?,
        val subscribers: String?,
        val description: String?,
        val topSongs: List<Track>,
        val albums: List<Album>,
        val singles: List<Album>,
        val relatedArtists: List<Artist>,
        val views: String? = null,
        val songsMore: BrowseRef? = null,
        val albumsMore: BrowseRef? = null,
        val singlesMore: BrowseRef? = null,
        val videos: List<Track> = emptyList(),
        val videosMore: BrowseRef? = null,
        val featured: List<Playlist> = emptyList(),
        val featuredMore: BrowseRef? = null,
    )

    /** One page of an artist "see all" list. Only the list matching the requested kind is filled. */
    data class ArtistListPage(
        val tracks: List<Track> = emptyList(),
        val albums: List<Album> = emptyList(),
        val playlists: List<Playlist> = emptyList(),
        val continuation: String? = null,
    )

    data class VideoDetails(
        val viewCount: String?,
        val likeCount: String?,
        val description: String?,
    )

    private data class ResolvedAlbumRef(
        val browseId: String,
        val title: String,
    )

    private data class PlaylistHeaderExtras(
        val ownerAvatarUrl: String?,
        val year: String?,
        val channelId: String?,
    )

    // ================= SEARCH =================

    suspend fun search(
        query: String,
        filter: SearchFilter,
        continuation: String?,
    ): SearchPage = withContext(Dispatchers.IO) {
        val json = runCatching {
            if (continuation == null) InnerTube.search(query, filter.params)
            else InnerTube.searchContinuation(continuation)
        }.getOrNull() ?: return@withContext SearchPage(emptyList(), null)

        val parsed = YTMusicSearchParser.parseSearchResults(json)
        val next = InnerTube.findContinuationToken(json)
        val hits = parsed.mapNotNull { YTMusicSearchParser.toHit(it, filter) }
        val enriched = enricher.enrichHits(hits)
        SearchPage(enriched, next)
    }

    /**
     * Resolves the official YouTube Music [Album] (or Single/EP) that contains [track]
     * using the song's exact `MPREb_` browseId from InnerTube instead of guessing.
     */
    suspend fun findAlbumForTrack(track: Track): Album? = withContext(Dispatchers.IO) {
        val primaryArtist = track.artist
            .substringBefore(",")
            .substringBefore("&")
            .substringBefore("feat.")
            .substringBefore("ft.")
            .trim()
            .takeIf { it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true) }

        val albumHint = track.album?.takeIf {
            it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true)
        }

        // 1. Search the exact song in YT Music to read its official MPREb_ album reference
        val songQuery = listOfNotNull(track.title, primaryArtist).joinToString(" ").trim()
        val exactRef = if (songQuery.isNotBlank()) {
            runCatching {
                val songJson = InnerTube.search(songQuery, SearchFilter.SONGS.params)
                extractAlbumRefFromSongSearch(songJson, track)
            }.getOrNull()
        } else {
            null
        }

        if (exactRef != null) {
            val header = runCatching { YTMusicAlbumParser.parseAlbumHeader(exactRef.browseId) }.getOrNull()
            return@withContext Album(
                id = exactRef.browseId,
                title = header?.title?.takeIf { it.isNotBlank() } ?: exactRef.title,
                artist = header?.artist?.takeIf { it.isNotBlank() } ?: primaryArtist ?: track.artist,
                thumbnailUrl = header?.coverUrl ?: track.thumbnailUrl,
                views = header?.views,
                source = Source.YOUTUBE_MUSIC,
            )
        }

        // 2. Fallback only when track already carried an explicit album name
        val targetTitle = albumHint ?: return@withContext null
        val albumSearchQuery = listOfNotNull(targetTitle, primaryArtist).joinToString(" ").trim()
        if (albumSearchQuery.isBlank()) return@withContext null

        val json = runCatching { InnerTube.search(albumSearchQuery, SearchFilter.ALBUMS.params) }.getOrNull()
            ?: return@withContext null

        val parsed = YTMusicSearchParser.parseSearchResults(json)
        val albums = parsed.mapNotNull { YTMusicSearchParser.toHit(it, SearchFilter.ALBUMS) }
            .filterIsInstance<SearchHit.AlbumHit>()
            .map { it.album }

        albums.firstOrNull { album ->
            val titleMatches = album.title.equals(targetTitle, ignoreCase = true) ||
                album.title.contains(targetTitle, ignoreCase = true)
            val artistMatches = primaryArtist == null ||
                album.artist.contains(primaryArtist, ignoreCase = true) ||
                primaryArtist.contains(album.artist, ignoreCase = true)
            titleMatches && artistMatches
        }
    }

    /**
     * Fills missing views / owner on playlist hits that did not come from YouTube Music
     * (e.g. NewPipe results). Cover and mosaic are left untouched.
     */
    suspend fun fillPlaylistStats(hits: List<SearchHit.PlaylistHit>): List<SearchHit.PlaylistHit> =
        enricher.fillPlaylistStats(hits)

    // ================= SUGGESTIONS =================

    suspend fun suggestions(query: String): SearchSuggestions =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext SearchSuggestions()

            coroutineScope {
                val queriesDeferred = async {
                    runCatching {
                        val json = InnerTube.searchSuggestions(query)
                        YTMusicSearchParser.parseSuggestions(json)
                    }.getOrDefault(emptyList())
                }

                val songsDeferred = async {
                    runCatching {
                        val limit = when {
                            query.length <= 2 -> 3
                            query.length == 3 -> 4
                            else -> 5
                        }

                        val json = InnerTube.search(query, SearchFilter.SONGS.params)
                        val parsed = YTMusicSearchParser.parseSearchResults(json)
                        parsed.mapNotNull { YTMusicSearchParser.toHit(it, SearchFilter.SONGS) }
                            .filterIsInstance<SearchHit.SongHit>()
                            .take(limit)
                            .map { it.track }
                    }.getOrDefault(emptyList())
                }

                SearchSuggestions(
                    songs = songsDeferred.await(),
                    queries = queriesDeferred.await()
                )
            }
        }

    // ================= BROWSE =================

    suspend fun browseAlbum(albumId: String): AlbumResult = withContext(Dispatchers.IO) {
        // Normalize OLAK5uy_ playlist-backed album IDs so InnerTube browse always resolves them
        val browseId = if (albumId.startsWith("OLAK5uy_")) "VL$albumId" else albumId

        val json = runCatching { InnerTube.browse(browseId) }.getOrNull()
            ?: return@withContext AlbumResult(null, null, null, null, null, emptyList())

        val headerInfo = YTMusicAlbumParser.parseFromJson(json)
        val cover = headerInfo?.coverUrl
        val title = headerInfo?.title
        val artist = headerInfo?.artist
        val views = headerInfo?.views
        val desc = extractDescription(json)
        val (artistId, year) = extractAlbumHeaderExtras(json)

        // Check both musicShelfRenderer and musicPlaylistShelfRenderer used by two-column album layouts
        val primaryShelf = YTMusicTextUtils.findFirst(json, "musicShelfRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicPlaylistShelfRenderer")

        var rawTracks = (primaryShelf?.let { collectMusicItems(it) } ?: emptyList())
            .ifEmpty { collectMusicItems(json) }
            .distinctBy { it.id }

        // Fallback: some MPREb_ pages only expose the album's OLAK5uy_ playlistId in the Play button
        if (rawTracks.isEmpty()) {
            val fallbackPlaylistId = findAlbumAudioPlaylistId(json)
            if (!fallbackPlaylistId.isNullOrBlank() && fallbackPlaylistId != browseId) {
                val playlistFallback = runCatching { browsePlaylist(fallbackPlaylistId) }.getOrNull()
                if (playlistFallback != null && playlistFallback.tracks.isNotEmpty()) {
                    rawTracks = playlistFallback.tracks
                }
            }
        }

        val normalizedTracks = rawTracks.map { item ->
            val resolvedArtist = if (item.artist.isBlank() || item.artist.equals("Desconhecido", ignoreCase = true)) {
                artist ?: item.artist
            } else {
                item.artist
            }
            val resolvedAlbum = item.album?.takeIf { it.isNotBlank() } ?: title
            item.copy(
                artist = resolvedArtist,
                album = resolvedAlbum,
                thumbnailUrl = item.thumbnailUrl ?: cover,
                source = Source.YOUTUBE_MUSIC,
            )
        }

        val finalTracks = enricher.enrichTracks(normalizedTracks, artist)

        AlbumResult(
            coverUrl = cover,
            artist = artist,
            title = title,
            description = desc,
            views = views,
            tracks = finalTracks,
            artistId = artistId,
            year = year,
        )
    }

    /**
     * Fetches the artist profile and other albums/singles by the same artist,
     * excluding the currently opened album.
     */
    suspend fun getMoreAlbumsByArtist(
        artistName: String?,
        artistId: String?,
        excludeAlbumId: String,
        excludeAlbumTitle: String?,
    ): Pair<Artist?, List<Album>> = withContext(Dispatchers.IO) {
        val resolvedId = artistId?.takeIf { it.startsWith("UC") }
            ?: artistName?.takeIf { it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true) }
                ?.let { name ->
                    val primary = name.substringBefore(",").substringBefore("&").trim()
                    // Verifica que o artista tem esse álbum no catálogo, senão pega o homônimo errado
                    runCatching {
                        resolveArtist(primary, albumTitle = excludeAlbumTitle)?.id
                    }.getOrNull()
                }
            ?: return@withContext (null to emptyList())

        val artistDetail = runCatching { browseArtist(resolvedId) }.getOrNull()
            ?: return@withContext (null to emptyList())

        val artist = Artist(
            id = artistDetail.browseId,
            name = artistDetail.name,
            thumbnailUrl = artistDetail.thumbnailUrl,
            source = Source.YOUTUBE_MUSIC,
        )

        val cleanExcludeId = excludeAlbumId.removePrefix("VL")
        val otherReleases = (artistDetail.albums + artistDetail.singles)
            .distinctBy { it.id }
            .filter { candidate ->
                val sameId = candidate.id == excludeAlbumId || candidate.id.removePrefix("VL") == cleanExcludeId
                val sameTitle = !excludeAlbumTitle.isNullOrBlank() &&
                    candidate.title.equals(excludeAlbumTitle, ignoreCase = true)
                !sameId && !sameTitle
            }
            .take(15)

        artist to otherReleases
    }

    suspend fun browsePlaylist(playlistId: String): PlaylistResult = withContext(Dispatchers.IO) {
        val cleanId = YTMusicTextUtils.normalizePlaylistBrowseId(playlistId)
        val json = runCatching { InnerTube.browse(cleanId) }.getOrNull()
            ?: return@withContext PlaylistResult(null, null, null, null, null, 0, null, null, emptyList())

        val parsed = YTMusicPlaylistParser.parsePlaylist(json)
        val headerExtras = extractPlaylistHeaderExtras(json)
        val finalTracks = enricher.enrichTracks(parsed.tracks, null)

        val resolvedAvatar = parsed.ownerAvatarUrl?.takeIf { it.isNotBlank() }
            ?: headerExtras.ownerAvatarUrl
            ?: headerExtras.channelId?.let { cid ->
                runCatching { browseArtist(cid).thumbnailUrl }.getOrNull()
            }

        val resolvedYear = parsed.year?.takeIf { it.isNotBlank() } ?: headerExtras.year

        PlaylistResult(
            coverUrl = parsed.coverUrl,
            owner = parsed.owner,
            ownerAvatarUrl = resolvedAvatar,
            title = parsed.title,
            description = parsed.description,
            trackCount = parsed.trackCount.takeIf { it > 0 } ?: finalTracks.size,
            year = resolvedYear,
            views = parsed.views,
            tracks = finalTracks
        )
    }

    // ================= ARTISTS =================

    suspend fun searchArtists(query: String): List<Artist> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Artist>()
        runCatching {
            val json = InnerTube.search(query, SearchFilter.ARTISTS.params)
            YTMusicArtistParser.extractArtistsFromSearch(json, out)
        }
        out.distinctBy { it.id }.filter { !it.thumbnailUrl.isNullOrBlank() }.take(30)
    }

    /**
     * Resolves an artist or YouTube channel profile (including avatar thumbnail) by name.
     * Sem contexto de faixa/álbum: usa só o nome mais parecido (Levenshtein, SimpMusic).
     * Com contexto, usa resolveArtist() com verificação de catálogo.
     */
    suspend fun findChannelOrArtist(name: String): Artist? =
        resolveArtist(name, trackTitle = null, albumTitle = null)

    /**
     * Resolve o artista CERTO pra um nome clicado dentro de álbum/faixa.
     *
     * Antigo: searchArtists(nome).firstOrNull() -> pegava o primeiro, que com nome
     * repetido era quase sempre o artista errado ou o "- Topic".
     *
     * Novo (SimpMusic + verificação que você pediu):
     * 1. Busca candidatos (até 30), rankeia por Levenshtein no nome normalizado,
     *    desempata Topic (oficial sem " - Topic" ganha).
     * 2. Se tem nome exato não-Topic, devolve ele direto sem gastar browse.
     * 3. Se tem faixa/álbum de contexto, abre a página dos 3 mais parecidos e só
     *    aceita quem tem aquela faixa / aquele álbum no catálogo. Quem não tem, não é ele.
     * 4. Sem verificação ou ninguém verificado, cai pro mais parecido.
     */
    suspend fun resolveArtist(
        artistName: String,
        trackTitle: String? = null,
        albumTitle: String? = null,
    ): Artist? = withContext(Dispatchers.IO) {
        val cleanQuery = artistName.trim()
        if (cleanQuery.isBlank() || cleanQuery.equals("YouTube Music", ignoreCase = true)) {
            return@withContext null
        }

        // 1. Candidatos: filtro ARTISTS primeiro, igual antes
        var candidates = searchArtists(cleanQuery)

        // 2. Fallback pra busca geral (canal/curador UC...) quando ARTISTS não achou nada
        if (candidates.isEmpty()) {
            val fallbackOut = mutableListOf<Artist>()
            runCatching {
                val json = InnerTube.search(cleanQuery, null)
                YTMusicArtistParser.extractArtistsFromSearch(json, fallbackOut)
            }
            candidates = fallbackOut.distinctBy { it.id }
                .filter { !it.thumbnailUrl.isNullOrBlank() }
        }
        if (candidates.isEmpty()) return@withContext null

        // Rankeia: menor Levenshtein primeiro, não-Topic antes de Topic, ordem original desempata
        val ranked = candidates.mapIndexed { idx, artist ->
            Triple(artist, ArtistResolver.distance(cleanQuery, artist.name), idx)
        }.sortedWith(
            compareBy<Triple<Artist, Int, Int>> { it.second }
                .thenBy { if (ArtistResolver.isTopicName(it.first.name)) 1 else 0 }
                .thenBy { it.third }
        )

        // Exato não-Topic: é ele, sem gastar browse
        val exactNonTopic = ranked.firstOrNull {
            it.second == 0 && !ArtistResolver.isTopicName(it.first.name)
        }
        if (exactNonTopic != null && trackTitle.isNullOrBlank() && albumTitle.isNullOrBlank()) {
            return@withContext exactNonTopic.first
        }
        // Exato (mesmo que seja Topic) sem contexto: também é ele, sem gastar browse
        val exactAny = ranked.firstOrNull { it.second == 0 }
        if (exactAny != null && trackTitle.isNullOrBlank() && albumTitle.isNullOrBlank()) {
            return@withContext exactAny.first
        }

        // 3. Verificação de catálogo: só pros 3 mais parecidos, pra não estourar rede
        val hasContext = !trackTitle.isNullOrBlank() || !albumTitle.isNullOrBlank()
        if (hasContext) {
            for ((candidate, _, _) in ranked.take(3)) {
                val detail = runCatching { browseArtist(candidate.id) }.getOrNull() ?: continue
                val contains = ArtistResolver.catalogContains(
                    topSongTitles = detail.topSongs.map { it.title },
                    videoTitles = detail.videos.map { it.title },
                    albumTitles = detail.albums.map { it.title },
                    singleTitles = detail.singles.map { it.title },
                    trackTitle = trackTitle,
                    albumTitle = albumTitle,
                )
                if (contains) {
                    // Devolve com o nome/foto da página verificada (mais confiável que o da busca)
                    return@withContext Artist(
                        id = candidate.id,
                        name = detail.name.takeUnless { it == "Artista" || it == "Unknown Artist" }
                            ?: candidate.name,
                        thumbnailUrl = detail.thumbnailUrl ?: candidate.thumbnailUrl,
                        source = candidate.source,
                    )
                }
            }
            // Ninguém verificado: cai pro mais parecido abaixo (não retorna null, senão o clique morre)
        }

        // 4. Mais parecido (SimpMusic: bestMatchingIndex por Levenshtein)
        return@withContext ranked.first().first
    }

    suspend fun featuredArtists(): List<Artist> = withContext(Dispatchers.IO) {
        val seeds = listOf(
            "top hits", "pop", "funk brasileiro", "sertanejo",
            "rock", "trap", "mpb", "lofi",
        ).shuffled().take(3)

        val out = mutableListOf<Artist>()
        for (seed in seeds) {
            runCatching {
                val json = InnerTube.search(seed, SearchFilter.ARTISTS.params)
                YTMusicArtistParser.extractArtistsFromSearch(json, out)
            }
        }
        out.distinctBy { it.id }.filter { !it.thumbnailUrl.isNullOrBlank() }.shuffled().take(30)
    }

    suspend fun browseArtist(browseId: String): ArtistResult =
        withContext(Dispatchers.IO) {
            val json = runCatching { InnerTube.browse(browseId) }.getOrNull()
                ?: return@withContext ArtistResult(
                    browseId = browseId, name = "Artista",
                    thumbnailUrl = null, subscribers = null, description = null,
                    topSongs = emptyList(), albums = emptyList(), singles = emptyList(),
                    relatedArtists = emptyList()
                )

            val parsed = YTMusicArtistParser.parseArtistPage(browseId, json)

            ArtistResult(
                browseId = parsed.browseId,
                name = parsed.name,
                thumbnailUrl = parsed.thumbnailUrl,
                subscribers = parsed.subscribers,
                description = parsed.description,
                topSongs = parsed.topSongs,
                albums = parsed.albums,
                singles = parsed.singles,
                relatedArtists = parsed.relatedArtists,
                views = parsed.views,
                songsMore = parsed.songsMore,
                albumsMore = parsed.albumsMore,
                singlesMore = parsed.singlesMore,
                videos = parsed.videos,
                videosMore = parsed.videosMore,
                featured = parsed.featured,
                featuredMore = parsed.featuredMore,
            )
        }

    /**
     * Loads one page of an artist "see all" list (songs, videos, albums, singles or featured playlists).
     * Pass [continuation] = null for the first page, then the token returned in the previous page.
     */
    suspend fun browseArtistList(
        kind: ArtistListKind,
        artistName: String,
        ref: BrowseRef,
        continuation: String?,
    ): ArtistListPage = withContext(Dispatchers.IO) {
        val json = runCatching {
            if (continuation == null) InnerTube.browse(ref.browseId, ref.params)
            else InnerTube.browseContinuation(continuation)
        }.getOrNull() ?: return@withContext ArtistListPage()

        val page = YTMusicArtistParser.parseListPage(kind, artistName, json)
        ArtistListPage(page.tracks, page.albums, page.playlists, page.continuation)
    }

    suspend fun getArtistFromVideo(videoId: String): Artist? =
        withContext(Dispatchers.IO) {
            val playerJson = runCatching { InnerTube.player(videoId) }.getOrNull()
                ?: return@withContext null
            val videoDetails = playerJson.optJSONObject("videoDetails")
                ?: return@withContext null
            val channelId = videoDetails.optString("channelId")
                .takeIf { it.isNotBlank() && it != "null" }
            val author = videoDetails.optString("author")
                .takeIf { it.isNotBlank() && it != "null" }
            if (channelId == null || author == null) return@withContext null
            val cleanName = YTMusicTextUtils.cleanArtistName(author)
            if (cleanName.isBlank()) return@withContext null
            val detail = runCatching { browseArtist(channelId) }.getOrNull()
            Artist(
                id = channelId, name = cleanName,
                thumbnailUrl = detail?.thumbnailUrl,
                source = Source.YOUTUBE_MUSIC,
            )
        }

    // ================= QUEUE =================

    suspend fun buildSongQueue(seed: Track): List<Track> = withContext(Dispatchers.IO) {
        val query = buildString {
            if (seed.artist.isNotBlank()) append(seed.artist)
            if (seed.title.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(seed.title)
            }
        }.trim()

        if (query.isBlank()) return@withContext listOf(seed)

        val json = runCatching {
            InnerTube.search(query, SearchFilter.SONGS.params)
        }.getOrNull() ?: return@withContext listOf(seed)

        val found = collectMusicItems(json)
            .distinctBy { it.id }
            .filter { it.source == Source.YOUTUBE_MUSIC }

        val normalized = found.map { item ->
            val uploader = item.artist.takeIf {
                it.isNotBlank() && !it.equals("Unknown", true)
            } ?: seed.artist
            item.copy(artist = uploader)
        }

        val list = if (normalized.any { it.id == seed.id }) {
            listOf(seed) + normalized.filter { it.id != seed.id }
        } else {
            listOf(seed) + normalized
        }
        list.take(50)
    }

    // ================= VIDEO DETAILS =================

    suspend fun getVideoDetails(videoId: String): VideoDetails? =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = InnerTube.player(videoId)
                val videoDetails = json.optJSONObject("videoDetails")
                    ?: return@runCatching null

                val viewCountRaw = videoDetails.optString("viewCount")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.toLongOrNull()

                var description = videoDetails.optString("shortDescription")
                    .takeIf { it.isNotBlank() && it != "null" }

                if (description == null) {
                    description = json.optJSONObject("microformat")
                        ?.optJSONObject("playerMicroformatRenderer")
                        ?.optJSONObject("description")
                        ?.optString("simpleText")
                        ?.takeIf { it.isNotBlank() && it != "null" }
                }

                VideoDetails(
                    viewCount = viewCountRaw?.let { formatNumber(it) },
                    likeCount = null,
                    description = description,
                )
            }.getOrNull()
        }

    // ================= HELPERS =================

    private fun extractPlaylistHeaderExtras(json: JSONObject): PlaylistHeaderExtras {
        val header = YTMusicTextUtils.findFirst(json, "musicResponsiveHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicDetailHeaderRenderer")
            ?: return PlaylistHeaderExtras(null, null, null)

        val avatarUrl = YTMusicThumbnailUtils.thumbnailOf(header.optJSONObject("straplineThumbnail"))
            ?: YTMusicThumbnailUtils.thumbnailOf(
                header.optJSONObject("facepile")
                    ?.optJSONObject("avatarStackViewModel")
                    ?.optJSONArray("avatars")
                    ?.optJSONObject(0)
                    ?.optJSONObject("avatarViewModel")
                    ?.optJSONObject("image")
            )

        var year: String? = null
        var channelId: String? = null

        val runArrays = listOfNotNull(
            header.optJSONObject("straplineTextOne")?.optJSONArray("runs"),
            header.optJSONObject("subtitle")?.optJSONArray("runs"),
            header.optJSONObject("secondSubtitle")?.optJSONArray("runs"),
        )

        for (runs in runArrays) {
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val text = run.optString("text").trim()
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.isNotBlank() && it != "null" }

                if (channelId == null && browseId != null && browseId.startsWith("UC")) {
                    channelId = browseId
                }
                if (year == null && YTMusicTextUtils.isYear(text)) {
                    year = text
                }
            }
        }

        return PlaylistHeaderExtras(
            ownerAvatarUrl = avatarUrl,
            year = year,
            channelId = channelId,
        )
    }

    private fun extractAlbumHeaderExtras(json: JSONObject): Pair<String?, String?> {
        var artistId: String? = null
        var year: String? = null

        val header = YTMusicTextUtils.findFirst(json, "musicResponsiveHeaderRenderer")
            ?: YTMusicTextUtils.findFirst(json, "musicDetailHeaderRenderer")
            ?: return (null to null)

        val runArrays = listOfNotNull(
            header.optJSONObject("straplineTextOne")?.optJSONArray("runs"),
            header.optJSONObject("subtitle")?.optJSONArray("runs"),
            header.optJSONObject("secondSubtitle")?.optJSONArray("runs"),
        )

        for (runs in runArrays) {
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val text = run.optString("text").trim()
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.isNotBlank() && it != "null" }

                if (artistId == null && browseId != null && browseId.startsWith("UC")) {
                    artistId = browseId
                }
                if (year == null && YTMusicTextUtils.isYear(text)) {
                    year = text
                }
            }
        }
        return artistId to year
    }

    private fun findAlbumAudioPlaylistId(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                val pid = node.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")
                if (!pid.isNullOrBlank() && pid != "null" && pid.startsWith("OLAK5uy_")) {
                    return pid
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    findAlbumAudioPlaylistId(node.opt(keys.next()))?.let { return it }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    findAlbumAudioPlaylistId(node.opt(i))?.let { return it }
                }
            }
        }
        return null
    }

    private fun extractAlbumRefFromSongSearch(
        node: Any?,
        targetTrack: Track,
        candidates: MutableList<Pair<Boolean, ResolvedAlbumRef>> = mutableListOf(),
    ): ResolvedAlbumRef? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                    val columns = r.optJSONArray("flexColumns")
                    val rowTitle = columns?.let { YTMusicTextUtils.flexText(it, 0) }.orEmpty()
                    val videoId = extractVideoIdFromRenderer(r, columns)

                    val exactIdMatch = !videoId.isNullOrBlank() && videoId == targetTrack.id
                    val titleMatch = rowTitle.isNotBlank() &&
                        rowTitle.equals(targetTrack.title, ignoreCase = true)

                    if ((exactIdMatch || titleMatch) && columns != null) {
                        val albumRef = findAlbumRunInColumns(columns)
                        if (albumRef != null) {
                            candidates += (exactIdMatch to albumRef)
                        }
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    extractAlbumRefFromSongSearch(node.opt(keys.next()), targetTrack, candidates)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    extractAlbumRefFromSongSearch(node.opt(i), targetTrack, candidates)
                }
            }
        }
        return candidates.firstOrNull { it.first }?.second ?: candidates.firstOrNull()?.second
    }

    private fun findAlbumRunInColumns(columns: JSONArray): ResolvedAlbumRef? {
        for (c in 0 until columns.length()) {
            val runs = columns.optJSONObject(c)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs") ?: continue

            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val browseId = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.isNotBlank() && it != "null" } ?: continue

                if (browseId.startsWith("MPREb_")) {
                    val albumTitle = run.optString("text").trim()
                    if (albumTitle.isNotBlank()) {
                        return ResolvedAlbumRef(browseId = browseId, title = albumTitle)
                    }
                }
            }
        }
        return null
    }

    private fun extractVideoIdFromRenderer(r: JSONObject, columns: JSONArray?): String? {
        // 1. Standard playlist/search item data
        r.optJSONObject("playlistItemData")?.optString("videoId")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }

        // 2. Top-level navigationEndpoint
        r.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }

        // 3. First column title run navigationEndpoint (common on album tracklists)
        columns?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optJSONObject("navigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }

        // 4. Overlay play button endpoint (used on newer YTMusic album rows without playlistItemData)
        r.optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }

        return null
    }

    private fun extractDescription(json: JSONObject): String? {
        val shelf = YTMusicTextUtils.findFirst(json, "musicDescriptionShelfRenderer")
            ?: return null
        val descNode = shelf.optJSONObject("description") ?: return null
        val text = YTMusicTextUtils.extractRunsText(descNode)
        return text.takeIf { it.isNotBlank() }
    }

    private fun collectMusicItems(
        node: Any?,
        out: MutableList<Track> = mutableListOf(),
    ): List<Track> {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                    val columns = r.optJSONArray("flexColumns")
                    val title = columns?.let { YTMusicTextUtils.flexText(it, 0) }
                    val videoId = extractVideoIdFromRenderer(r, columns)

                    if (title != null && !videoId.isNullOrBlank()) {
                        val subtitleCol = columns.optJSONObject(1)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        val subtitle = YTMusicTextUtils.runsText(subtitleCol)
                        val source = YTMusicSearchParser.detectSource(videoId, r)
                        val uploader = YTMusicSearchParser.extractArtistFromSubtitle(subtitleCol, subtitle)
                            ?: subtitle.substringBefore("•").trim().ifBlank { "Desconhecido" }
                        val albumTitle = columns.let { findAlbumRunInColumns(it)?.title }
                        out += Track(
                            id = videoId,
                            title = title,
                            artist = uploader,
                            album = albumTitle,
                            thumbnailUrl = YTMusicThumbnailUtils
                                .thumbnailOf(r.optJSONObject("thumbnail")),
                            durationSeconds = YTMusicTextUtils
                                .parseDuration(YTMusicTextUtils.fixedText(r)),
                            source = source,
                            isExplicit = YTMusicSearchParser.hasExplicitBadge(r),
                        )
                    }
                }
                node.optJSONObject("musicTwoRowItemRenderer")?.let { r ->
                    val videoId = r.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("watchEndpoint")?.optString("videoId")
                    val title = r.optJSONObject("title")?.optJSONArray("runs")
                        ?.optJSONObject(0)?.optString("text")
                    if (!videoId.isNullOrBlank() && !title.isNullOrBlank()) {
                        val subtitle = YTMusicTextUtils.runsText(r.optJSONObject("subtitle"))
                        out += Track(
                            id = videoId, title = title,
                            artist = subtitle.substringBefore("•").trim()
                                .ifBlank { "Desconhecido" },
                            thumbnailUrl = YTMusicThumbnailUtils
                                .thumbnailOf(r.optJSONObject("thumbnailRenderer")),
                            durationSeconds = 0L,
                            source = Source.YOUTUBE,
                        )
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) collectMusicItems(node.opt(keys.next()), out)
            }
            is JSONArray -> for (i in 0 until node.length()) {
                collectMusicItems(node.opt(i), out)
            }
        }
        return out
    }

    private fun formatNumber(n: Long): String = when {
        n >= 1_000_000_000L -> String.format("%.1fB", n / 1_000_000_000.0)
        n >= 1_000_000L -> String.format("%.1fM", n / 1_000_000.0)
        n >= 1_000L -> String.format("%.1fK", n / 1_000.0)
        else -> n.toString()
    }
}
VZ_EOF_3_FIX4

echo "[4/8] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListViewModel.kt" <<'VZ_EOF_4_FIX4'
package com.vztechcore.vztune.ui.screens.medialist

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SortOrder
import com.vztechcore.vztune.data.download.TrackDownloader
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.models.StoredPlaylist
import com.vztechcore.vztune.data.prefs.models.isLocalPlaylistId
import com.vztechcore.vztune.data.prefs.store.FavoritePlaylistsStore
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.MediaListPreferencesStore
import com.vztechcore.vztune.data.ytmusic.YTMusicOwnerValidator
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import com.vztechcore.vztune.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class MediaListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val ytMusicRepository: YTMusicRepository,
    private val playerController: PlayerController,
    private val trackDownloader: TrackDownloader,
    private val store: MediaListPreferencesStore,
    private val favoritesStore: FavoritePlaylistsStore,
    private val localStore: LocalPlaylistsStore,
) : ViewModel() {

    sealed interface NavigationEvent {
        data class OpenArtist(val artist: Artist) : NavigationEvent
        data object CloseScreen : NavigationEvent
    }

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    private val route = savedStateHandle.toRoute<Routes.MediaList>()

    private val _uiState = MutableStateFlow(
        MediaListUiState(
            listId = route.id,
            title = route.title,
            subtitle = route.subtitle,
            thumbnailUrl = route.thumbnailUrl,
            isAlbum = route.isAlbum,
            isLocal = isLocalPlaylistId(route.id),
        )
    )
    val uiState: StateFlow<MediaListUiState> = _uiState.asStateFlow()

    init {
        val savedSort = store.getSortOrder(route.id)
        val savedManual = store.getManualOrder(route.id)
        _uiState.update { it.copy(sortOrder = savedSort, manualOrder = savedManual) }
        loadContent()
        viewModelScope.launch {
            favoritesStore.contains(route.id).collect { favorite ->
                _uiState.update { it.copy(isFavorite = favorite) }
            }
        }
    }

    private fun observeLocalPlaylist() {
        viewModelScope.launch {
            localStore.ensureRandomDefaultCover(route.id)
            localStore.playlist(route.id).collect { playlist ->
                _uiState.update {
                    if (playlist == null) {
                        it.copy(isLoading = false, errorMessage = "Playlist não encontrada.")
                    } else {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            title = playlist.name,
                            owner = "Você",
                            subtitle = "Você",
                            thumbnailUrl = playlist.coverPath,
                            mosaicThumbnails = playlist.mosaic,
                            allTracks = playlist.tracks.mapNotNull { stored -> stored.toTrack() },
                        )
                    }
                }
            }
        }
    }

    private fun loadContent() {
        if (_uiState.value.isLocal) {
            observeLocalPlaylist()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                if (route.isAlbum) {
                    val album = ytMusicRepository.browseAlbum(route.id)

                    val resolvedArtist = listOfNotNull(
                        album.artist,
                        route.subtitle,
                        _uiState.value.subtitle,
                    ).firstOrNull { YTMusicOwnerValidator.isValidOwnerName(it) }

                    val resolvedTitle = album.title ?: _uiState.value.title

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = resolvedTitle,
                            subtitle = resolvedArtist ?: it.subtitle,
                            owner = resolvedArtist,
                            ownerAvatarUrl = null,
                            artistId = album.artistId,
                            year = album.year,
                            thumbnailUrl = album.coverUrl ?: it.thumbnailUrl,
                            description = album.description,
                            views = album.views,
                            // Tira duplicadas (mesmo source + id) pra LazyColumn nunca crashar com key repetida
                            allTracks = album.tracks.distinctBy { "${it.source}_${it.id}" },
                        )
                    }

                    loadMoreAlbumsByArtist(
                        artistName = resolvedArtist,
                        artistId = album.artistId,
                        excludeAlbumTitle = resolvedTitle,
                    )
                } else {
                    var playlist = ytMusicRepository.browsePlaylist(route.id)
                    if (playlist.tracks.isEmpty()) {
                        delay(1000)
                        playlist = ytMusicRepository.browsePlaylist(route.id)
                    }

                    val resolvedOwner = listOfNotNull(
                        playlist.owner,
                        route.subtitle,
                        _uiState.value.subtitle,
                    ).firstOrNull { YTMusicOwnerValidator.isValidOwnerName(it) }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = playlist.title ?: it.title,
                            subtitle = resolvedOwner ?: it.subtitle,
                            owner = resolvedOwner,
                            ownerAvatarUrl = playlist.ownerAvatarUrl,
                            year = playlist.year,
                            views = playlist.views,
                            originalTrackCount = playlist.trackCount,
                            thumbnailUrl = playlist.coverUrl ?: it.thumbnailUrl,
                            description = playlist.description,
                            mosaicThumbnails = playlist.tracks
                                .asSequence()
                                .mapNotNull { t -> t.thumbnailUrl }
                                .distinct()
                                .take(4)
                                .toList(),
                            allTracks = playlist.tracks.distinctBy { "${it.source}_${it.id}" },
                        )
                    }

                    if (playlist.ownerAvatarUrl.isNullOrBlank() && !resolvedOwner.isNullOrBlank()) {
                        loadPlaylistOwnerAvatar(resolvedOwner)
                    }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = "Erro: ${e.message}") }
            }
        }
    }

    private fun loadPlaylistOwnerAvatar(ownerName: String) {
        viewModelScope.launch {
            val primary = ownerName
                .substringBefore(",")
                .substringBefore("&")
                .trim()
            if (primary.isBlank() || primary.equals("YouTube Music", ignoreCase = true)) return@launch
            val resolved = runCatching {
                ytMusicRepository.findChannelOrArtist(primary)
            }.getOrNull()
            if (resolved != null) {
                _uiState.update { current ->
                    current.copy(
                        artistId = current.artistId ?: resolved.id,
                        ownerAvatarUrl = current.ownerAvatarUrl ?: resolved.thumbnailUrl,
                    )
                }
            }
        }
    }

    private fun loadMoreAlbumsByArtist(
        artistName: String?,
        artistId: String?,
        excludeAlbumTitle: String?,
    ) {
        viewModelScope.launch {
            val (artist, moreAlbums) = ytMusicRepository.getMoreAlbumsByArtist(
                artistName = artistName,
                artistId = artistId,
                excludeAlbumId = route.id,
                excludeAlbumTitle = excludeAlbumTitle,
            )
            _uiState.update { current ->
                current.copy(
                    artistId = artist?.id ?: current.artistId,
                    ownerAvatarUrl = artist?.thumbnailUrl ?: current.ownerAvatarUrl,
                    moreAlbumsByArtist = moreAlbums,
                )
            }
        }
    }

    fun onTabSelected(tab: MediaListTab) {
        if (tab == _uiState.value.selectedTab) return
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onSortOrderSelected(order: SortOrder) {
        if (order == _uiState.value.sortOrder) return
        _uiState.update { it.copy(sortOrder = order, isShuffling = false) }
        store.setSortOrder(route.id, order)
    }

    fun onReorder(fromIndex: Int, toIndex: Int) {
        val currentTracks = _uiState.value.getDisplayTracks()
        if (fromIndex !in currentTracks.indices) return
        if (toIndex !in currentTracks.indices) return
        val newList = currentTracks.toMutableList()
        val item = newList.removeAt(fromIndex)
        newList.add(toIndex, item)
        val newOrderIds = newList.map { it.id }
        _uiState.update { it.copy(manualOrder = newOrderIds, sortOrder = SortOrder.MANUAL) }
        store.setManualOrder(route.id, newOrderIds)
        store.setSortOrder(route.id, SortOrder.MANUAL)
    }

    fun startShuffle() { _uiState.update { it.copy(isShuffling = true) } }

    fun shuffleOnce() {
        val currentTracks = _uiState.value.allTracks
        if (currentTracks.isEmpty()) return
        val newOrderIds = currentTracks.map { it.id }.shuffled()
        _uiState.update { it.copy(manualOrder = newOrderIds, sortOrder = SortOrder.SHUFFLE) }
    }

    fun stopShuffle() {
        _uiState.update { it.copy(isShuffling = false) }
        store.setManualOrder(route.id, _uiState.value.manualOrder)
        store.setSortOrder(route.id, SortOrder.SHUFFLE)
    }

    fun onTrackClick(track: Track) {
        val sorted = _uiState.value.getDisplayTracks()
        val idx = sorted.indexOfFirst { it.id == track.id && it.source == track.source }
        if (idx >= 0) playerController.playQueue(sorted, idx)
        else playerController.playQueue(listOf(track), 0)
    }

    fun onFavoriteClick() {
        val state = _uiState.value
        viewModelScope.launch {
            favoritesStore.toggle(
                StoredPlaylist(
                    id = route.id,
                    title = state.title,
                    owner = state.owner,
                    thumbnailUrl = state.thumbnailUrl,
                    mosaic = state.mosaicThumbnails,
                    isAlbum = state.isAlbum,
                )
            )
        }
    }

    fun onPlayNextClick(track: Track) { playerController.addToQueueNext(track) }

    fun onPlayNextAll() {
        val tracks = _uiState.value.getDisplayTracks()
        tracks.asReversed().forEach { playerController.addToQueueNext(it) }
    }

    fun onAddToQueueAll() {
        val tracks = _uiState.value.getDisplayTracks()
        tracks.forEach { playerController.addToQueueEnd(it) }
    }

    fun onDownloadAll() {
        val tracks = _uiState.value.allTracks
        if (tracks.isNotEmpty()) {
            trackDownloader.downloadAll(tracks)
        }
    }

    fun onOpenArtist() {
        val state = _uiState.value
        val directId = state.artistId?.takeIf { it.startsWith("UC") }
        val artistName = state.owner?.takeIf { it.isNotBlank() }
        if (directId != null && artistName != null) {
            viewModelScope.launch {
                _navigationEvent.emit(
                    NavigationEvent.OpenArtist(
                        Artist(
                            id = directId,
                            name = artistName,
                            thumbnailUrl = state.ownerAvatarUrl,
                            source = Source.YOUTUBE_MUSIC,
                        )
                    )
                )
            }
            return
        }
        if (artistName != null) {
            onOpenArtistByName(artistName)
        }
    }

    fun onOpenArtistByName(artistName: String) {
        viewModelScope.launch {
            // Álbum: passa o título do álbum como contexto, pra resolver só aceitar
            // o artista que tem ESSE álbum no catálogo. Playlist: sem contexto, cai pro mais parecido.
            val albumTitle = _uiState.value.title.takeIf { _uiState.value.isAlbum }
            val found = runCatching {
                ytMusicRepository.resolveArtist(artistName, albumTitle = albumTitle)
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(found))
            }
        }
    }

    fun onDeleteLocalPlaylist() {
        if (!_uiState.value.isLocal) return
        viewModelScope.launch {
            localStore.remove(route.id)
            _navigationEvent.emit(NavigationEvent.CloseScreen)
        }
    }

    fun onRenameLocalPlaylist(newTitle: String) {
        if (!_uiState.value.isLocal) return
        val clean = newTitle.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            localStore.rename(route.id, clean)
        }
    }

    fun onChangeLocalPlaylistCover(coverUri: Uri) {
        if (!_uiState.value.isLocal) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val coversDir = File(context.filesDir, "playlist_covers").apply {
                    if (!exists()) mkdirs()
                }
                val safeId = route.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val destFile = File(coversDir, "${safeId}_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(coverUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0L) {
                    _uiState.value.thumbnailUrl?.let { oldPath ->
                        val oldFile = File(oldPath)
                        if (oldFile.exists() && oldFile.parentFile == coversDir) {
                            runCatching { oldFile.delete() }
                        }
                    }
                    localStore.updateCover(route.id, destFile.absolutePath)
                }
            }
        }
    }

    fun onPlayAll() {
        val sorted = _uiState.value.getDisplayTracks()
        if (sorted.isNotEmpty()) playerController.playQueue(sorted, 0)
    }

    fun onShuffle() {
        val shuffled = _uiState.value.filteredTracks.shuffled()
        if (shuffled.isNotEmpty()) playerController.playQueue(shuffled, 0)
    }
}
VZ_EOF_4_FIX4

echo "[5/8] app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackMenuViewModel.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackMenuViewModel.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackMenuViewModel.kt" <<'VZ_EOF_5_FIX4'
package com.vztechcore.vztune.ui.components.sheets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.download.TrackDownloader
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.data.prefs.store.DownloadedTracksStore
import com.vztechcore.vztune.data.prefs.store.FavoriteTracksStore
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.PlayHistoryStore
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared ViewModel that powers the three-dots context menu across all screens
 * (Search, Library, LibraryList, MediaList, Artist, ArtistList) with album resolution.
 */
@HiltViewModel
class TrackMenuViewModel @Inject constructor(
    private val favoriteTracksStore: FavoriteTracksStore,
    private val localPlaylistsStore: LocalPlaylistsStore,
    private val downloadedTracksStore: DownloadedTracksStore,
    private val playHistoryStore: PlayHistoryStore,
    private val trackDownloader: TrackDownloader,
    private val playerController: PlayerController,
    private val ytMusicRepository: YTMusicRepository,
) : ViewModel() {

    sealed interface MenuNavigationEvent {
        data class OpenAlbum(val album: Album) : MenuNavigationEvent
        data class OpenArtist(val artist: Artist) : MenuNavigationEvent
    }

    private val _navigationEvent = MutableSharedFlow<MenuNavigationEvent>()
    val navigationEvent: SharedFlow<MenuNavigationEvent> = _navigationEvent.asSharedFlow()

    val favoriteKeys: StateFlow<Set<String>> = favoriteTracksStore.tracks
        .map { list -> list.map { StoredTrack.from(it).key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val downloadedKeys: StateFlow<Set<String>> = downloadedTracksStore.downloadedKeys
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val activeDownloads: StateFlow<Map<String, TrackDownloadItem>> = trackDownloader.downloads

    val localPlaylists: StateFlow<List<LocalPlaylist>> = localPlaylistsStore.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val albumCache = mutableMapOf<String, Album?>()
    private var activeAlbumKey: String? = null

    private val _resolvedAlbum = MutableStateFlow<Album?>(null)
    val resolvedAlbum: StateFlow<Album?> = _resolvedAlbum.asStateFlow()

    fun resolveAlbumFor(track: Track) {
        val key = StoredTrack.from(track).key
        activeAlbumKey = key
        if (albumCache.containsKey(key)) {
            _resolvedAlbum.value = albumCache[key]
            return
        }
        _resolvedAlbum.value = null
        viewModelScope.launch {
            val found = runCatching { ytMusicRepository.findAlbumForTrack(track) }.getOrNull()
            albumCache[key] = found
            if (activeAlbumKey == key) {
                _resolvedAlbum.value = found
            }
        }
    }

    fun onOpenAlbumForTrack(track: Track) {
        val key = StoredTrack.from(track).key
        val cached = albumCache[key]
        if (cached != null) {
            viewModelScope.launch {
                _navigationEvent.emit(MenuNavigationEvent.OpenAlbum(cached))
            }
            return
        }
        viewModelScope.launch {
            val found = runCatching { ytMusicRepository.findAlbumForTrack(track) }.getOrNull()
            albumCache[key] = found
            if (activeAlbumKey == key) {
                _resolvedAlbum.value = found
            }
            if (found != null) {
                _navigationEvent.emit(MenuNavigationEvent.OpenAlbum(found))
            }
        }
    }

    fun onToggleFavorite(track: Track) {
        viewModelScope.launch { favoriteTracksStore.toggle(track) }
    }

    fun onAddToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch { localPlaylistsStore.addTrack(playlistId, track) }
    }

    fun onRemoveFromLocalPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch { localPlaylistsStore.removeTrack(playlistId, track) }
    }

    fun onRemoveFromRecentHistory(track: Track) {
        viewModelScope.launch {
            val key = StoredTrack.from(track).key
            playHistoryStore.remove(key)
        }
    }

    fun onPlayNext(track: Track) {
        playerController.addToQueueNext(track)
    }

    fun onAddToQueue(track: Track) {
        playerController.addToQueueEnd(track)
    }

    fun onDownload(track: Track) {
        trackDownloader.download(track)
    }

    fun onRemoveDownload(track: Track) {
        trackDownloader.deleteDownload(track)
    }

    fun onOpenArtistByName(artistName: String) {
        viewModelScope.launch {
            // Sem contexto: ainda usa Levenshtein (mais parecido), nunca mais firstOrNull
            val found = runCatching {
                ytMusicRepository.resolveArtist(artistName)
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(MenuNavigationEvent.OpenArtist(found))
            }
        }
    }

    /** Clique em artista DENTRO de uma faixa: verifica quem tem ESSA faixa / ESSE álbum no catálogo. */
    fun onOpenArtistForTrack(track: Track, artistName: String) {
        viewModelScope.launch {
            val found = runCatching {
                ytMusicRepository.resolveArtist(
                    artistName,
                    trackTitle = track.title,
                    albumTitle = track.album?.takeIf { it.isNotBlank() },
                )
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(MenuNavigationEvent.OpenArtist(found))
            }
        }
    }
}
VZ_EOF_5_FIX4

echo "[6/8] app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/SearchViewModel.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/SearchViewModel.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/search/SearchViewModel.kt" <<'VZ_EOF_6_FIX4'
package com.vztechcore.vztune.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.core.domain.search.SearchFilter
import com.vztechcore.vztune.core.domain.search.SearchHit
import com.vztechcore.vztune.core.domain.search.SearchResult
import com.vztechcore.vztune.core.domain.search.SearchSuggestions
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.download.TrackDownloader
import com.vztechcore.vztune.data.music.MusicRepository
import com.vztechcore.vztune.data.player.PlayerController
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.data.prefs.store.DownloadedTracksStore
import com.vztechcore.vztune.data.prefs.store.FavoriteTracksStore
import com.vztechcore.vztune.data.prefs.store.LocalPlaylistsStore
import com.vztechcore.vztune.data.prefs.store.RecentPlayedStore
import com.vztechcore.vztune.data.prefs.store.SearchHistoryStore
import com.vztechcore.vztune.data.ytmusic.YTMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val ytMusicRepository: YTMusicRepository,
    private val historyStore: SearchHistoryStore,
    private val recentPlayedStore: RecentPlayedStore,
    private val favoriteTracksStore: FavoriteTracksStore,
    private val localPlaylistsStore: LocalPlaylistsStore,
    private val downloadedTracksStore: DownloadedTracksStore,
    private val trackDownloader: TrackDownloader,
    private val playerController: PlayerController,
) : ViewModel() {

    sealed interface NavigationEvent {
        data class OpenPlaylist(
            val id: String, val title: String, val owner: String?,
            val thumbnailUrl: String?, val mosaic: List<String>,
        ) : NavigationEvent
        data class OpenAlbum(
            val id: String, val title: String, val artist: String,
            val thumbnailUrl: String?,
        ) : NavigationEvent
        data class OpenArtist(
            val id: String, val name: String, val thumbnailUrl: String?,
        ) : NavigationEvent
    }

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    private data class CachedTab(val query: String, val hits: List<SearchHit>, val hasMore: Boolean)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val favoriteKeys: StateFlow<Set<String>> = favoriteTracksStore.tracks
        .map { list -> list.map { StoredTrack.from(it).key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val downloadedKeys: StateFlow<Set<String>> = downloadedTracksStore.items
        .map { list -> list.map { it.key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val activeDownloads: StateFlow<Map<String, TrackDownloadItem>> = trackDownloader.downloads

    val localPlaylists: StateFlow<List<LocalPlaylist>> = localPlaylistsStore.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val filterCache = mutableMapOf<SearchFilter, CachedTab>()
    private var suggestJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            historyStore.history.collect { list ->
                _uiState.update { it.copy(history = list) }
            }
        }
        viewModelScope.launch {
            recentPlayedStore.tracks.collect { list ->
                _uiState.update { it.copy(recentTracks = list) }
            }
        }
    }

    fun onQueryChange(value: String) {
        suggestJob?.cancel()
        searchJob?.cancel()
        filterCache.clear()
        _uiState.update {
            it.copy(
                query = value,
                errorMessage = null,
                hasSearched = false,
                hits = emptyList(),
                hasMore = false,
                isLoading = false,
                isLoadingMore = false,
                isRefreshing = false,
            )
        }
        if (value.isBlank() || value.trim().length < 2) {
            _uiState.update { it.copy(suggestions = SearchSuggestions()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(400)
            val s = musicRepository.suggestions(value)
            _uiState.update { it.copy(suggestions = s) }
        }
    }

    fun onFocusChanged(focused: Boolean) {
        _uiState.update { it.copy(isFocused = focused) }
    }

    fun onFilterSelect(filter: SearchFilter) {
        if (filter == _uiState.value.selectedFilter) return
        val currentQuery = _uiState.value.query
        val cached = filterCache[filter]
        if (cached != null && cached.query == currentQuery) {
            _uiState.update { it.copy(selectedFilter = filter, hits = cached.hits, hasMore = cached.hasMore, hasSearched = true) }
            return
        }
        _uiState.update { it.copy(selectedFilter = filter) }
        if (currentQuery.isNotBlank()) {
            runSearch(currentQuery, filter, isRefresh = false, loadMore = false)
        }
    }

    fun onSubmit() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        _uiState.update { it.copy(isFocused = false) }
        viewModelScope.launch { historyStore.add(query) }
        filterCache.clear()
        runSearch(query, _uiState.value.selectedFilter, isRefresh = false, loadMore = false)
    }

    fun onRefresh() {
        if (_uiState.value.query.isBlank()) return
        filterCache.clear()
        runSearch(_uiState.value.query, _uiState.value.selectedFilter, isRefresh = true, loadMore = false)
    }

    fun onRemoveHistory(query: String) { viewModelScope.launch { historyStore.remove(query) } }
    fun onClearHistory() { viewModelScope.launch { historyStore.clear() } }

    fun onLoadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isLoading || !state.hasMore) return
        if (state.query.isBlank()) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val filter = state.selectedFilter
            when (val result = musicRepository.search(state.query, filter, loadMore = true)) {
                is SearchResult.Success -> {
                    val merged = _uiState.value.hits + result.hits
                    filterCache[filter] = CachedTab(state.query, merged, result.hasMore)
                    _uiState.update { it.copy(isLoadingMore = false, hits = merged, hasMore = result.hasMore) }
                }
                is SearchResult.Failure -> {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            }
        }
    }

    fun onHitClick(hit: SearchHit) {
        when (hit) {
            is SearchHit.SongHit -> playRadio(hit.track)
            is SearchHit.VideoHit -> playRadio(hit.track)
            is SearchHit.PlaylistHit -> {
                viewModelScope.launch {
                    _navigationEvent.emit(
                        NavigationEvent.OpenPlaylist(
                            hit.playlist.id,
                            hit.playlist.title,
                            hit.playlist.owner,
                            hit.playlist.thumbnailUrl,
                            hit.playlist.mosaicThumbnails,
                        )
                    )
                }
            }
            is SearchHit.AlbumHit -> {
                viewModelScope.launch {
                    _navigationEvent.emit(
                        NavigationEvent.OpenAlbum(
                            hit.album.id,
                            hit.album.title,
                            hit.album.artist,
                            hit.album.thumbnailUrl,
                        )
                    )
                }
            }
            is SearchHit.ArtistHit -> {
                viewModelScope.launch {
                    _navigationEvent.emit(
                        NavigationEvent.OpenArtist(
                            hit.artist.id,
                            hit.artist.name,
                            hit.artist.thumbnailUrl,
                        )
                    )
                }
            }
        }
    }

    fun onPlayNextClick(track: Track) {
        playerController.addToQueueNext(track)
    }

    fun onAddToQueueClick(track: Track) {
        playerController.addToQueueEnd(track)
    }

    /** Strictly toggles favorite status without triggering any automatic download. */
    fun onToggleFavorite(track: Track) {
        viewModelScope.launch {
            favoriteTracksStore.toggle(track)
        }
    }

    fun onAddToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch {
            localPlaylistsStore.addTrack(playlistId, track)
        }
    }

    fun onDownloadTrack(track: Track) {
        trackDownloader.download(track)
    }

    fun onRemoveDownload(track: Track) {
        trackDownloader.deleteDownload(track)
    }

    fun onOpenArtistByName(artistName: String) {
        viewModelScope.launch {
            // Mais parecido por Levenshtein, nunca mais o primeiro da busca
            val found = runCatching {
                ytMusicRepository.resolveArtist(artistName)
            }.getOrNull()
            if (found != null) {
                _navigationEvent.emit(NavigationEvent.OpenArtist(found.id, found.name, found.thumbnailUrl))
            }
        }
    }

    private fun playRadio(seed: Track) {
        if (_uiState.value.isBuildingQueue) return
        _uiState.update { it.copy(isBuildingQueue = true) }
        viewModelScope.launch { recentPlayedStore.add(seed) }
        viewModelScope.launch {
            val queue = runCatching { musicRepository.buildRadioQueue(seed) }.getOrDefault(listOf(seed))
            _uiState.update { it.copy(isBuildingQueue = false) }
            val idx = queue.indexOfFirst { it.id == seed.id && it.source == seed.source }
            playerController.playQueue(queue, if (idx >= 0) idx else 0)
        }
    }

    private fun runSearch(query: String, filter: SearchFilter, isRefresh: Boolean, loadMore: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh && !loadMore,
                    isRefreshing = isRefresh,
                    hasSearched = true,
                    errorMessage = null,
                )
            }
            when (val result = musicRepository.search(query, filter, loadMore)) {
                is SearchResult.Success -> {
                    filterCache[filter] = CachedTab(query, result.hits, result.hasMore)
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, hits = result.hits, hasMore = result.hasMore) }
                }
                is SearchResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, errorMessage = result.message) }
                }
            }
        }
    }
}
VZ_EOF_6_FIX4

echo "[7/8] app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackOptionsSheet.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackOptionsSheet.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/sheets/TrackOptionsSheet.kt" <<'VZ_EOF_7_FIX4'
package com.vztechcore.vztune.ui.components.sheets

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.download.DownloadStatus
import com.vztechcore.vztune.data.download.TrackDownloadItem
import com.vztechcore.vztune.data.prefs.models.LocalPlaylist
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Delete
import com.vztechcore.vztune.ui.icons.actions.DeleteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteOutline
import com.vztechcore.vztune.ui.icons.actions.Info
import com.vztechcore.vztune.ui.icons.actions.Share
import com.vztechcore.vztune.ui.icons.media.Album
import com.vztechcore.vztune.ui.icons.media.Artist
import com.vztechcore.vztune.ui.icons.media.Download
import com.vztechcore.vztune.ui.icons.media.DownloadDone
import com.vztechcore.vztune.ui.icons.media.Groups
import com.vztechcore.vztune.ui.icons.media.PlaylistAdd
import com.vztechcore.vztune.ui.icons.media.QueueMusic
import com.vztechcore.vztune.ui.icons.player.PlayCircleFilled

private val SheetContainerColor = Color(0xFF121829)
private val DownloadGreen = Color(0xFF4ADE80)
private val DangerRed = Color(0xFFF87171)

enum class TrackMenuContext {
    DEFAULT,
    RECENT_HISTORY,
    EXTERNAL_PLAYLIST,
    LOCAL_PLAYLIST,
}

/**
 * Context menu opened from the three-dots button on any track card across the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsSheet(
    track: Track,
    menuContext: TrackMenuContext = TrackMenuContext.DEFAULT,
    resolvedAlbum: Album? = null,
    isFavorite: Boolean,
    isDownloaded: Boolean = false,
    downloadItem: TrackDownloadItem? = null,
    localPlaylists: List<LocalPlaylist>,
    onDismiss: () -> Unit,
    onRemoveFromRecentHistory: (Track) -> Unit = {},
    onRemoveFromLocalPlaylist: (Track) -> Unit = {},
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (playlistId: String, track: Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onDownload: (Track) -> Unit = {},
    onRemoveDownload: (Track) -> Unit = {},
    onOpenDownloadsScreen: () -> Unit = {},
    onOpenAlbum: (Album) -> Unit = {},
    onOpenAlbumForTrack: (Track) -> Unit = {},
    onOpenArtistByName: (String) -> Unit,
    onOpenArtistForTrack: (Track, String) -> Unit = { _, name -> onOpenArtistByName(name) },
    onTrackInfo: (Track) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showArtistPicker by remember { mutableStateOf(false) }
    var showRemoveDownloadSheet by remember { mutableStateOf(false) }

    val artists = remember(track.artist) { parseTrackArtists(track.artist) }
    val isDownloading = downloadItem?.status == DownloadStatus.DOWNLOADING ||
        downloadItem?.status == DownloadStatus.QUEUED ||
        downloadItem?.status == DownloadStatus.PAUSED

    val addToPlaylistLabel = when (menuContext) {
        TrackMenuContext.EXTERNAL_PLAYLIST, TrackMenuContext.LOCAL_PLAYLIST -> "Adicionar à outra playlist"
        else -> "Adicionar à playlist"
    }

    if (showRemoveDownloadSheet) {
        RemoveDownloadSheet(
            track = track,
            onConfirmRemove = {
                showRemoveDownloadSheet = false
                onRemoveDownload(track)
                onDismiss()
            },
            onDismiss = { showRemoveDownloadSheet = false },
        )
        return
    }

    if (showPlaylistPicker) {
        PlaylistPickerSheet(
            title = addToPlaylistLabel,
            playlists = localPlaylists,
            onPick = { playlist ->
                onAddToPlaylist(playlist.id, track)
                showPlaylistPicker = false
                onDismiss()
            },
            onDismiss = { showPlaylistPicker = false },
        )
        return
    }

    if (showArtistPicker) {
        ArtistPickerSheet(
            artists = artists,
            onPick = { artistName ->
                showArtistPicker = false
                onDismiss()
                // Passa a faixa: o resolver só aceita quem tem ESSA faixa no catálogo
                onOpenArtistForTrack(track, artistName)
            },
            onDismiss = { showArtistPicker = false },
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            TrackSheetHeader(track = track)

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(alpha = 0.08f),
            )

            // Context-specific top removal actions
            if (menuContext == TrackMenuContext.RECENT_HISTORY) {
                SheetOptionRow(
                    icon = VzIcons.DeleteFilled,
                    iconTint = DangerRed,
                    label = "Apagar",
                    labelColor = DangerRed,
                    onClick = {
                        onRemoveFromRecentHistory(track)
                        onDismiss()
                    },
                )
            }

            if (menuContext == TrackMenuContext.LOCAL_PLAYLIST) {
                SheetOptionRow(
                    icon = VzIcons.DeleteFilled,
                    iconTint = DangerRed,
                    label = "Excluir faixa da playlist",
                    labelColor = DangerRed,
                    onClick = {
                        onRemoveFromLocalPlaylist(track)
                        onDismiss()
                    },
                )
            }

            // 1. Favorite / Unfavorite
            SheetOptionRow(
                icon = if (isFavorite) VzIcons.FavoriteFilled else VzIcons.FavoriteOutline,
                iconTint = if (isFavorite) Color(0xFFEC4899) else Color.White,
                label = if (isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos",
                onClick = {
                    onToggleFavorite(track)
                    onDismiss()
                },
            )

            // 2. Add to playlist / Add to another playlist
            SheetOptionRow(
                icon = VzIcons.PlaylistAdd,
                label = addToPlaylistLabel,
                onClick = { showPlaylistPicker = true },
            )

            // 3. Play next
            SheetOptionRow(
                icon = VzIcons.PlayCircleFilled,
                label = "Reproduzir como próxima faixa",
                onClick = {
                    onPlayNext(track)
                    onDismiss()
                },
            )

            // 4. Add to queue
            SheetOptionRow(
                icon = VzIcons.QueueMusic,
                label = "Adicionar à fila",
                onClick = {
                    onAddToQueue(track)
                    onDismiss()
                },
            )

            // 5. Download / Downloading / Downloaded
            when {
                isDownloading -> {
                    val progress = downloadItem?.progress ?: 0f
                    val pct = downloadItem?.percentage ?: 0
                    val statusLabel = if (downloadItem?.status == DownloadStatus.PAUSED) {
                        "Download pausado ($pct%)"
                    } else if (pct > 0) {
                        "Baixando faixa ($pct%)"
                    } else {
                        "Baixando faixa"
                    }
                    SheetDownloadProgressRow(
                        progress = progress,
                        label = statusLabel,
                        onClick = {
                            onDismiss()
                            onOpenDownloadsScreen()
                        },
                    )
                }
                isDownloaded -> {
                    SheetOptionRow(
                        icon = VzIcons.DownloadDone,
                        iconTint = DownloadGreen,
                        label = "Faixa baixada",
                        labelColor = Color.White,
                        onClick = { showRemoveDownloadSheet = true },
                    )
                }
                else -> {
                    SheetOptionRow(
                        icon = VzIcons.Download,
                        iconTint = Color.White,
                        label = "Baixar faixa",
                        onClick = {
                            onDownload(track)
                            onDismiss()
                        },
                    )
                }
            }

            // 6. Share
            SheetOptionRow(
                icon = VzIcons.Share,
                label = "Compartilhar",
                onClick = {
                    shareTrack(context, track)
                    onDismiss()
                },
            )

            // 7. Go to album (shows immediately when track.album is known or once resolved)
            val albumTitle = resolvedAlbum?.title ?: track.album?.takeIf {
                it.isNotBlank() && !it.equals("Desconhecido", ignoreCase = true)
            }
            if (!albumTitle.isNullOrBlank()) {
                SheetOptionRow(
                    icon = VzIcons.Album,
                    label = "Álbum",
                    subtitle = albumTitle,
                    onClick = {
                        onDismiss()
                        if (resolvedAlbum != null) {
                            onOpenAlbum(resolvedAlbum)
                        } else {
                            onOpenAlbumForTrack(track)
                        }
                    },
                )
            }

            // 8. Go to artist(s)
            if (artists.isNotEmpty()) {
                val hasMultiple = artists.size > 1
                SheetOptionRow(
                    icon = if (hasMultiple) VzIcons.Groups else VzIcons.Artist,
                    label = if (hasMultiple) "Ir para os artistas" else "Ir para o artista",
                    subtitle = if (hasMultiple) artists.joinToString(", ") else artists.first(),
                    onClick = {
                        if (hasMultiple) {
                            showArtistPicker = true
                        } else {
                            onDismiss()
                            onOpenArtistForTrack(track, artists.first())
                        }
                    },
                )
            }

            // 9. Track details
            SheetOptionRow(
                icon = VzIcons.Info,
                label = "Ver informações da faixa",
                onClick = {
                    onDismiss()
                    onTrackInfo(track)
                },
            )
        }
    }
}

/**
 * Confirmation bottom sheet displayed when tapping "Faixa baixada" or the delete icon in completed downloads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoveDownloadSheet(
    track: Track,
    onConfirmRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            TrackSheetHeader(track = track)

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color.White.copy(alpha = 0.08f),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = VzIcons.DownloadDone,
                        contentDescription = null,
                        tint = DownloadGreen,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Faixa salva para reprodução offline",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Esta música já está armazenada no seu dispositivo para você ouvir mesmo sem conexão com a internet. Deseja removê-la dos seus downloads?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                    ) {
                        Text(
                            text = "Manter faixa",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Button(
                        onClick = onConfirmRemove,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DangerRed.copy(alpha = 0.18f),
                            contentColor = DangerRed,
                        ),
                        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                    ) {
                        Icon(
                            imageVector = VzIcons.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Apagar",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackSheetHeader(track: Track) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            if (track.thumbnailUrl != null) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SheetOptionRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    iconTint: Color = Color.White,
    labelColor: Color = Color.White,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = labelColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SheetDownloadProgressRow(
    progress: Float,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (progress > 0f) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(28.dp),
                    color = DownloadGreen,
                    trackColor = DownloadGreen.copy(alpha = 0.2f),
                    strokeWidth = 2.5.dp,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = DownloadGreen,
                    trackColor = DownloadGreen.copy(alpha = 0.2f),
                    strokeWidth = 2.5.dp,
                )
            }
            Icon(
                imageVector = VzIcons.Download,
                contentDescription = null,
                tint = DownloadGreen,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.55f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickerSheet(
    title: String = "Adicionar à playlist",
    playlists: List<LocalPlaylist>,
    onPick: (LocalPlaylist) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (playlists.isEmpty()) {
                Text(
                    text = "Você ainda não criou nenhuma playlist. Crie uma na Biblioteca, em \"Suas playlists\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else {
                playlists.forEach { playlist ->
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(playlist) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistPickerSheet(
    artists: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetContainerColor,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Escolher artista",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            artists.forEach { artistName ->
                SheetOptionRow(
                    icon = VzIcons.Artist,
                    label = artistName,
                    onClick = { onPick(artistName) },
                )
            }
        }
    }
}

private fun parseTrackArtists(raw: String): List<String> {
    if (raw.isBlank() || raw.equals("Desconhecido", ignoreCase = true)) return emptyList()
    return raw
        .split(Regex("\\s*(?:,|&|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bx\\b)\\s*", RegexOption.IGNORE_CASE))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private val SharePhrases = listOf(
    "Ouvindo no VzTune 🎧",
    "Escuta essa faixa no VzTune ✨",
    "Descobri no VzTune 🔥",
    "Tocando agora no VzTune 🎵",
    "Recomendo ouvir no VzTune 🚀",
    "Vibe boa no VzTune 🎶",
)

private fun shareTrack(context: Context, track: Track) {
    val url = if (track.source == Source.YOUTUBE) {
        "https://www.youtube.com/watch?v=${track.id}"
    } else {
        "https://music.youtube.com/watch?v=${track.id}"
    }
    val phrase = SharePhrases.random()
    val shareText = "$phrase\n${track.title} — ${track.artist}\n$url"

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, track.title)
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    val chooser = Intent.createChooser(sendIntent, "Compartilhar faixa")
    context.startActivity(chooser)
}
VZ_EOF_7_FIX4

echo "[8/8] app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt"
mkdir -p "$(dirname "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt")"
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt" <<'VZ_EOF_8_FIX4'
package com.vztechcore.vztune.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.data.prefs.models.isLocalPlaylistId
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.feedback.EclipseBackground
import com.vztechcore.vztune.ui.components.feedback.rememberDominantColor
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.sheets.TrackMenuContext
import com.vztechcore.vztune.ui.components.sheets.TrackMenuViewModel
import com.vztechcore.vztune.ui.components.sheets.TrackOptionsSheet
import com.vztechcore.vztune.ui.screens.artist.ArtistScreen
import com.vztechcore.vztune.ui.screens.artist.ArtistViewModel
import com.vztechcore.vztune.ui.screens.artist.list.ArtistListScreen
import com.vztechcore.vztune.ui.screens.artist.list.ArtistListViewModel
import com.vztechcore.vztune.ui.screens.downloads.DownloadBannerViewModel
import com.vztechcore.vztune.ui.screens.downloads.DownloadsScreen
import com.vztechcore.vztune.ui.screens.downloads.DownloadsViewModel
import com.vztechcore.vztune.ui.screens.library.LibraryScreen
import com.vztechcore.vztune.ui.screens.library.LibraryViewModel
import com.vztechcore.vztune.ui.screens.library.list.LibraryListScreen
import com.vztechcore.vztune.ui.screens.library.list.LibraryListViewModel
import com.vztechcore.vztune.ui.screens.main.MiniPlayerViewModel
import com.vztechcore.vztune.ui.screens.medialist.MediaListScreen
import com.vztechcore.vztune.ui.screens.medialist.MediaListViewModel
import com.vztechcore.vztune.ui.screens.search.SearchScreen
import com.vztechcore.vztune.ui.screens.search.SearchViewModel
import com.vztechcore.vztune.ui.screens.trackinfo.TrackInfoScreen
import com.vztechcore.vztune.ui.screens.trackinfo.TrackInfoViewModel
import com.vztechcore.vztune.ui.theme.VzOnSurface

private data class ActiveTrackMenu(
    val track: Track,
    val context: TrackMenuContext = TrackMenuContext.DEFAULT,
    val localPlaylistId: String? = null,
)

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    val currentTab = when {
        backStackEntry?.destination?.hasRoute<Routes.Search>() == true -> AppTab.SEARCH
        backStackEntry?.destination?.hasRoute<Routes.Analytics>() == true -> AppTab.ANALYTICS
        backStackEntry?.destination?.hasRoute<Routes.Library>() == true -> AppTab.LIBRARY
        else -> AppTab.HOME
    }

    val isMediaListScreen = backStackEntry?.destination?.hasRoute<Routes.MediaList>() == true
    val isArtistScreen = backStackEntry?.destination?.hasRoute<Routes.Artist>() == true
    val isArtistListScreen = backStackEntry?.destination?.hasRoute<Routes.ArtistList>() == true
    val isLibraryListScreen = backStackEntry?.destination?.hasRoute<Routes.LibraryList>() == true
    val isDownloadsScreen = backStackEntry?.destination?.hasRoute<Routes.Downloads>() == true
    val isDetailScreen = isMediaListScreen || isArtistScreen || isArtistListScreen || isLibraryListScreen || isDownloadsScreen
    val isTrackInfoScreen = backStackEntry?.destination?.hasRoute<Routes.TrackInfo>() == true

    val playback = LocalNowPlaying.current
    val track = playback.currentTrack

    val miniPlayerVm: MiniPlayerViewModel = hiltViewModel()
    val bannerVm: DownloadBannerViewModel = hiltViewModel()
    val menuVm: TrackMenuViewModel = hiltViewModel()

    val dominantColor = rememberDominantColor(track?.thumbnailUrl)
    val isMinimized by miniPlayerVm.isMinimized.collectAsStateWithLifecycle()
    val bannerTrack by bannerVm.bannerTrack.collectAsStateWithLifecycle()

    val favoriteKeys by menuVm.favoriteKeys.collectAsStateWithLifecycle()
    val downloadedKeys by menuVm.downloadedKeys.collectAsStateWithLifecycle()
    val activeDownloads by menuVm.activeDownloads.collectAsStateWithLifecycle()
    val localPlaylists by menuVm.localPlaylists.collectAsStateWithLifecycle()
    val resolvedAlbum by menuVm.resolvedAlbum.collectAsStateWithLifecycle()

    var activeMenu by remember { mutableStateOf<ActiveTrackMenu?>(null) }

    fun openTrackMenu(
        targetTrack: Track,
        context: TrackMenuContext = TrackMenuContext.DEFAULT,
        localPlaylistId: String? = null,
    ) {
        menuVm.resolveAlbumFor(targetTrack)
        activeMenu = ActiveTrackMenu(targetTrack, context, localPlaylistId)
    }

    LaunchedEffect(Unit) {
        menuVm.navigationEvent.collect { event ->
            when (event) {
                is TrackMenuViewModel.MenuNavigationEvent.OpenAlbum -> navController.openAlbum(event.album)
                is TrackMenuViewModel.MenuNavigationEvent.OpenArtist -> navController.navigate(
                    Routes.Artist(event.artist.id, event.artist.name, event.artist.thumbnailUrl)
                )
            }
        }
    }

    LaunchedEffect(backStackEntry?.destination?.route) {
        if (backStackEntry?.destination?.hasRoute<Routes.Search>() == true) {
            miniPlayerVm.setMinimized(true)
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -30f) miniPlayerVm.setMinimized(true)
                else if (available.y > 30f) miniPlayerVm.setMinimized(false)
                return Offset.Zero
            }
        }
    }

    activeMenu?.let { menu ->
        val key = StoredTrack.from(menu.track).key
        TrackOptionsSheet(
            track = menu.track,
            menuContext = menu.context,
            resolvedAlbum = resolvedAlbum,
            isFavorite = key in favoriteKeys,
            isDownloaded = key in downloadedKeys,
            downloadItem = activeDownloads[key],
            localPlaylists = localPlaylists,
            onDismiss = { activeMenu = null },
            onRemoveFromRecentHistory = menuVm::onRemoveFromRecentHistory,
            onRemoveFromLocalPlaylist = { t ->
                menu.localPlaylistId?.let { pid -> menuVm.onRemoveFromLocalPlaylist(pid, t) }
            },
            onToggleFavorite = menuVm::onToggleFavorite,
            onAddToPlaylist = menuVm::onAddToPlaylist,
            onPlayNext = menuVm::onPlayNext,
            onAddToQueue = menuVm::onAddToQueue,
            onDownload = menuVm::onDownload,
            onRemoveDownload = menuVm::onRemoveDownload,
            onOpenDownloadsScreen = { navController.navigate(Routes.Downloads) },
            onOpenAlbum = { album -> navController.openAlbum(album) },
            onOpenAlbumForTrack = menuVm::onOpenAlbumForTrack,
            onOpenArtistByName = menuVm::onOpenArtistByName,
            onOpenArtistForTrack = menuVm::onOpenArtistForTrack,
            onTrackInfo = { t -> navController.navigateToTrackInfo(t) },
        )
    }

    EclipseBackground(baseColor = dominantColor) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.Home,
            ) {
                composable<Routes.Home> { Placeholder("Home — coming soon") }
                composable<Routes.Analytics> { Placeholder("Analytics — coming soon") }

                composable<Routes.Library> {
                    val vm: LibraryViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    LibraryScreen(
                        state = state,
                        onTabSelected = vm::onTabSelected,
                        onShortcutClick = { navController.navigate(Routes.LibraryList(it.name)) },
                        onTrackClick = vm::onTrackClick,
                        onTrackInfoClick = { t ->
                            openTrackMenu(t, context = TrackMenuContext.RECENT_HISTORY)
                        },
                        onPlaylistClick = { item ->
                            navController.navigate(
                                Routes.MediaList(
                                    id = item.id,
                                    // Favorito de álbum abre como álbum, senão crashava com key duplicada
                                    isAlbum = item.isAlbum,
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    thumbnailUrl = item.imageUrl,
                                )
                            )
                        },
                        onCreatePlaylist = vm::createPlaylist,
                    )
                }

                composable<Routes.LibraryList> {
                    val vm: LibraryListViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    LibraryListScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onTrackClick = vm::onTrackClick,
                        onTrackInfoClick = { t -> openTrackMenu(t, context = TrackMenuContext.DEFAULT) },
                        onArtistClick = { artist ->
                            navController.navigate(Routes.Artist(artist.id, artist.name, artist.thumbnailUrl))
                        },
                        onShuffleClick = vm::onShuffleClick,
                        onPlayAllClick = vm::onPlayAllClick,
                        onDownloadsHistoryClick = { navController.navigate(Routes.Downloads) },
                    )
                }

                composable<Routes.Downloads> {
                    val vm: DownloadsViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    DownloadsScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onTabSelected = vm::onTabSelected,
                        onTogglePauseResume = vm::onTogglePauseResume,
                        onCancelDownload = vm::onCancelDownload,
                        onDeleteDownload = vm::onDeleteDownload,
                        onPlayTrack = vm::onPlayTrack,
                        onPauseAll = vm::onPauseAll,
                        onResumeAll = vm::onResumeAll,
                        onCancelAll = vm::onCancelAll,
                    )
                }

                composable<Routes.Search> {
                    val vm: SearchViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()

                    LaunchedEffect(Unit) {
                        vm.navigationEvent.collect { event ->
                            when (event) {
                                is SearchViewModel.NavigationEvent.OpenPlaylist -> navController.navigate(
                                    Routes.MediaList(
                                        id = event.id,
                                        isAlbum = false,
                                        title = event.title,
                                        subtitle = event.owner,
                                        thumbnailUrl = event.thumbnailUrl,
                                    )
                                )
                                is SearchViewModel.NavigationEvent.OpenArtist -> navController.navigate(
                                    Routes.Artist(
                                        id = event.id,
                                        name = event.name,
                                        thumbnailUrl = event.thumbnailUrl,
                                    )
                                )
                                is SearchViewModel.NavigationEvent.OpenAlbum -> navController.navigate(
                                    Routes.MediaList(
                                        id = event.id,
                                        isAlbum = true,
                                        title = event.title,
                                        subtitle = event.artist,
                                        thumbnailUrl = event.thumbnailUrl,
                                    )
                                )
                            }
                        }
                    }

                    SearchScreen(
                        state = state,
                        favoriteKeys = favoriteKeys,
                        downloadedKeys = downloadedKeys,
                        activeDownloads = activeDownloads,
                        localPlaylists = localPlaylists,
                        resolvedAlbum = resolvedAlbum,
                        onQueryChange = vm::onQueryChange,
                        onFocusChanged = vm::onFocusChanged,
                        onFilterSelect = vm::onFilterSelect,
                        onSubmit = vm::onSubmit,
                        onRefresh = vm::onRefresh,
                        onRemoveHistory = vm::onRemoveHistory,
                        onClearHistory = vm::onClearHistory,
                        onLoadMore = vm::onLoadMore,
                        onHitClick = vm::onHitClick,
                        onPlayNextClick = vm::onPlayNextClick,
                        onAddToQueueClick = vm::onAddToQueueClick,
                        onToggleFavorite = vm::onToggleFavorite,
                        onAddToPlaylist = vm::onAddToPlaylist,
                        onDownloadTrack = vm::onDownloadTrack,
                        onRemoveDownload = vm::onRemoveDownload,
                        onOpenDownloadsScreen = { navController.navigate(Routes.Downloads) },
                        onPrepareTrackMenu = menuVm::resolveAlbumFor,
                        onOpenAlbum = { album -> navController.openAlbum(album) },
                        onOpenArtistByName = vm::onOpenArtistByName,
                        onTrackInfoClick = { navController.navigateToTrackInfo(it) },
                    )
                }

                composable<Routes.MediaList> {
                    val vm: MediaListViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    val playlistId = state.listId
                    val menuCtx = when {
                        state.isAlbum -> TrackMenuContext.DEFAULT
                        state.isLocal || isLocalPlaylistId(playlistId) -> TrackMenuContext.LOCAL_PLAYLIST
                        else -> TrackMenuContext.EXTERNAL_PLAYLIST
                    }

                    LaunchedEffect(Unit) {
                        vm.navigationEvent.collect { event ->
                            when (event) {
                                is MediaListViewModel.NavigationEvent.OpenArtist -> navController.navigate(
                                    Routes.Artist(event.artist.id, event.artist.name, event.artist.thumbnailUrl)
                                )
                                MediaListViewModel.NavigationEvent.CloseScreen -> navController.popBackStack()
                            }
                        }
                    }

                    MediaListScreen(
                        state = state,
                        activeDownloads = activeDownloads,
                        onBackClick = { navController.popBackStack() },
                        onTabSelected = vm::onTabSelected,
                        onTrackClick = vm::onTrackClick,
                        onPlayAllClick = vm::onPlayAll,
                        onShuffleClick = vm::onShuffle,
                        onSortSelected = vm::onSortOrderSelected,
                        onReorder = vm::onReorder,
                        onStartShuffle = vm::startShuffle,
                        onShuffleOnce = vm::shuffleOnce,
                        onStopShuffle = vm::stopShuffle,
                        onPlayNextClick = vm::onPlayNextClick,
                        onTrackInfoClick = { t ->
                            openTrackMenu(
                                targetTrack = t,
                                context = menuCtx,
                                localPlaylistId = playlistId.takeIf { menuCtx == TrackMenuContext.LOCAL_PLAYLIST },
                            )
                        },
                        onFavoriteClick = vm::onFavoriteClick,
                        onPlayNextAll = vm::onPlayNextAll,
                        onAddToQueueAll = vm::onAddToQueueAll,
                        onDownloadAll = vm::onDownloadAll,
                        onOpenDownloadsScreen = { navController.navigate(Routes.Downloads) },
                        onOpenArtistByName = vm::onOpenArtistByName,
                        onOpenArtist = vm::onOpenArtist,
                        onAlbumClick = { album ->
                            if (album.id != state.listId) navController.openAlbum(album)
                        },
                        onDeleteLocalPlaylist = vm::onDeleteLocalPlaylist,
                        onRenameLocalPlaylist = vm::onRenameLocalPlaylist,
                        onChangeLocalPlaylistCover = vm::onChangeLocalPlaylistCover,
                    )
                }

                composable<Routes.Artist> {
                    val vm: ArtistViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    ArtistScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onRandomClick = vm::onRandomClick,
                        onFollowClick = vm::onFollowClick,
                        onSongClick = vm::onSongClick,
                        onVideoClick = vm::onVideoClick,
                        onTrackInfoClick = { t -> openTrackMenu(t, context = TrackMenuContext.DEFAULT) },
                        onAlbumClick = { navController.openAlbum(it) },
                        onPlaylistClick = { navController.openPlaylist(it) },
                        onArtistClick = { artist ->
                            navController.navigate(Routes.Artist(artist.id, artist.name, artist.thumbnailUrl))
                        },
                        onSeeAllClick = { kind, ref ->
                            navController.navigate(
                                Routes.ArtistList(
                                    artistId = state.artistId,
                                    artistName = state.name,
                                    kind = kind.name,
                                    browseId = ref.browseId,
                                    params = ref.params,
                                )
                            )
                        },
                    )
                }

                composable<Routes.ArtistList> {
                    val vm: ArtistListViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    ArtistListScreen(
                        state = state,
                        onBackClick = { navController.popBackStack() },
                        onLoadMore = vm::onLoadMore,
                        onTrackClick = vm::onTrackClick,
                        onTrackInfoClick = { t -> openTrackMenu(t, context = TrackMenuContext.DEFAULT) },
                        onAlbumClick = { navController.openAlbum(it) },
                        onPlaylistClick = { navController.openPlaylist(it) },
                    )
                }

                composable<Routes.TrackInfo> {
                    val vm: TrackInfoViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    TrackInfoScreen(
                        state = state,
                        isDownloaded = state.isDownloaded,
                        onBackClick = { navController.popBackStack() },
                        onFavoriteClick = vm::onFavoriteClick,
                        onAddToPlaylist = vm::onAddToPlaylist,
                        onDownloadClick = {
                            if (state.isDownloaded) vm.onRemoveDownloadClick() else vm.onDownloadClick()
                        },
                    )
                }
            }

            PlaybackChrome(
                navController = navController,
                currentTab = currentTab,
                playback = playback,
                track = track,
                dominantColor = dominantColor,
                miniPlayerVm = miniPlayerVm,
                isMinimized = isMinimized,
                isDetailScreen = isDetailScreen,
                isTrackInfoScreen = isTrackInfoScreen,
                bannerTrack = bannerTrack,
                onDismissBanner = bannerVm::dismissBanner,
                onViewDownloadsClick = { navController.navigate(Routes.Downloads) },
            )
        }
    }
}

@Composable
private fun Placeholder(message: String) {
    Box(Modifier.fillMaxSize()) {
        Text(text = message, color = VzOnSurface, modifier = Modifier.align(Alignment.Center))
        AppCredits(Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }
}
VZ_EOF_8_FIX4

echo ""
echo "Pronto! FIX4 aplicado."
echo " - Album tocando: equalizador identico aos cards normais (3 barras, 20dp)"
echo " - Artista: Levenshtein SimpMusic + verifica se tem faixa/album no catalogo, Topic perde pro oficial"
echo "Roda o build agora."
