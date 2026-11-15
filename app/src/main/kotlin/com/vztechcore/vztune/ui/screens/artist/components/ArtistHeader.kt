package com.vztechcore.vztune.ui.screens.artist.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.ui.components.feedback.rememberDominantColor
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.ArrowBack
import com.vztechcore.vztune.ui.icons.actions.PersonAdd
import com.vztechcore.vztune.ui.icons.actions.PersonCheck
import com.vztechcore.vztune.ui.icons.player.Shuffle
import com.vztechcore.vztune.ui.screens.artist.ArtistUiState

/**
 * Computes a full-screen chameleon background brush from the artist's photo.
 */
@Composable
fun rememberArtistChameleonBrush(thumbnailUrl: String?): Brush {
    val dominantColor = rememberDominantColor(thumbnailUrl)
    return remember(dominantColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to lerp(Color.Black, dominantColor, 0.65f),
                0.35f to lerp(Color.Black, dominantColor, 0.42f),
                0.70f to lerp(Color.Black, dominantColor, 0.24f),
                1.0f to lerp(Color.Black, dominantColor, 0.12f),
            )
        )
    }
}

@Composable
fun ArtistHeader(
    state: ArtistUiState,
    onBackClick: () -> Unit,
    onRandomClick: () -> Unit,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dominantColor = rememberDominantColor(state.thumbnailUrl)
    val hasPlayableItems = state.topSongs.isNotEmpty() || state.videos.isNotEmpty()

    val chameleonHeaderBrush = remember(dominantColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to lerp(Color.Black, dominantColor, 0.65f),
                0.65f to lerp(Color.Black, dominantColor, 0.45f),
                1.0f to lerp(Color.Black, dominantColor, 0.36f),
            )
        )
    }

    val chameleonBottomFade = remember(dominantColor) {
        lerp(Color.Black, dominantColor, 0.42f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(chameleonHeaderBrush)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(275.dp)
                .background(chameleonHeaderBrush),
        ) {
            if (state.thumbnailUrl != null) {
                // Displays the artist's original photo without cropping or artificial zoom
                AsyncImage(
                    model = state.thumbnailUrl,
                    contentDescription = state.name,
                    alignment = Alignment.Center,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 8.dp, bottom = 28.dp),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(dominantColor.copy(alpha = 0.65f), chameleonBottomFade)
                            )
                        )
                )
            }

            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.30f),
                                0.55f to Color.Transparent,
                                0.85f to chameleonBottomFade.copy(alpha = 0.78f),
                                1.0f to chameleonBottomFade,
                            )
                        )
                    )
            )

            // Back button at top-left
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Icon(
                    imageVector = VzIcons.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Color.White,
                )
            }

            // Artist name & stats
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    text = state.name,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.statsLabel.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = state.statsLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Action row: Follow / Following + Shuffle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FollowButton(
                isFollowing = state.isFollowing,
                accent = dominantColor,
                onClick = onFollowClick,
            )

            Spacer(Modifier.weight(1f))

            if (hasPlayableItems) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onRandomClick() }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = VzIcons.Shuffle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Aleatório",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowButton(
    isFollowing: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val bg = if (isFollowing) accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f)
    val border = if (isFollowing) accent.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.24f)
    val content = if (isFollowing) accent else Color.White

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isFollowing) VzIcons.PersonCheck else VzIcons.PersonAdd,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isFollowing) "Seguindo" else "Seguir",
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = FontWeight.Bold,
        )
    }
}