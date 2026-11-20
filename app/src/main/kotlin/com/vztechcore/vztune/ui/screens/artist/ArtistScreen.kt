package com.vztechcore.vztune.ui.screens.artist

import androidx.compose.animation.animateContentSize
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.ArtistListKind
import com.vztechcore.vztune.core.domain.music.BrowseRef
import com.vztechcore.vztune.core.domain.music.Playlist
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.cards.ArtistCircleCard
import com.vztechcore.vztune.ui.components.cards.CoverCard
import com.vztechcore.vztune.ui.components.cards.TrackListRow
import com.vztechcore.vztune.ui.components.cards.ChannelPlaylistCard
import com.vztechcore.vztune.ui.components.cards.ChannelVideoCard
import com.vztechcore.vztune.ui.components.cards.PlaylistOptionsSheet
import com.vztechcore.vztune.ui.components.cards.ShortsGridRows
import com.vztechcore.vztune.ui.components.cards.ShortsOptionsSheet
import com.vztechcore.vztune.data.prefs.models.StoredTrack
import com.vztechcore.vztune.ui.components.feedback.VzLoading
import com.vztechcore.vztune.ui.components.layout.SectionHeader
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.playbackOf
import com.vztechcore.vztune.ui.screens.medialist.CollapsedHeaderHost
import com.vztechcore.vztune.ui.screens.medialist.MediaListCollapsedHeader
import com.vztechcore.vztune.ui.screens.medialist.MediaListVideoCard
import com.vztechcore.vztune.ui.screens.medialist.rememberHeaderCollapsed

private val RowPadding = PaddingValues(horizontal = 16.dp)
private val CoverWidth = 160.dp
private val VideoWidth = 280.dp
private val RelatedArtistSize = 124.dp
private val SectionGap = 24.dp

/**
 * Artist profile. Sections (hidden when empty), in order:
 * popular songs, albums, singles, videos, featured, related artists, description, credits.
 * The whole screen sits on a backdrop tinted with the photo color; once the hero scrolls away,
 * a sticky bar (close + photo + name) fades in.
 */
