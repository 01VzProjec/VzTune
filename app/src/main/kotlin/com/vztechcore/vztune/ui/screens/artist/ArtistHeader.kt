package com.vztechcore.vztune.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.PersonAdd
import com.vztechcore.vztune.ui.icons.actions.PersonCheck
import com.vztechcore.vztune.ui.icons.player.Play
import com.vztechcore.vztune.ui.icons.player.Shuffle

@Composable
fun ArtistHeader(
    name: String,
    thumbnailUrl: String?,
    subscribers: String?,
    isFollowing: Boolean,
    dominantColor: Color,
    hasSongs: Boolean,
    onFollowClick: () -> Unit,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
        ) {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = name,
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
                            0f to Color.Black.copy(alpha = 0.25f),
                            0.55f to Color.Transparent,
                            1f to Color.Black,
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subscribers.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        subscribers,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FollowButton(
                isFollowing = isFollowing,
                accent = dominantColor,
                onClick = onFollowClick,
            )

            Spacer(Modifier.weight(1f))

            if (hasSongs) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { onShuffleClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VzIcons.Shuffle,
                        contentDescription = "Aleatório",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(dominantColor)
                        .clickable { onPlayClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = VzIcons.Play,
                        contentDescription = "Tocar",
                        tint = Color.Black,
                        modifier = Modifier.size(26.dp),
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
            if (isFollowing) "Seguindo" else "Seguir",
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = FontWeight.Bold,
        )
    }
}