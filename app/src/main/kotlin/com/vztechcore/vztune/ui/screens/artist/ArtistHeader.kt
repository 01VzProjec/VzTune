package com.vztechcore.vztune.ui.screens.artist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
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

/** Brightest tone the backdrop may reach, so white text stays readable on vivid photos. */
private const val MAX_BACKDROP_LUMINANCE = 0.30f

private val HeroMinHeight = 300.dp
private val ButtonHeight = 44.dp

/**
 * Accent color extracted from the artist photo, animated so the screen
 * blends from black into the artist's color instead of popping in.
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

/** Darkens overly bright accents; dark accents are returned untouched. */
private fun Color.toBackdropTone(): Color {
    val lum = luminance()
    return if (lum > MAX_BACKDROP_LUMINANCE) lerp(Color.Black, this, MAX_BACKDROP_LUMINANCE / lum) else this
}

/**
 * Full-screen "chameleon" backdrop: the artist color on top, fading into near-black at the bottom.
 * It is drawn once behind the whole screen, so the hero photo can fade straight into it (no seams).
 */
/**
 * Solid backdrop: same tone everywhere, no gradient to black.
 * Kept with the same name so ArtistScreen still compiles.
 */
@Composable
fun rememberArtistChameleonBrush(accent: Color): Brush = remember(accent) {
    val tone = accent.toBackdropTone()
    Brush.verticalGradient(listOf(tone, tone))
}

/**
 * Artist hero: full-width photo that fades into the backdrop, the name and stats over it,
 * and the shuffle / follow buttons right below.
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

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = HeroMinHeight)
                .animateContentSize()
        ) {
            ArtistPhoto(imageUrl = state.thumbnailUrl, name = state.name)

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
                    style = MaterialTheme.typography.displaySmall.copy(
                        letterSpacing = (-0.5).sp,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.45f),
                            offset = Offset(0f, 3f),
                            blurRadius = 12f,
                        ),
                    ),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.statsLabel.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = state.statsLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
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
 * The photo is shown whole (never cropped) with Fit, no fade, no crop.
 * Without a photo, a big faint icon fills the hero.
 */
@Composable
private fun ArtistPhoto(imageUrl: String?, name: String) {
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
            .heightIn(min = HeroMinHeight, max = 520.dp),
    )
}

@Composable
private fun FollowButton(
    isFollowing: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    if (isFollowing) {
        // Lightened accent keeps the label readable even when the photo color is dark
        val tint = lerp(accent, Color.White, 0.55f)
        PillButton(
            label = "Seguindo",
            icon = VzIcons.PersonCheck,
            containerColor = accent.copy(alpha = 0.30f),
            contentColor = tint,
            borderColor = tint.copy(alpha = 0.70f),
            onClick = onClick,
        )
    } else {
        PillButton(
            label = "Seguir",
            icon = VzIcons.PersonAdd,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            borderColor = Color.White.copy(alpha = 0.35f),
            onClick = onClick,
        )
    }
}

/** Rounded pill with icon + label. Shared by the shuffle and follow buttons. */
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