@Composable
fun ArtistScreen(
    state: ArtistUiState,
    onBackClick: () -> Unit,
    onRandomClick: () -> Unit,
    onFollowClick: () -> Unit,
    onSongClick: (Track) -> Unit,
    onVideoClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onSeeAllClick: (ArtistListKind, BrowseRef) -> Unit,
    favoriteKeys: Set<String> = emptySet(),
    onShortClick: (Track) -> Unit = onVideoClick,
    onToggleFavorite: (Track) -> Unit = {},
    onPlaylistPlayNext: (Playlist) -> Unit = {},
    onPlaylistAddToQueue: (Playlist) -> Unit = {},
    onSavePlaylistToLibrary: (Playlist) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val isCollapsed by rememberHeaderCollapsed(listState)
    val nowPlaying = LocalNowPlaying.current
    var selectedChannelTab by remember { mutableStateOf(0) }
    var shortMenuTrack by remember { mutableStateOf<Track?>(null) }
    var playlistMenu by remember { mutableStateOf<Playlist?>(null) }

    // Fundo sempre preto AMOLED - camaleao desativado por enquanto

    fun seeAll(kind: ArtistListKind, ref: BrowseRef?): (() -> Unit)? =
        ref?.let { { onSeeAllClick(kind, it) } }

    val context = LocalContext.current
    // Compartilhar artista: abre a barra de compartilhar do Android com link do YouTube Music
    val onShareClick: () -> Unit = {
        val url = if (state.isYouTubeChannel) "https://www.youtube.com/channel/${state.artistId}" else "https://music.youtube.com/browse/${state.artistId}"
        val shareText = "Confere esse artista no VzTune 🎧\n${state.name}\n$url"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, state.name)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Compartilhar artista"))
    }

    shortMenuTrack?.let { st ->
        val sk = StoredTrack.from(st).key
        ShortsOptionsSheet(track = st, isFavorite = sk in favoriteKeys, onToggleFavorite = onToggleFavorite, onDismiss = { shortMenuTrack = null })
    }
    playlistMenu?.let { pl ->
        PlaylistOptionsSheet(playlist = pl, onDismiss = { playlistMenu = null }, onPlayNext = { onPlaylistPlayNext(it); playlistMenu = null }, onAddToQueue = { onPlaylistAddToQueue(it); playlistMenu = null }, onSaveToLibrary = { onSavePlaylistToLibrary(it); playlistMenu = null }, onShare = { p -> val url = "https://www.youtube.com/playlist?list=" + p.id; val si = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, p.title + "\n" + url) }; context.startActivity(Intent.createChooser(si, "Compartilhar playlist")); playlistMenu = null })
    }
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 140.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                ArtistHeader(
                    state = state,
                    accent = Color.Black,
                    onBackClick = onBackClick,
                    onRandomClick = onRandomClick,
                    onFollowClick = onFollowClick,
                    onShareClick = onShareClick,
                )
            }

            when {
                state.isLoading -> item(key = "loading") {
                    Box(
                        Modifier.fillMaxWidth().height(280.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        VzLoading(Modifier.size(160.dp))
                    }
                }

                state.errorMessage != null -> item(key = "error") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.errorMessage,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                else -> {
                    // ---------- Abas UNIFICADAS: mesma para Artista e Canal ----------
                    // Nao separa mais por isYouTubeChannel. A pagina e uma so, o que muda e o conteudo que a conta tem.
                    // Ordem: Inicio | Musicas | Albuns & Singles | Playlists | Videos | Shorts
                    // Aba so aparece se tiver conteudo, menos Inicio que sempre aparece.
                    val hasAlbumsSingles = state.albums.isNotEmpty() || state.singles.isNotEmpty()
                    val allPlaylists = (state.channelPlaylists + state.featured).distinctBy { it.id }
                    val tabs = listOf(
                        "home" to "Início",
                        "songs" to "Músicas",
                        "albums" to "Álbuns & Singles",
                        "playlists" to "Playlists",
                        "videos" to "Vídeos",
                        "shorts" to "Shorts",
                    ).filter { (key, _) ->
                        when (key) {
                            "home" -> true
                            "songs" -> state.topSongs.isNotEmpty()
                            "albums" -> hasAlbumsSingles
                            "playlists" -> allPlaylists.isNotEmpty()
                            "videos" -> state.videos.isNotEmpty()
                            "shorts" -> state.shorts.isNotEmpty()
                            else -> true
                        }
                    }
                    val safeTabIndex = selectedChannelTab.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
                    val selectedKey = tabs.getOrNull(safeTabIndex)?.first ?: "home"

                    // Barrinha colada no header, antes de qualquer lista
                    item(key = "unified_tabs") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(tabs, key = { it.first }) { (key, label) ->
                                val sel = key == selectedKey
                                Box(
                                    Modifier.clip(RoundedCornerShape(50)).background(if (sel) Color.White else Color.White.copy(alpha = 0.08f)).clickable { selectedChannelTab = tabs.indexOfFirst { it.first == key } }.padding(horizontal = 18.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(text = label, style = MaterialTheme.typography.labelLarge, color = if (sel) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    item(key = "unified_tabs_space") { Spacer(Modifier.height(16.dp)) }

                    when (selectedKey) {
                        "songs" -> {
                            item(key = "songs_tab_header") {
                                SectionHeader(title = "Músicas", onActionClick = seeAll(ArtistListKind.SONGS, state.songsMore))
                            }
                            itemsIndexed(state.topSongs, key = { _, t -> "song_${t.id}" }) { index, track ->
                                TrackListRow(track = track, number = index + 1, onClick = { onSongClick(track) }, onMoreClick = { onTrackInfoClick(track) })
                            }
                            item(key = "songs_tab_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        "albums" -> {
                            rowSection(
                                key = "albums_tab", title = "Álbuns", items = state.albums,
                                onMoreClick = seeAll(ArtistListKind.ALBUMS, state.albumsMore),
                                itemKey = { it.id },
                            ) { album ->
                                CoverCard(title = album.title, subtitle = "Álbum", imageUrl = album.thumbnailUrl, onClick = { onAlbumClick(album) }, modifier = Modifier.width(CoverWidth))
                            }
                            rowSection(
                                key = "singles_tab", title = "Singles", items = state.singles,
                                onMoreClick = seeAll(ArtistListKind.SINGLES, state.singlesMore),
                                itemKey = { it.id },
                            ) { single ->
                                CoverCard(title = single.title, subtitle = "Single", imageUrl = single.thumbnailUrl, onClick = { onAlbumClick(single) }, modifier = Modifier.width(CoverWidth))
                            }
                        }
                        "playlists" -> {
                            itemsIndexed(allPlaylists, key = { _, pl -> "pl_${pl.id}" }) { _, playlist ->
                                ChannelPlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) }, onMoreClick = { playlistMenu = playlist })
                            }
                            item(key = "playlists_tab_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        "videos" -> {
                            itemsIndexed(state.videos, key = { _, v -> "cv_${v.id}" }) { _, video ->
                                ChannelVideoCard(track = video, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) })
                            }
                            item(key = "videos_tab_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        "shorts" -> {
                            if (state.shorts.isNotEmpty()) {
                                item(key = "shorts_tab") { ShortsGridRows(tracks = state.shorts, onClick = { onShortClick(it) }, onMoreClick = { shortMenuTrack = it }) }
                            }
                            item(key = "shorts_tab_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        else -> {
                            // ---------- Início (Tudo): igual era antes, sem separar canal/artista ----------
                            if (state.topSongs.isNotEmpty()) {
                                item(key = "songs_header") {
                                    SectionHeader(title = "Músicas populares", onActionClick = seeAll(ArtistListKind.SONGS, state.songsMore))
                                }
                                itemsIndexed(state.topSongs.take(5), key = { _, t -> "song_${t.id}" }) { index, track ->
                                    TrackListRow(track = track, number = index + 1, onClick = { onSongClick(track) }, onMoreClick = { onTrackInfoClick(track) })
                                }
                                item(key = "songs_space") { Spacer(Modifier.height(SectionGap)) }
                            }
                            rowSection(
                                key = "albums", title = "Álbuns", items = state.albums,
                                onMoreClick = seeAll(ArtistListKind.ALBUMS, state.albumsMore),
                                itemKey = { it.id },
                            ) { album ->
                                CoverCard(title = album.title, subtitle = "Álbum", imageUrl = album.thumbnailUrl, onClick = { onAlbumClick(album) }, modifier = Modifier.width(CoverWidth))
                            }
                            rowSection(
                                key = "singles", title = "Singles", items = state.singles,
                                onMoreClick = seeAll(ArtistListKind.SINGLES, state.singlesMore),
                                itemKey = { it.id },
                            ) { single ->
                                CoverCard(title = single.title, subtitle = "Single", imageUrl = single.thumbnailUrl, onClick = { onAlbumClick(single) }, modifier = Modifier.width(CoverWidth))
                            }
                            rowSection(
                                key = "videos", title = "Vídeos", items = state.videos,
                                onMoreClick = seeAll(ArtistListKind.VIDEOS, state.videosMore),
                                itemKey = { it.id },
                            ) { video ->
                                val playback = nowPlaying.playbackOf(video)
                                MediaListVideoCard(track = video, isPlaying = playback.isPlaying, isPaused = playback.isPaused, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }, modifier = Modifier.width(VideoWidth), horizontalPadding = 0.dp)
                            }
                            rowSection(
                                key = "shorts", title = "Shorts", items = state.shorts,
                                onMoreClick = null,
                                itemKey = { it.id },
                            ) { short ->
                                val playback = nowPlaying.playbackOf(short)
                                MediaListVideoCard(track = short, isPlaying = playback.isPlaying, isPaused = playback.isPaused, onClick = { onVideoClick(short) }, onMoreClick = { onTrackInfoClick(short) }, modifier = Modifier.width(160.dp), horizontalPadding = 0.dp)
                            }
                            rowSection(
                                key = "channelPlaylists", title = "Playlists", items = state.channelPlaylists,
                                onMoreClick = null,
                                itemKey = { it.id },
                            ) { playlist ->
                                CoverCard(title = playlist.title, subtitle = playlist.owner, imageUrl = playlist.thumbnailUrl, onClick = { onPlaylistClick(playlist) }, modifier = Modifier.width(CoverWidth))
                            }
                            rowSection(
                                key = "featured", title = "Destacado", items = state.featured,
                                onMoreClick = seeAll(ArtistListKind.FEATURED, state.featuredMore),
                                itemKey = { it.id },
                            ) { playlist ->
                                CoverCard(title = playlist.title, subtitle = playlist.owner, imageUrl = playlist.thumbnailUrl, onClick = { onPlaylistClick(playlist) }, modifier = Modifier.width(CoverWidth))
                            }
                            rowSection(
                                key = "related", title = "Artistas relacionados", items = state.relatedArtists,
                                onMoreClick = null, spacing = 16.dp,
                                itemKey = { it.id },
                            ) { artist ->
                                ArtistCircleCard(name = artist.name, imageUrl = artist.thumbnailUrl, onClick = { onArtistClick(artist) }, size = RelatedArtistSize)
                            }
                            item(key = "description") { ArtistDescription(description = state.description) }
                        }
                    }
                }
            }

            item(key = "credits") { AppCredits() }
        }

        CollapsedHeaderHost(visible = isCollapsed) {
            MediaListCollapsedHeader(
                title = state.name,
                thumbnailUrl = state.thumbnailUrl,
                onBackClick = onBackClick,
                dominantColor = Color.Black,
                circularThumbnail = true,
            )
        }
    }
}

