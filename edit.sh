#!/bin/bash
set -e

# VzTune - Sistema de holders (holder.png 1:1 / holder_video.png 16:9)
# Rode na raiz do projeto VzTune. O holder fica POR BAIXO da thumb:
# aparece enquanto a thumb carrega, falha ou ainda nao existe.
# holder_light.png NAO e usado.

if [ ! -f "app/src/main/kotlin/com/vztechcore/vztune/ui/components/player/MediaThumbnail.kt" ]; then
  echo "ERRO: rode este script na raiz do projeto VzTune."
  exit 1
fi

# --- 1. app/src/main/kotlin/com/vztechcore/vztune/ui/components/player/MediaThumbnail.kt ---
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/player/MediaThumbnail.kt" <<'EOF'
package com.vztechcore.vztune.ui.components.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.R
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.player.Pause
import com.vztechcore.vztune.ui.theme.VzSecondary

enum class ThumbnailShape { SQUARE, CIRCLE, WIDE }

@Composable
fun MediaThumbnail(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    shape: ThumbnailShape = ThumbnailShape.SQUARE,
    isPlaying: Boolean = false,
    isPaused: Boolean = false,
    isLoading: Boolean = false,
    size: Dp = 56.dp,
    cornerRadius: Dp = 8.dp,
) {
    val clipShape: Shape = when (shape) {
        ThumbnailShape.SQUARE -> RoundedCornerShape(cornerRadius)
        ThumbnailShape.CIRCLE -> CircleShape
        ThumbnailShape.WIDE -> RoundedCornerShape(cornerRadius + 4.dp)
    }

    val baseModifier = when (shape) {
        ThumbnailShape.WIDE -> modifier.fillMaxWidth().aspectRatio(16f / 9f)
        else -> modifier.size(size)
    }

    // Holder por baixo da thumb: 1:1 (holder.png) p/ faixas e playlists,
    // 16:9 (holder_video.png) p/ videos. Aparece enquanto a thumb carrega,
    // falha ou ainda nao existe - no lugar do cinza.
    val holderRes = if (shape == ThumbnailShape.WIDE) R.drawable.holder_video else R.drawable.holder

    Box(
        modifier = baseModifier
            .clip(clipShape)
            .background(Color.Black),
    ) {
        Image(
            painter = painterResource(holderRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        when {
            isPlaying -> PlayingOverlayLarge(clipShape)
            isPaused -> PlayingOverlayPaused(clipShape)
            isLoading -> Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun PlayingOverlayLarge(clipShape: Shape) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(clipShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.15f),
                        VzSecondary.copy(alpha = 0.25f),
                        Color.Black.copy(alpha = 0.6f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        PlayingEqualizer(
            color = Color.White,
            barCount = 3,
            maxHeight = 20.dp,
            barWidth = 3.dp,
            gap = 3.dp,
        )
    }
}

@Composable
internal fun PlayingOverlayPaused(clipShape: Shape) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(clipShape)
            .background(Color.Black.copy(alpha = 0.5f)),
    ) {
        Icon(
            imageVector = VzIcons.Pause,
            contentDescription = "Pausado",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .size(24.dp),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp),
        ) {
            PlayingEqualizer(
                color = Color.White,
                barCount = 3,
                maxHeight = 8.dp,
                barWidth = 2.dp,
                gap = 2.dp,
            )
        }
    }
}

@Composable
fun PlayingEqualizer(
    color: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 3,
    maxHeight: Dp = 20.dp,
    barWidth: Dp = 3.dp,
    gap: Dp = 3.dp,
) {
    val anims = (0 until barCount).map { i ->
        val infinite = rememberInfiniteTransition(label = "eq$i")
        infinite.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 380 + i * 90,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar$i",
        )
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        anims.forEach { anim ->
            val h by anim
            Box(
                Modifier
                    .width(barWidth)
                    .height(maxHeight * h)
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(color),
            )
        }
    }
}
EOF

# --- 2. app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/CoverCard.kt ---
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/cards/CoverCard.kt" <<'EOF'
package com.vztechcore.vztune.ui.components.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.R
import com.vztechcore.vztune.ui.components.player.PlaylistMosaic
import com.vztechcore.vztune.ui.components.feedback.VzLogoStatic
import com.vztechcore.vztune.ui.components.player.DefaultPlaylistCover
import com.vztechcore.vztune.ui.components.player.PlaylistCoverPalette

/**
 * Square cover with title and subtitle below. Used for albums, singles, playlists
 * (artist screen, "see all" grids and the Library). A mosaic of 4+ thumbnails wins over [imageUrl].
 *
 * When there is neither a mosaic nor a cover (a brand-new local playlist with no
 * tracks and no chosen cover), the card is drawn with a black background and the
 * static VzTechCore logo — same identity shown on the loading screens.
 *
 * Enquanto a capa carrega/falha, o holder.png (1:1) fica por baixo no lugar do cinza.
 */
