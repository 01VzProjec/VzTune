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
