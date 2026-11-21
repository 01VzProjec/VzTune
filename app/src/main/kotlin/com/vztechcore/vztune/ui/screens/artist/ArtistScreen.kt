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
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import com.vztechcore.vztune.R
import com.vztechcore.vztune.ui.components.layout.SectionHeader
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.playbackOf
import com.vztechcore.vztune.ui.screens.medialist.CollapsedHeaderHost
import com.vztechcore.vztune.ui.screens.medialist.MediaListCollapsedHeader
import com.vztechcore.vztune.ui.screens.medialist.MediaListVideoCard
import com.vztechcore.vztune.ui.screens.medialist.rememberHeaderCollapsed
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.ArrowForward
import com.vztechcore.vztune.ui.icons.actions.ChevronRight
import com.vztechcore.vztune.ui.icons.player.Play
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.vztechcore.vztune.ui.icons.actions.ArrowBackIos
import com.vztechcore.vztune.ui.icons.actions.Check
import com.vztechcore.vztune.ui.screens.medialist.MediaListCircleButton
import androidx.compose.foundation.layout.statusBarsPadding
import com.vztechcore.vztune.ui.icons.actions.PersonAdd
import com.vztechcore.vztune.ui.icons.actions.PersonCheck
import com.vztechcore.vztune.ui.icons.actions.Share

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
    onLoadMoreChannelVideos: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val isCollapsed by rememberHeaderCollapsed(listState)
    val nowPlaying = LocalNowPlaying.current
    var selectedChannelTab by remember { mutableStateOf(0) }
    var channelSort by remember { mutableStateOf(0) } // 0 = Mais recentes, 1 = Em alta, 2 = Mais antigos
    var channelDescExpanded by remember { mutableStateOf(false) }
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
                if (state.isYouTubeChannel) {
                    ChannelHeader(
                        state = state,
                        selectedTab = selectedChannelTab,
                        onTabSelected = { selectedChannelTab = it },
                        descExpanded = channelDescExpanded,
                        onDescClick = { channelDescExpanded = !channelDescExpanded },
                        onBackClick = onBackClick,
                        onFollowClick = onFollowClick,
                        onShareClick = onShareClick,
                    )
                } else {
                    ArtistHeader(
                        state = state,
                        accent = Color.Black,
                        onBackClick = onBackClick,
                        onRandomClick = onRandomClick,
                        onFollowClick = onFollowClick,
                        onShareClick = onShareClick,
                    )
                }
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
                    // FIX14 REBUILD - Regra: tem musica = ARTISTA (so Home, sem abas). Sem musica = CANAL (abas Videos | Shorts | Playlists).
                    val hasMusic = state.topSongs.isNotEmpty() || state.albums.isNotEmpty() || state.singles.isNotEmpty()
                    val isChannel = !hasMusic

                    if (isChannel) {
                        // ---------- CANAL: Videos | Shorts | Playlists ----------
                        val sortedVideos = when (channelSort) {
                            1 -> state.videos.sortedByDescending { it.viewCount ?: 0 }
                            2 -> state.videos.reversed()
                            else -> state.videos
                        }
                        if (selectedChannelTab == 0) {
                            item(key = "channel_chips") {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Sem forEach: weight() so existe direto dentro do Row.
                                    val chips = listOf("Mais recentes", "Em alta", "Mais antigos")
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (channelSort == 0) Color.White else Color.White.copy(alpha = 0.12f)).clickable { channelSort = 0 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(text = chips[0], style = MaterialTheme.typography.labelLarge, color = if (channelSort == 0) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (channelSort == 1) Color.White else Color.White.copy(alpha = 0.12f)).clickable { channelSort = 1 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(text = chips[1], style = MaterialTheme.typography.labelLarge, color = if (channelSort == 1) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (channelSort == 2) Color.White else Color.White.copy(alpha = 0.12f)).clickable { channelSort = 2 }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(text = chips[2], style = MaterialTheme.typography.labelLarge, color = if (channelSort == 2) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            item(key = "channel_chips_space") { Spacer(Modifier.height(8.dp)) }
                        }
                        when (selectedChannelTab) {
                            0 -> {
                                if (sortedVideos.isNotEmpty()) {
                                    itemsIndexed(sortedVideos, key = { _, v -> "cv_" + v.id }) { _, video -> ChannelVideoCard(track = video, onClick = { onVideoClick(video) }, onMoreClick = { onTrackInfoClick(video) }) }
                                } else {
                                    item(key = "channel_videos_empty") { Text(text = "Nenhum vídeo ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                                }
                            }
                            1 -> {
                                if (state.shorts.isNotEmpty()) {
                                    item(key = "channel_shorts_grid") { ShortsGridRows(tracks = state.shorts, onClick = { onShortClick(it) }, onMoreClick = { onTrackInfoClick(it) }) }
                                } else {
                                    item(key = "channel_shorts_empty") { Text(text = "Nenhum Short ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                                }
                            }
                            else -> {
                                if (state.channelPlaylists.isNotEmpty()) {
                                    itemsIndexed(state.channelPlaylists, key = { _, pl -> "cp_" + pl.id }) { _, playlist -> ChannelPlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) }, onMoreClick = { playlistMenu = playlist }) }
                                } else {
                                    item(key = "channel_playlists_empty") { Text(text = "Nenhuma playlist ainda", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) }
                                }
                            }
                        }
                                                // FIX18 - Buscar mais: so em Mais recentes e Em alta. Mais antigos nao tem botao, mas se atualiza sozinho porque usa a mesma lista (videos) invertida.
                        if (selectedChannelTab == 0 && state.channelVideosHasMore && channelSort != 2) {
                            item(key = "channel_load_more") {
                                Box(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable { onLoadMoreChannelVideos() }.padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (state.isLoadingMoreChannelVideos) "Carregando..." else "Buscar mais",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
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
                        // 3) Destacado: fileira + > (featured do YT Music - playlists onde o artista aparece)
                        val artistPlaylists = state.featured
                        if (artistPlaylists.isNotEmpty()) {
                            item(key = "playlists_header") {
                                SectionHeaderArrow(title = "Destacado", onArrowClick = seeAll(ArtistListKind.FEATURED, state.featuredMore))
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
                                                // 6) Artistas relacionados: foto, nome, ouvintes mensais
                        if (state.relatedArtists.isNotEmpty()) {
                            item(key = "related_header") {
                                SectionHeaderArrow(title = "Artistas relacionados")
                            }
                            item(key = "related_row") {
                                LazyRow(contentPadding = RowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(state.relatedArtists, key = { it.id }) { artist ->
                                        RelatedArtistCard(artist = artist, onClick = { onArtistClick(artist) })
                                    }
                                }
                            }
                            item(key = "related_space") { Spacer(Modifier.height(SectionGap)) }
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
                onShareClick = onShareClick,
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
                Icon(VzIcons.ChevronRight, contentDescription = "Ver tudo", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * Cabecalho do canal igual a foto do YouTube, mas no tema VzTune (preto AMOLED).
 * Banner 16:9 arredondado, avatar redondo, nome + selo, @handle, inscritos • videos,
 * descricao com ...mais, botao Seguir (mesmo sistema do artista),
 * abas Videos | Shorts | Playlists.
 */
@Composable
private fun ChannelHeader(
    state: ArtistUiState,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    descExpanded: Boolean,
    onDescClick: () -> Unit,
    onBackClick: () -> Unit,
    onFollowClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    val handle = "@" + state.name.replace(" ", "").lowercase()
    val subsVideos = listOfNotNull(
        state.subscribers?.takeIf { it.isNotBlank() },
        if (state.videos.isNotEmpty()) state.videos.size.toString() + " vídeos" else null,
    ).joinToString(" • ")
    val tabs = listOf("Vídeos", "Shorts", "Playlists")
    Column(
        Modifier.fillMaxWidth().background(Color.Black)
    ) {
        // Barra de cima: voltar | compartilhar. Desce pra nao ficar em cima da barra de infos do celular.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaListCircleButton(VzIcons.ArrowBackIos, "Voltar", onBackClick)
            MediaListCircleButton(VzIcons.Share, "Compartilhar", onShareClick)
        }
        // FIX18 - Banner: so aparece se o criador tem banner. Sem banner = sem card vazio. Tamanho exato do criador: sem aspectRatio fixo, a imagem manda na altura (FillWidth).
        if (!state.bannerUrl.isNullOrBlank()) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(model = state.bannerUrl, contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
            }
        }
        // Avatar + nome + @ + inscritos
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(80.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                if (!state.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(model = state.thumbnailUrl, contentDescription = state.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(text = state.name, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                Spacer(Modifier.height(4.dp))
                Text(text = handle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subsVideos.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(text = subsVideos, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // Descricao com ...mais (clica pra expandir)
        if (!state.description.isNullOrBlank()) {
            val shown = if (descExpanded) state.description else state.description.take(110) + if (state.description.length > 110) "...mais" else ""
            Text(
                text = shown,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = if (descExpanded) 10 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onDescClick() },
            )
            Spacer(Modifier.height(12.dp))
        }
        // So Seguir, mesmo sistema do artista: Seguir / Seguindo, troca com onFollowClick (isFollowing).
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val followShape = RoundedCornerShape(50)
            Row(
                Modifier.height(44.dp).clip(followShape).background(if (state.isFollowing) Color.White.copy(alpha = 0.12f) else Color.Transparent).border(1.dp, Color.White.copy(alpha = 0.35f), followShape).clickable { onFollowClick() }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(imageVector = if (state.isFollowing) VzIcons.PersonCheck else VzIcons.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(text = if (state.isFollowing) "Seguindo" else "Seguir", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))
        // Abas igual a foto, com sublinhado na selecionada
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(tabs) { index, tab ->
                Column(Modifier.clickable { onTabSelected(index) }.padding(bottom = 8.dp)) {
                    Text(text = tab, style = MaterialTheme.typography.titleSmall, color = if (selectedTab == index) Color.White else Color.White.copy(alpha = 0.6f), fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (selectedTab == index) Color.White else Color.Transparent))
                }
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
    val pages = tracks.take(16).chunked(4)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pageWidth = maxWidth - 64.dp // FIX15: peek da próxima fileira (64.dp = 16 padding + ~48 da próxima fileira aparente)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(pages, key = { _, page -> page.first().id }) { pageIndex, page ->
                Column(Modifier.width(pageWidth)) {
                    page.forEach { track ->
                        TrackListRow(track = track, onClick = { onSongClick(track) }, onMoreClick = { onTrackInfoClick(track) })
                    }
                }
            }
        }
    }
}


/** FIX15: Relacionados - foto circular, nome, ouvintes mensais. Ex: Vtzin x / 1,55 mi ouvintes mensais. */
@Composable
private fun RelatedArtistCard(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(124.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MediaThumbnail(
            imageUrl = artist.thumbnailUrl,
            shape = ThumbnailShape.CIRCLE,
            size = 124.dp,
            placeholderRes = R.drawable.holder,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        val listeners = artist.subscribers?.takeIf { it.isNotBlank() }
        if (!listeners.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = listeners,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