@Composable
fun CoverCard(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    mosaic: List<String> = emptyList(),
) {
    val isEmpty = mosaic.isEmpty() && imageUrl == null

    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            // Holder 1:1 por baixo da capa (mosaico ou imagem). Capa vazia
            // mostra o logo, capa de cor padrao ja tem o fundo dela.
            if (!isEmpty) {
                Image(
                    painter = painterResource(R.drawable.holder),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            when {
                mosaic.size >= 4 -> PlaylistMosaic(mosaic)
                PlaylistCoverPalette.isDefaultColorToken(imageUrl) -> DefaultPlaylistCover(
                    seed = title,
                    coverPath = imageUrl,
                    modifier = Modifier.fillMaxSize(),
                )
                imageUrl != null -> AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> VzLogoStatic(Modifier.fillMaxSize(0.9f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
EOF

# --- 3. app/src/main/kotlin/com/vztechcore/vztune/ui/components/player/PlaylistMosaic.kt ---
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/player/PlaylistMosaic.kt" <<'EOF'
package com.vztechcore.vztune.ui.components.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.vztechcore.vztune.R

/** 2x2 grid of thumbnails that fills its parent (size/clip are decided by the caller). */
@Composable
fun PlaylistMosaic(thumbnails: List<String>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize()) {
        MosaicColumn(thumbnails.getOrNull(0), thumbnails.getOrNull(2), Modifier.weight(1f))
        MosaicColumn(thumbnails.getOrNull(1), thumbnails.getOrNull(3), Modifier.weight(1f))
    }
}

@Composable
private fun MosaicColumn(top: String?, bottom: String?, modifier: Modifier) {
    Column(modifier) {
        MosaicCell(top, Modifier.weight(1f))
        MosaicCell(bottom, Modifier.weight(1f))
    }
}

@Composable
private fun MosaicCell(url: String?, modifier: Modifier) {
    // Holder 1:1 por baixo de cada celula: enquanto a thumb do mosaico
    // carrega/falha, aparece o holder no lugar do vazio.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black),
    ) {
        Image(
            painter = painterResource(R.drawable.holder),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
EOF

# --- 4. app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListHeader.kt ---
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/screens/medialist/MediaListHeader.kt" <<'EOF'
package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.R
import com.vztechcore.vztune.core.domain.search.SortOrder
import com.vztechcore.vztune.ui.components.player.DefaultPlaylistCover
import com.vztechcore.vztune.ui.components.player.PlaylistCoverPalette
import com.vztechcore.vztune.ui.components.player.PlaylistMosaic
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Close
import com.vztechcore.vztune.ui.icons.actions.FavoriteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteOutline
import com.vztechcore.vztune.ui.icons.actions.MoreVert
import com.vztechcore.vztune.ui.icons.actions.Sort
import com.vztechcore.vztune.ui.icons.actions.StopCircle
import com.vztechcore.vztune.ui.icons.actions.Sync
import com.vztechcore.vztune.ui.icons.media.DownloadForOffline
import com.vztechcore.vztune.ui.icons.navigation.Search
import com.vztechcore.vztune.ui.icons.player.Play
import com.vztechcore.vztune.ui.icons.player.Shuffle

@Composable
internal fun MediaListExpandedHeader(
    state: MediaListUiState,
    dominantColor: Color = Color.Black,
    allTracksDownloaded: Boolean = false,
    onBackClick: () -> Unit,
    onTabSelected: (MediaListTab) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMoreClick: () -> Unit,
    onSortClick: () -> Unit,
    onShuffleToggle: () -> Unit,
    onFavoriteClick: () -> Unit,
    onArtistClick: (() -> Unit)? = null,
) {
    // Sem camaleao - fade sempre pra preto AMOLED
    val chameleonBase = Color.Black

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
        ) {
            // Holder 1:1 por baixo da capa da playlist/album: aparece enquanto
            // a capa carrega/falha, no lugar do vazio.
            Image(
                painter = painterResource(R.drawable.holder),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            val hasDefaultColorToken = PlaylistCoverPalette.isDefaultColorToken(state.thumbnailUrl)
            when {
                state.isLocal && !hasDefaultColorToken && !state.thumbnailUrl.isNullOrBlank() -> {
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
                    DefaultPlaylistCover(
                        seed = state.listId,
                        coverPath = state.thumbnailUrl,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    AsyncImage(
                        model = state.thumbnailUrl,
                        contentDescription = state.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.42f),
                                0.48f to Color.Transparent,
                                0.82f to chameleonBase.copy(alpha = 0.78f),
                                1.0f to chameleonBase,
                            )
                        )
                    )
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaListCircleButton(VzIcons.Close, "Fechar", onBackClick)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Ordem igual as playlists: Lupa, Favoritar, 3 pontinhos
                    MediaListCircleButton(VzIcons.Search, "Pesquisar", onSearchClick)
                    if (state.showFavorite) {
                        MediaListCircleButton(
                            icon = if (state.isFavorite) VzIcons.FavoriteFilled else VzIcons.FavoriteOutline,
                            contentDescription = if (state.isFavorite) "Remover dos favoritos" else "Favoritar",
                            onClick = onFavoriteClick,
                        )
                    }
                    MediaListCircleButton(VzIcons.MoreVert, "Mais opções", onMoreClick)
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset(y = (-12).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            val ownerName = state.owner?.takeIf { it.isNotBlank() }
            if (!ownerName.isNullOrBlank()) {
                val isClickableOwner = !state.isLocal && onArtistClick != null
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .then(
                            if (isClickableOwner) {
                                Modifier
                                    .clickable { onArtistClick?.invoke() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            } else {
                                Modifier
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (!state.ownerAvatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = state.ownerAvatarUrl,
                            contentDescription = ownerName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = ownerName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.92f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            val statsText = if (state.isAlbum) {
                listOfNotNull(
                    "Álbum",
                    state.year?.takeIf { it.isNotBlank() },
                    state.trackCountLabel.takeIf { state.allTracks.isNotEmpty() },
                    state.totalDurationLabel.takeIf { it.isNotBlank() },
                ).joinToString(" • ")
            } else {
                listOfNotNull(
                    "Playlist",
                    state.year?.takeIf { it.isNotBlank() },
                    state.trackCountLabel.takeIf { (state.originalTrackCount ?: state.allTracks.size) > 0 },
                    state.totalDurationLabel.takeIf { it.isNotBlank() },
                ).joinToString(" • ")
            }

            if (statsText.isNotBlank()) {
                Text(
                    text = statsText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (state.showTabs) {
            MediaListTabs(state.selectedTab, onTabSelected)
            Spacer(Modifier.height(12.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1E40AF))
                    .clickable { onPlayAllClick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    VzIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Play",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(Modifier.width(8.dp))

            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable { onShuffleClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    VzIcons.Shuffle,
                    contentDescription = "Aleatório",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (allTracksDownloaded) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VzIcons.DownloadForOffline,
                        contentDescription = if (state.isAlbum) "Álbum baixado" else "Playlist baixada",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (state.isSortEnabled && state.sortOrder == SortOrder.SHUFFLE) {
                IconButton(onClick = onShuffleToggle) {
                    Icon(
                        imageVector = if (state.isShuffling) VzIcons.StopCircle else VzIcons.Sync,
                        contentDescription = "Sortear animado",
                        tint = if (state.isShuffling) Color(0xFF1E40AF) else Color.White,
                    )
                }
            }

            if (state.isSortEnabled) {
                IconButton(onClick = onSortClick) {
                    Icon(
                        VzIcons.Sort,
                        contentDescription = "Ordenar",
                        tint = Color.White,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun MediaListCollapsedHeader(
    title: String,
    thumbnailUrl: String?,
    onBackClick: () -> Unit,
    dominantColor: Color = Color.Black,
    onSearchClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    circularThumbnail: Boolean = false,
    showFavorite: Boolean = false,
    isFavorite: Boolean = false,
    onFavoriteClick: (() -> Unit)? = null,
) {
    val barTint = remember(dominantColor) {
        lerp(Color.Black, dominantColor, 0.38f)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        barTint.copy(alpha = 0.98f),
                        barTint.copy(alpha = 0.88f),
                        Color.Transparent,
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaListCircleButton(VzIcons.Close, "Fechar", onBackClick)
        Spacer(Modifier.width(12.dp))
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(if (circularThumbnail) CircleShape else RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onSearchClick != null) {
            IconButton(onClick = onSearchClick) {
                Icon(VzIcons.Search, contentDescription = "Pesquisar", tint = Color.White)
            }
        }
        // Colapsado também fica Lupa, Favoritar, 3 pontinhos (quando tem favoritar)
        if (showFavorite && onFavoriteClick != null) {
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (isFavorite) VzIcons.FavoriteFilled else VzIcons.FavoriteOutline,
                    contentDescription = if (isFavorite) "Remover dos favoritos" else "Favoritar",
                    tint = if (isFavorite) Color(0xFFEF4444) else Color.White,
                )
            }
        }
        if (onMoreClick != null) {
            IconButton(onClick = onMoreClick) {
                Icon(VzIcons.MoreVert, contentDescription = "Mais opções", tint = Color.White)
            }
        }
    }
}
EOF

echo "Holders aplicados: MediaThumbnail, CoverCard, PlaylistMosaic, MediaListHeader."
