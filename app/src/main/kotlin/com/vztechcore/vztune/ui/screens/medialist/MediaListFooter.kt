package com.vztechcore.vztune.ui.screens.medialist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Album
import com.vztechcore.vztune.ui.components.AppCredits

@Composable
internal fun MediaListFooter(
    state: MediaListUiState,
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        val showDescription = !state.isAlbum || !state.description.isNullOrBlank()
        if (showDescription) {
            Text(
                state.descriptionLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }

        if (state.isAlbum && state.moreAlbumsByArtist.isNotEmpty()) {
            MoreAlbumsSection(
                artistName = state.owner,
                albums = state.moreAlbumsByArtist,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
            )
        }

        AppCredits()
    }
}

@Composable
private fun MoreAlbumsSection(
    artistName: String?,
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (() -> Unit)?,
) {
    val heading = if (!artistName.isNullOrBlank()) "Mais de $artistName" else "Outros lançamentos"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
    ) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (onArtistClick != null) {
                        Modifier.clickable { onArtistClick() }
                    } else {
                        Modifier
                    }
                )
                .padding(vertical = 4.dp),
        )

        Spacer(Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items = albums, key = { it.id }) { album ->
                AlbumCarouselCard(
                    album = album,
                    onClick = { onAlbumClick(album) },
                )
            }
        }
    }
}

@Composable
private fun AlbumCarouselCard(
    album: Album,
    onClick: () -> Unit,
) {
    val subtitleText = listOfNotNull(
        album.year?.toString()?.takeIf { it.isNotBlank() },
        album.views?.takeIf { it.isNotBlank() },
    ).joinToString(" • ").ifBlank { "Álbum" }

    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            if (!album.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = album.thumbnailUrl,
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = subtitleText,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}