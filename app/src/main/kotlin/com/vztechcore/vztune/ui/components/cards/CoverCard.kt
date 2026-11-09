package com.vztechcore.vztune.ui.components.cards

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.ui.components.player.PlaylistMosaic
import com.vztechcore.vztune.ui.components.feedback.VzLogoStatic

/**
 * Square cover with title and subtitle below. Used for albums, singles, playlists
 * (artist screen, "see all" grids and the Library). A mosaic of 4+ thumbnails wins over [imageUrl].
 *
 * When there is neither a mosaic nor a cover (a brand-new local playlist with no
 * tracks and no chosen cover), the card is drawn with a black background and the
 * static VzTechCore logo — same identity shown on the loading screens.
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
                .background(
                    if (isEmpty) Color.Black
                    else Color.White.copy(alpha = 0.08f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                mosaic.size >= 4 -> PlaylistMosaic(mosaic)
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