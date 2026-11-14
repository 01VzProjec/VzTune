package com.vztechcore.vztune.ui.screens.library.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.AppCredits
import com.vztechcore.vztune.ui.components.player.LocalNowPlaying
import com.vztechcore.vztune.ui.components.player.MediaThumbnail
import com.vztechcore.vztune.ui.components.player.ThumbnailShape
import com.vztechcore.vztune.ui.components.text.TrackSubtitle
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.ArrowBack
import com.vztechcore.vztune.ui.icons.actions.MoreVert
import com.vztechcore.vztune.ui.icons.media.Download
import com.vztechcore.vztune.ui.icons.player.Play
import com.vztechcore.vztune.ui.icons.player.Shuffle
import com.vztechcore.vztune.ui.screens.library.LibraryShortcut

private val PlayBlue = Color(0xFF1D4ED8)
private val NavyBorder = Color(0xFF1E3A8A)

@Composable
fun LibraryListScreen(
    state: LibraryListUiState,
    onBackClick: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackInfoClick: (Track) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onShuffleClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onDownloadsHistoryClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isDownloads = state.shortcut == LibraryShortcut.DOWNLOADS
    val hasTracks = state.tracks.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Barra superior: Voltar + Título + [Ícone Download se Baixadas] + [Aleatório] + [Reproduzir]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = VzIcons.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Color.White,
                )
            }

            Text(
                text = state.shortcut.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Ícone de Download à esquerda do botão Aleatório na aba Baixadas
            if (isDownloads) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(1.dp, NavyBorder), CircleShape)
                        .clickable { onDownloadsHistoryClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VzIcons.Download,
                        contentDescription = "Histórico de downloads",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
            }

            if (hasTracks && state.shortcut != LibraryShortcut.FOLLOWED) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { onShuffleClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VzIcons.Shuffle,
                        contentDescription = "Aleatório",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }

                Spacer(Modifier.width(8.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PlayBlue)
                        .clickable { onPlayAllClick() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = VzIcons.Play,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Tocar",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (state.shortcut == LibraryShortcut.FOLLOWED) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.artists, key = { it.id }) { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onArtistClick(artist) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MediaThumbnail(
                            imageUrl = artist.thumbnailUrl,
                            shape = ThumbnailShape.CIRCLE,
                            size = 54.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item(key = "credits") { AppCredits() }
            }
        } else if (state.tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isDownloads) "Nenhuma faixa baixada ainda." else "Nenhuma faixa por aqui ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        } else {
            val nowPlaying = LocalNowPlaying.current
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.tracks, key = { "${it.source.name}_${it.id}" }) { track ->
                    val isCurrent = nowPlaying.isCurrent(track.id, track.source)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onTrackClick(track) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MediaThumbnail(
                            imageUrl = track.thumbnailUrl,
                            shape = ThumbnailShape.SQUARE,
                            size = 52.dp,
                            cornerRadius = 8.dp,
                            isPlaying = isCurrent && nowPlaying.isPlaying,
                            isPaused = isCurrent && !nowPlaying.isPlaying,
                        )
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
                        IconButton(onClick = { onTrackInfoClick(track) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = VzIcons.MoreVert,
                                contentDescription = "Mais",
                                tint = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
                item(key = "credits") { AppCredits() }
            }
        }
    }
}