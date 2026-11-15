package com.vztechcore.vztune.ui.screens.artist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vztechcore.vztune.ui.components.feedback.rememberDominantColor
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.ArrowBack
import com.vztechcore.vztune.ui.icons.actions.PersonAdd
import com.vztechcore.vztune.ui.icons.actions.PersonCheck
import com.vztechcore.vztune.ui.icons.media.Artist
import com.vztechcore.vztune.ui.icons.player.Shuffle
import com.vztechcore.vztune.ui.screens.medialist.MediaListCircleButton

private val HeroMinHeight = 300.dp
private val HeroMaxHeight = 520.dp
private val ButtonHeight = 44.dp

/**
 * Accent color extracted from the artist photo.
 * It is animated so the screen blends smoothly when the photo loads.
 */
@Composable
fun rememberArtistAccent(thumbnailUrl: String?): Color {
    val extracted = rememberDominantColor(thumbnailUrl)
    val animated by animateColorAsState(
        targetValue = extracted,
        animationSpec = tween(durationMillis = 700),
        label = "artistAccent",
    )
    return animated
}

/**
 * Chooses a readable text color for a solid accent background.
 * Dark text on very bright backgrounds, white text on everything else.
 * The background itself stays 100 percent solid, no gradient is applied.
 */
fun contentColorForAccent(accent: Color): Color {
    return if (accent.luminance() > 0.60f) Color(0xFF0B0D12) else Color.White
}

/**
 * Artist hero.
 *
 * Layout rules:
 * - The background is solid accent, never a gradient to black.
 * - The photo is shown whole with Fit, never cropped.
 * - The name and stats sit BELOW the photo, so nothing covers the photo.
 * - The back button has its own dark circle, so no top gradient scrim is needed.
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
    val contentColor = contentColorForAccent(accent)

    Column(
        modifier
            .fillMaxWidth()
            .background(accent)
    ) {
        Box(Modifier.fillMaxWidth()) {
            ArtistPhoto(
                imageUrl = state.thumbnailUrl,
                name = state.name,
                background = accent,
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

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)
        ) {
            Text(
                text = state.name,
                style = MaterialTheme.typography.displaySmall.copy(letterSpacing = (-0.5).sp),
                color = contentColor,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.statsLabel.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.statsLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (hasPlayableItems) {
                PillButton(
                    label = "Aleatório",
                    icon = VzIcons.Shuffle,
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    onClick = onRandomClick,
                )
            }
            FollowButton(
                isFollowing = state.isFollowing,
                accent = accent,
                onClick = onFollowClick,
            )
        }
    }
}

/**
 * Artist photo, always fully visible.
 * Uses Fit so the whole image is shown, without any bottom fade.
 * The empty space around the photo uses the same solid accent background.
 */
@Composable
private fun ArtistPhoto(
    imageUrl: String?,
    name: String,
    background: Color,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrBlank()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(HeroMinHeight),
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
        val request = remember(imageUrl) {
            ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(400)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HeroMinHeight, max = HeroMaxHeight),
        )
    }
}

@Composable
private fun FollowButton(
    isFollowing: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val contentColor = contentColorForAccent(accent)
    if (isFollowing) {
        PillButton(
            label = "Seguindo",
            icon = VzIcons.PersonCheck,
            containerColor = Color.White.copy(alpha = 0.22f),
            contentColor = contentColor,
            borderColor = contentColor.copy(alpha = 0.70f),
            onClick = onClick,
        )
    } else {
        PillButton(
            label = "Seguir",
            icon = VzIcons.PersonAdd,
            containerColor = Color.Transparent,
            contentColor = contentColor,
            borderColor = contentColor.copy(alpha = 0.35f),
            onClick = onClick,
        )
    }
}

/** Rounded pill with icon and label. Shared by shuffle and follow buttons. */
@Composable
private fun PillButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    borderColor: Color? = null,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .height(ButtonHeight)
            .clip(shape)
            .background(containerColor)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = FontWeight.Bold,
        )
    }
}
