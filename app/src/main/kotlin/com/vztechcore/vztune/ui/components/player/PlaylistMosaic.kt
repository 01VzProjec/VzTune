package com.vztechcore.vztune.ui.components.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

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
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxWidth(),
    )
}
