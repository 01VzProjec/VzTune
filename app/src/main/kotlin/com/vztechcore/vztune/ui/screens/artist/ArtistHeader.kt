package com.vztechcore.vztune.ui.screens.artist

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
        ) {
            if (state.thumbnailUrl != null) {
                AsyncImage(
                    model = state.thumbnailUrl,
                    contentDescription = state.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(dominantColor.copy(alpha = 0.6f), Color.Black)
                            )
                        )
                )
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.55f to Color.Transparent,
                            1f to Color.Black,
                        )
                    )
            )

            // Botão voltar no topo
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

            // Nome e inscritos/visualizações
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
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Barra de ações: Seguir / Seguindo + Aleatório
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
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
                        .background(Color.White.copy(alpha = 0.1f))
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
    val bg = if (isFollowing) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f)
    val border = if (isFollowing) accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.22f)
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