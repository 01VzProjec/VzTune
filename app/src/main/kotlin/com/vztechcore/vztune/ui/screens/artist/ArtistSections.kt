package com.vztechcore.vztune.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Artist
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.ExplicitBadge

/** Section title with the "Mais" action on the right (hidden when [onMoreClick] is null). */
@Composable
internal fun ArtistSectionHeader(
    title: String,
    onMoreClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (onMoreClick != null) {
            Text(
                text = "Mais",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onMoreClick() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** Song row: optional ranking number on the left, 3-dots button on the right. */
@Composable
internal fun ArtistSongRow(
    track: Track,
    number: Int?,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.width(32.dp),
            )
        }
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isExplicit) {
                    ExplicitBadge()
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Mais",
                tint = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

/** Square cover card used for albums, singles and featured playlists. */
@Composable
internal fun ArtistSquareCard(
    imageUrl: String?,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
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

/** 16:9 video card used in the horizontal "Vídeos" row. */
@Composable
internal fun ArtistVideoCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Circular artist photo with the name centered below (related artists row). */
@Composable
internal fun ArtistCircleCard(
    artist: Artist,
    onClick: () -> Unit,
    size: Dp = 144.dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(size)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            AsyncImage(
                model = artist.thumbnailUrl,
                contentDescription = artist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
