package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.ui.components.player.DefaultPlaylistCover
import com.vztechcore.vztune.ui.components.player.PlaylistCoverPalette
import com.vztechcore.vztune.ui.components.player.PlaylistMosaic
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.ArrowBack
import com.vztechcore.vztune.ui.icons.actions.FavoriteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteOutline
import com.vztechcore.vztune.ui.icons.actions.MoreVert
import com.vztechcore.vztune.ui.icons.actions.Search
import com.vztechcore.vztune.ui.icons.media.Artist
import com.vztechcore.vztune.ui.icons.player.PlayCircleFilled
import com.vztechcore.vztune.ui.icons.player.Shuffle

/**
 * Header for playlist / album screens.
 *
 * Background rule: the header uses the same solid dominant color as the
 * list background. No gradient to black, no bottom fade. The only dark
 * elements are the small circular buttons, which already have their own
 * translucent dark background.
 */
@Composable
internal fun MediaListExpandedHeader(
    state: MediaListUiState,
    dominantColor: Color,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMoreClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onHeaderArtistClick: () -> Unit,
    onPlayNextAll: () -> Unit,
    onAddToQueueAll: () -> Unit,
    onDownloadAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Text color that stays readable on bright solid backgrounds.
    val contentColor = remember(dominantColor) {
        if (dominantColor.luminance() > 0.60f) Color(0xFF0B0D12) else Color.White
    }
    val secondaryColor = contentColor.copy(alpha = 0.75f)

    Column(
        modifier
            .fillMaxWidth()
            .background(dominantColor)
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MediaListCircleButton(VzIcons.ArrowBack, "Voltar", onBackClick)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MediaListCircleButton(VzIcons.Search, "Pesquisar", onSearchClick)
                MediaListCircleButton(VzIcons.MoreVert, "Mais", onMoreClick)
            }
        }

        // Cover - no overlay gradient on top of it
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                val isColorToken = PlaylistCoverPalette.isDefaultColorToken(state.thumbnailUrl)
                when {
                    state.isLocal && isColorToken -> {
                        DefaultPlaylistCover(seed = state.listId, coverPath = state.thumbnailUrl)
                    }
                    state.isLocal && !state.thumbnailUrl.isNullOrBlank() -> {
                        AsyncImage(
                            model = state.thumbnailUrl,
                            contentDescription = state.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    state.mosaicThumbnails.size >= 4 -> {
                        PlaylistMosaic(state.mosaicThumbnails)
                    }
                    state.isLocal -> {
                        DefaultPlaylistCover(seed = state.listId, coverPath = state.thumbnailUrl)
                    }
                    !state.thumbnailUrl.isNullOrBlank() -> {
                        AsyncImage(
                            model = state.thumbnailUrl,
                            contentDescription = state.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = VzIcons.Artist,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(80.dp),
                        )
                    }
                }
            }
        }

        // Title + subtitle, below the cover, not over it
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            if (!state.subtitle.isNullOrBlank()) {
                Text(
                    text = state.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onHeaderArtistClick() }
                        .padding(vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = state.trackCountLabel,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor,
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Play button
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onPlayAllClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VzIcons.PlayCircleFilled,
                    contentDescription = "Tocar",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp),
                )
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable { onShuffleClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = VzIcons.Shuffle,
                    contentDescription = "Aleatório",
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.showFavorite) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onFavoriteClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (state.isFavorite) VzIcons.FavoriteFilled else VzIcons.FavoriteOutline,
                        contentDescription = if (state.isFavorite) "Desfavoritar" else "Favoritar",
                        tint = if (state.isFavorite) Color(0xFFF43F5E) else contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

/**
 * Collapsed top bar shown after scrolling past the hero.
 * It uses the same solid dominant color, no gradient.
 */
@Composable
internal fun MediaListCollapsedHeader(
    title: String,
    thumbnailUrl: String?,
    dominantColor: Color,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = remember(dominantColor) {
        if (dominantColor.luminance() > 0.60f) Color(0xFF0B0D12) else Color.White
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(dominantColor)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaListCircleButton(VzIcons.ArrowBack, "Voltar", onBackClick)
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            if (PlaylistCoverPalette.isDefaultColorToken(thumbnailUrl)) {
                DefaultPlaylistCover(seed = title, coverPath = thumbnailUrl)
            } else if (!thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MediaListCircleButton(VzIcons.Search, "Pesquisar", onSearchClick)
        Spacer(Modifier.width(8.dp))
        MediaListCircleButton(VzIcons.MoreVert, "Mais", onMoreClick)
    }
}
