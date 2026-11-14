package com.vztechcore.vztune.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.ui.screens.medialist.MediaListCircleButton

private val ActionBlue = Color(0xFF1E40AF)

/**
 * Artist page header: blurred banner, circular photo, name, stats line
 * ("subscribers • views") and the action buttons (random play + follow).
 */
@Composable
internal fun ArtistHeader(
    state: ArtistUiState,
    onBackClick: () -> Unit,
    onRandomClick: () -> Unit,
    onFollowClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ================= HERO =================
        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
        ) {
            // Blurred banner (blur is a no-op on Android < 12, the gradient still keeps it readable)
            AsyncImage(
                model = state.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(18.dp)
                    .alpha(0.85f),
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.45f),
                                0.45f to Color.Black.copy(alpha = 0.15f),
                                0.85f to Color.Black.copy(alpha = 0.85f),
                                1.0f to Color.Black,
                            )
                        )
                    )
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaListCircleButton(VzIcons.Close, "Fechar", onBackClick)
            }

            // Circular artist photo
            AsyncImage(
                model = state.thumbnailUrl,
                contentDescription = state.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .size(168.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
            )
        }

        // ================= NAME + STATS =================
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .offset(y = (-4).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.name,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (state.statsLabel.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.statsLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ================= ACTION BUTTONS =================
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Same pill as playlists/albums, but with the shuffle icon (plays a random track)
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ActionBlue)
                    .clickable { onRandomClick() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    VzIcons.Shuffle,
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

            // Follow the artist inside the app
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (state.isFollowing) ActionBlue else Color.White.copy(alpha = 0.12f))
                    .clickable { onFollowClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state.isFollowing) VzIcons.Check else Icons.Filled.PersonAdd,
                    contentDescription = if (state.isFollowing) "Deixar de seguir" else "Seguir artista",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}