/** A titled horizontal row ("Álbuns", "Vídeos"...). Nothing is emitted when [items] is empty. */
private fun <T> LazyListScope.rowSection(
    key: String,
    title: String,
    items: List<T>,
    onMoreClick: (() -> Unit)?,
    itemKey: (T) -> Any,
    spacing: Dp = 12.dp,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return

    item(key = "${key}_header") { SectionHeader(title = title, onActionClick = onMoreClick) }
    item(key = "${key}_row") {
        LazyRow(
            contentPadding = RowPadding,
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            items(items, key = itemKey) { itemContent(it) }
        }
    }
    item(key = "${key}_space") { Spacer(Modifier.height(SectionGap)) }
}

/** "About" card: soft translucent surface, expands in place when the text is long. */
@Composable
private fun ArtistDescription(description: String?) {
    var expanded by remember { mutableStateOf(false) }
    val text = description?.takeIf { it.isNotBlank() }
    val canExpand = text != null && text.length > 220

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .animateContentSize()
            .clickable(enabled = canExpand) { expanded = !expanded }
            .padding(16.dp)
    ) {
        Text(
            text = "Sobre",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = text ?: "Sem descrição",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = if (text != null) 0.75f else 0.5f),
            maxLines = if (expanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
        )
        if (canExpand) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (expanded) "Ver menos" else "Ver mais",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
