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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.ArrowForward
import com.vztechcore.vztune.ui.icons.player.Play

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
    onPlayAllClick: () -> Unit = onRandomClick,
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
                    // FIX14 REBUILD - Regra: tem musica = ARTISTA (so Home, sem abas). Sem musica = CANAL (abas Videos | Playlists).
                    val hasMusic = state.topSongs.isNotEmpty() || state.albums.isNotEmpty() || state.singles.isNotEmpty()
                    val isChannel = !hasMusic

                    if (isChannel) {
                        // ---------- CANAL: abas Videos | Playlists (sem Shorts) ----------
                        item(key = "channel_tabs") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Vídeos", "Playlists").forEachIndexed { idx, label ->
                                    val sel = selectedChannelTab == idx
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (sel) Color.White else Color.Transparent).clickable { selectedChannelTab = idx }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(text = label, style = MaterialTheme.typography.labelLarge, color = if (sel) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        item(key = "channel_tabs_space") { Spacer(Modifier.height(16.dp)) }
                        if (selectedChannelTab == 0) {
                            if (state.videos.isNotEmpty()) {
                                itemsIndexed(state.videos, key = { _, v -> "cv_" + v.id }) { _, video -> ChannelVideoCard(track = video, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }) }
                            } else {
                                item(key = "channel_videos_empty") { Text(text = "Nenhum vídeo ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                            }
                        } else {
                            if (state.channelPlaylists.isNotEmpty()) {
                                itemsIndexed(state.channelPlaylists, key = { _, pl -> "cp_" + pl.id }) { _, playlist -> ChannelPlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) }, onMoreClick = { playlistMenu = playlist }) }
                            } else {
                                item(key = "channel_playlists_empty") { Text(text = "Nenhuma playlist ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                            }
                        }
                        item(key = "channel_space") { Spacer(Modifier.height(SectionGap)) }
                        item(key = "description") { ArtistDescription(description = state.description) }
                    } else {
                        // ---------- ARTISTA: so Home, sem abas ----------
                        // 1) Musicas populares: slide de 4 por pagina + Tocar tudo + >
                        if (state.topSongs.isNotEmpty()) {
                            item(key = "songs_header") {
                                SectionHeaderArrow(title = "Músicas populares", onPlayAllClick = onPlayAllClick, onArrowClick = seeAll(ArtistListKind.SONGS, state.songsMore))
                            }
                            item(key = "songs_slide") {
                                SongsSlide(tracks = state.topSongs, onSongClick = onSongClick, onTrackInfoClick = onTrackInfoClick)
                            }
                            item(key = "songs_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        // 2) Singles e EPs: fileira + > (se nao tem singles, mostra albuns pra nao perder os albuns)
                        val singlesEps = if (state.singles.isNotEmpty()) state.singles else state.albums
                        val singlesEpsMore = if (state.singles.isNotEmpty()) state.singlesMore else state.albumsMore
                        val singlesEpsKind = if (state.singles.isNotEmpty()) ArtistListKind.SINGLES else ArtistListKind.ALBUMS
                        if (singlesEps.isNotEmpty()) {
                            item(key = "singles_header") {
                                SectionHeaderArrow(title = "Singles e EPs", onArrowClick = seeAll(singlesEpsKind, singlesEpsMore))
                            }
                            item(key = "singles_row") {
                                LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(singlesEps, key = { it.id }) { album ->
                                        val sub = if (state.singles.isNotEmpty()) "Single" else "Álbum"
                                        CoverCard(title = album.title, subtitle = sub, imageUrl = album.thumbnailUrl, onClick = { onAlbumClick(album) }, modifier = Modifier.width(CoverWidth))
                                    }
                                }
                            }
                            item(key = "singles_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        // 3) Playlists: fileira + > (artista usa featured do YT Music)
                        val artistPlaylists = state.featured
                        if (artistPlaylists.isNotEmpty()) {
                            item(key = "playlists_header") {
                                SectionHeaderArrow(title = "Playlists", onArrowClick = seeAll(ArtistListKind.FEATURED, state.featuredMore))
                            }
                            item(key = "playlists_row") {
                                LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(artistPlaylists, key = { it.id }) { playlist ->
                                        CoverCard(title = playlist.title, subtitle = playlist.owner, imageUrl = playlist.thumbnailUrl, onClick = { onPlaylistClick(playlist) }, modifier = Modifier.width(CoverWidth))
                                    }
                                }
                            }
                            item(key = "playlists_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        // 4) Videos: fileira 16:9 + > (so videos do artista, do InnerTube)
                        if (state.videos.isNotEmpty()) {
                            item(key = "videos_header") {
                                SectionHeaderArrow(title = "Vídeos", onArrowClick = seeAll(ArtistListKind.VIDEOS, state.videosMore))
                            }
                            item(key = "videos_row") {
                                LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(state.videos, key = { it.id }) { video ->
                                        val playback = nowPlaying.playbackOf(video)
                                        MediaListVideoCard(track = video, isPlaying = playback.isPlaying, isPaused = playback.isPaused, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }, modifier = Modifier.width(VideoWidth), horizontalPadding = 0.dp)
                                    }
                                }
                            }
                            item(key = "videos_space") { Spacer(Modifier.height(SectionGap)) }
                        }
                        item(key = "description") { ArtistDescription(description = state.description) }
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


/** Header de secao com balao "Tocar tudo" (so em Musicas) + icone > pra ver tudo. Substitui o "Mais". */
@Composable
private fun SectionHeaderArrow(
    title: String,
    onPlayAllClick: (() -> Unit)? = null,
    onArrowClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (onPlayAllClick != null) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable { onPlayAllClick() }.padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(VzIcons.Play, contentDescription = "Tocar tudo", tint = Color.Black, modifier = Modifier.size(16.dp))
                    Text(text = "Tocar tudo", style = MaterialTheme.typography.labelLarge, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        if (onArrowClick != null) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable { onArrowClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(VzIcons.ArrowForward, contentDescription = "Ver tudo", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Slide de Musicas populares: 4 faixas por pagina, arrasta da direita pra esquerda pra proxima fileira de 4. */
@Composable
private fun SongsSlide(
    tracks: List<Track>,
    onSongClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
) {
    val pages = tracks.chunked(4)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pageWidth = maxWidth - 32.dp
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(pages, key = { _, page -> page.first().id }) { pageIndex, page ->
                Column(Modifier.width(pageWidth)) {
                    page.forEachIndexed { idx, track ->
                        val number = pageIndex * 4 + idx + 1
                        TrackListRow(track = track, number = number, onClick = { onSongClick(track) }, onMoreClick = { onTrackInfoClick(track) })
                    }
                }
            }
        }
    }
}
