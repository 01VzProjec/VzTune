package com.vztechcore.vztune.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vztechcore.vztune.ui.components.feedback.fadeOutBottom
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.ArrowBack
import com.vztechcore.vztune.ui.icons.actions.PersonAdd
import com.vztechcore.vztune.ui.icons.actions.PersonCheck
import com.vztechcore.vztune.ui.icons.media.Artist
import com.vztechcore.vztune.ui.icons.player.Play
import com.vztechcore.vztune.ui.icons.player.Shuffle
import com.vztechcore.vztune.ui.screens.medialist.MediaListCircleButton

// Mantido pra compatibilidade com ArtistScreen antigo - agora sempre retorna preto.
// Camaleao desativado por enquanto, fundo e sempre AMOLED.
@Composable
fun rememberArtistAccent(thumbnailUrl: String?): Color = Color.Black

// Mantido pra compatibilidade - agora e sempre preto solido, sem gradiente de cor.
@Composable
fun rememberArtistChameleonBrush(accent: Color): Brush =
    Brush.verticalGradient(listOf(Color.Black, Color.Black))

/**
 * Hero do artista igual a foto: foto cheia em cima (Crop, corta pra preencher),
 * com fade pra preto embaixo, nome centralizado embaixo da foto,
 * stats centralizados, e 3 botoes circulares centralizados.
 * Fundo sempre preto AMOLED - sem camaleao.
 * Icones mantidos: Play, Shuffle, PersonAdd/PersonCheck.
 */
@Composable
fun ArtistHeader(
    state: ArtistUiState,
    accent: Color,
    onBackClick: () -> Unit,
    onRandomClick: () -> Unit,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasPlayableItems = state.topSongs.isNotEmpty() || state.videos.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        // Foto de perfil em cima - igual a foto: cheia, Crop, fade pra preto
        Box(
            Modifier
                .fillMaxWidth()
                .height(380.dp)
        ) {
            ArtistPhoto(imageUrl = state.thumbnailUrl, name = state.name)

            // Sombra em cima pra botao voltar ficar legivel
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
            )

            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp)
            ) {
                MediaListCircleButton(VzIcons.ArrowBack, "Voltar", onBackClick)
            }
        }

        // Nome + stats centralizados embaixo da foto - igual a foto
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.name,
                style = MaterialTheme.typography.displaySmall,
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
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 3 botoes circulares centralizados - igual a foto
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (hasPlayableItems) {
                // Esquerda: Play (toca embaralhado, igual o Aleatorio)
                CircleButton(
                    icon = VzIcons.Play,
                    contentDescription = "Tocar",
                    size = 56.dp,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    borderColor = Color.White.copy(alpha = 0.4f),
                    onClick = onRandomClick,
                )
                Spacer(Modifier.size(16.dp))
                // Meio: Shuffle branco grandao - igual a foto
                CircleButton(
                    icon = VzIcons.Shuffle,
                    contentDescription = "Aleatório",
                    size = 72.dp,
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    borderColor = null,
                    onClick = onRandomClick,
                )
                Spacer(Modifier.size(16.dp))
            }
            // Direita: Seguir / Seguindo
            CircleButton(
                icon = if (state.isFollowing) VzIcons.PersonCheck else VzIcons.PersonAdd,
                contentDescription = if (state.isFollowing) "Seguindo" else "Seguir",
                size = 56.dp,
                containerColor = if (state.isFollowing) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                contentColor = Color.White,
                borderColor = Color.White.copy(alpha = 0.4f),
                onClick = onFollowClick,
            )
        }
    }
}

/**
 * Foto cheia, Crop (corta pra preencher, nao mostra inteira), alinhada em cima,
 * com fade pra preto embaixo pra derreter no fundo AMOLED.
 */
@Composable
private fun ArtistPhoto(imageUrl: String?, name: String) {
    if (imageUrl.isNullOrBlank()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = VzIcons.Artist,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.size(140.dp),
            )
        }
        return
    }
    val context = LocalContext.current
    val request = androidx.compose.runtime.remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(400)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = name,
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .fadeOutBottom(fadeFraction = 0.55f),
    )
}

@Composable
private fun CircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .then(
                if (borderColor != null) Modifier.border(1.dp, borderColor, CircleShape)
                else Modifier
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
    }
}
