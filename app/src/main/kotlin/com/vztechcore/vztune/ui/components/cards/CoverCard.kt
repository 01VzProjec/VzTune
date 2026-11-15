package com.vztechcore.vztune.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.ui.components.feedback.VzLogoStatic
import com.vztechcore.vztune.ui.components.player.DefaultPlaylistCover
import com.vztechcore.vztune.ui.components.player.PlaylistCoverPalette
import com.vztechcore.vztune.ui.components.player.PlaylistMosaic

/**
 * Card used in the Library for playlists and other collections.
 *
 * Cover priority:
 * 1. Mosaic when there are 4 or more track thumbnails and no explicit cover.
 * 2. Vz color cover when imageUrl is a "vz_color:" token.
 * 3. Real image when imageUrl is a file path or http url.
 * 4. Static Vz logo fallback when there is nothing else.
 *
 * The color token is the same token used inside the playlist screen,
 * so the card and the detail screen always show the same cover.
 */
@Composable
fun CoverCard(
    title: String,
    subtitle: String? = null,
    imageUrl: String? = null,
    mosaic: List<String> = emptyList(),
    isEmpty: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(140.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
    ) {
        Box(
            Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            when {
                mosaic.size >= 4 -> {
                    PlaylistMosaic(mosaic)
                }
                PlaylistCoverPalette.isDefaultColorToken(imageUrl) -> {
                    DefaultPlaylistCover(
                        seed = title,
                        coverPath = imageUrl,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                !imageUrl.isNullOrBlank() -> {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                isEmpty -> {
                    VzLogoStatic(modifier = Modifier.fillMaxSize(0.5f))
                }
                else -> {
                    VzLogoStatic(modifier = Modifier.fillMaxSize(0.5f))
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
