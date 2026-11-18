#!/bin/bash
set -e

# VzTune - Glow pequeno no canto + MiniPlayer com progresso suave
# Rode na raiz do projeto VzTune.
#
# 1) EclipseBackground: antes era um degrade vertical na tela inteira, dividindo
#    a tela. Agora e so um brilho radial bem pequeno (220 dp) no canto superior
#    esquerdo, o resto fica preto puro. Vale pra Home, Search, Analytics e Library.
# 2) LibraryScreen: a raiz dela tinha background(Color.Black) opaco, que tampava o
#    glow global do AppNavHost. Por isso a Library parecia nao ter degrade.
#    Este script tira esse fundo opaco, ai o glow aparece na Library tambem.
# 3) MiniPlayerBar + CompactMiniPlayerBar: o playback.progress so atualiza 1x por
#    segundo, por isso o contorno da thumbnail e a barra inferior pulavam a cada
#    segundo. Agora os dois interpolam entre um update e o proximo com animacao
#    linear de 1000 ms, ai o slide anda continuo, sem pulo. Seek / troca de faixa
#    pula direto, nao desliza pra tras.

if [ ! -f "app/src/main/kotlin/com/vztechcore/vztune/ui/navigation/AppNavHost.kt" ]; then
  echo "ERRO: rode este script na raiz do projeto VzTune."
  exit 1
fi

# --- app/src/main/kotlin/com/vztechcore/vztune/ui/components/feedback/EclipseBackground.kt ---
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/feedback/EclipseBackground.kt" <<'EOF'
package com.vztechcore.vztune.ui.components.feedback

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts the most vibrant color from an image URL.
 * Returns Color.Black if the image is missing or extraction fails.
 */
@Composable
fun rememberDominantColor(url: String?): Color {
    var color by remember { mutableStateOf(Color.Black) }
    val context = LocalContext.current

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            color = Color.Black
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(96)
                    .build()
                val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
                val bitmap = drawable?.toBitmap()
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    Color(
                        palette.getVibrantColor(
                            palette.getDominantColor(android.graphics.Color.BLACK)
                        )
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
        if (extracted != null) color = extracted
    }
    return color
}

/**
 * App-wide background glow: a small radial glow in the top-left corner,
 * using the dominant color of the current track image.
 * It does NOT divide the screen anymore: just a little glow in the corner,
 * the rest stays pure black.
 */
@Composable
fun EclipseBackground(
    baseColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // 300 ms = igual ao slide das telas.
    val animated by animateColorAsState(
        targetValue = baseColor,
        animationSpec = tween(durationMillis = 300),
        label = "eclipse",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Glow bem pequeno, so no canto superior esquerdo (220 dp).
        // Centro do radial no canto (0, 0), some ate transparente.
        Canvas(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopStart)
        ) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animated.copy(alpha = 0.55f),
                        animated.copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                    center = Offset.Zero,
                    radius = size.maxDimension * 1.1f,
                )
            )
        }
        content()
    }
}
EOF

# --- app/src/main/kotlin/com/vztechcore/vztune/ui/components/player/MiniPlayerBar.kt ---
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/player/MiniPlayerBar.kt" <<'EOF'
package com.vztechcore.vztune.ui.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.text.TrackSubtitle
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.player.Pause
import com.vztechcore.vztune.ui.icons.player.Play
import com.vztechcore.vztune.ui.icons.player.SkipNext
import com.vztechcore.vztune.ui.icons.player.SkipPrevious

private val GlassFill = Color.Black.copy(alpha = 0.75f)
private val GlassBorder = Color.White.copy(alpha = 0.15f)

@Composable
fun MiniPlayerBar(
    track: Track,
    isPlaying: Boolean,
    isLoading: Boolean = false,
    progress: Float = 0f,
    dominantColor: Color,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val isVideo = track.source == Source.YOUTUBE

    // Smooth progress: playback.progress so atualiza 1x por segundo, por isso
    // o contorno da thumbnail pulava a cada segundo. Aqui a gente interpola
    // entre um update e o proximo com animacao linear de 1000 ms, ai o slide
    // anda continuo, sem pulo. Seek / troca de faixa pula direto, nao desliza.
    val smoothProgressAnim = remember { Animatable(progress.coerceIn(0f, 1f)) }
    LaunchedEffect(progress, track.id) {
        val target = progress.coerceIn(0f, 1f)
        val delta = target - smoothProgressAnim.value
        if (kotlin.math.abs(delta) > 0.08f) {
            smoothProgressAnim.snapTo(target)
        } else {
            smoothProgressAnim.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
            )
        }
    }
    val smoothProgress = smoothProgressAnim.value

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 4.dp)
            .clip(shape)
            .background(GlassFill)
            .border(1.dp, GlassBorder, shape)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
            if (isVideo) {
                SquareThumbWithProgress(
                    imageUrl = track.thumbnailUrl,
                    progress = smoothProgress,
                    dominantColor = dominantColor,
                    isLoading = isLoading
                )
            } else {
                DiscThumbWithProgress(
                    imageUrl = track.thumbnailUrl,
                    progress = smoothProgress,
                    dominantColor = dominantColor,
                    isPlaying = isPlaying,
                    isLoading = isLoading
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            TrackSubtitle(
                track = track,
                style = MaterialTheme.typography.bodySmall,
                prefixColor = Color.White.copy(alpha = 0.55f),
                artistColor = Color.White.copy(alpha = 0.75f),
            )
        }

        Spacer(Modifier.width(4.dp))

        IconButton(onClick = onPrevClick, modifier = Modifier.size(34.dp)) {
            Icon(VzIcons.SkipPrevious, "Anterior", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(38.dp)) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Icon(if (isPlaying) VzIcons.Pause else VzIcons.Play, if (isPlaying) "Pausar" else "Tocar", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        IconButton(onClick = onNextClick, modifier = Modifier.size(34.dp)) {
            Icon(VzIcons.SkipNext, "Próxima", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SquareThumbWithProgress(
    imageUrl: String?,
    progress: Float,
    dominantColor: Color,
    isLoading: Boolean
) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 3.dp.toPx()
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            val inset = strokeWidth / 2f
            val cornerRadius = 8.dp.toPx()
            val rect = Rect(inset, inset, size.width - inset, size.height - inset)
            val path = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(cornerRadius))) }

            drawPath(path, color = Color.White.copy(alpha = 0.15f), style = stroke)

            if (progress > 0f) {
                val measure = PathMeasure().apply { setPath(path, false) }
                val partial = Path()
                measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), partial, true)
                drawPath(partial, color = dominantColor, style = stroke)
            }
        }

        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.06f)),
        )

        if (isLoading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun DiscThumbWithProgress(
    imageUrl: String?,
    progress: Float,
    dominantColor: Color,
    isPlaying: Boolean,
    isLoading: Boolean
) {
    val discRotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying, isLoading) {
        if (isPlaying || isLoading) {
            while (true) {
                discRotation.animateTo(
                    targetValue = discRotation.value + 360f,
                    animationSpec = tween(durationMillis = 12000, easing = LinearEasing)
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 3.dp.toPx()
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            val inset = strokeWidth / 2f

            drawCircle(color = Color.White.copy(alpha = 0.15f), radius = size.minDimension / 2 - inset, style = stroke)

            if (progress > 0f) {
                drawArc(
                    color = dominantColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = stroke
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .padding(4.dp)
                .graphicsLayer { rotationZ = discRotation.value }
                .clip(CircleShape)
                .background(Color(0xFF0F1E3D))
        ) {
            AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.Center).size(10.dp).clip(CircleShape).background(Color.Black))
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f), CircleShape), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
    }
}
EOF

# --- app/src/main/kotlin/com/vztechcore/vztune/ui/components/player/CompactMiniPlayerBar.kt ---
cat > "app/src/main/kotlin/com/vztechcore/vztune/ui/components/player/CompactMiniPlayerBar.kt" <<'EOF'
package com.vztechcore.vztune.ui.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.player.Pause
import com.vztechcore.vztune.ui.icons.player.Play
import com.vztechcore.vztune.ui.icons.player.SkipNext

private val GlassFill = Color.Black.copy(alpha = 0.75f)
private val GlassBorder = Color.White.copy(alpha = 0.15f)

@Composable
fun CompactMiniPlayerBar(
    track: Track,
    isPlaying: Boolean,
    isLoading: Boolean = false,
    progress: Float = 0f,
    dominantColor: Color = Color.White,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    val isVideo = track.source == Source.YOUTUBE
    val clampedProgress = progress.coerceIn(0f, 1f)

    // Smooth progress: mesmo motivo do MiniPlayerBar, a barra inferior pulava
    // 1x por segundo. Interpola com animacao linear de 1000 ms pra ela andar
    // continua, sem pulo. Seek / troca de faixa pula direto.
    val smoothProgressAnim = remember { Animatable(progress.coerceIn(0f, 1f)) }
    LaunchedEffect(progress, track.id) {
        val target = progress.coerceIn(0f, 1f)
        val delta = target - smoothProgressAnim.value
        if (kotlin.math.abs(delta) > 0.08f) {
            smoothProgressAnim.snapTo(target)
        } else {
            smoothProgressAnim.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
            )
        }
    }
    val smoothProgress = smoothProgressAnim.value

    val discRotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying, isVideo, isLoading) {
        if ((isPlaying || isLoading) && !isVideo) {
            while (true) {
                discRotation.animateTo(
                    targetValue = discRotation.value + 360f,
                    animationSpec = tween(durationMillis = 12000, easing = LinearEasing)
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(GlassFill)
            .border(1.dp, GlassBorder, shape)
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (isVideo) 12.dp else 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(if (isVideo) 36.dp else 40.dp)
                    .clip(if (isVideo) RoundedCornerShape(6.dp) else CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                if (isVideo) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = discRotation.value }
                            .clip(CircleShape)
                    ) {
                        AsyncImage(
                            model = track.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        )
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.Black)
                        )
                    }
                }

                if (isLoading) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Color.Black.copy(alpha = 0.45f),
                                if (isVideo) RoundedCornerShape(6.dp) else CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(4.dp))

            IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(36.dp)) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) VzIcons.Pause else VzIcons.Play,
                        contentDescription = if (isPlaying) "Pausar" else "Tocar",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            IconButton(onClick = onNextClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = VzIcons.SkipNext,
                    contentDescription = "Próxima",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // Bottom track progress bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.15f))
        ) {
            if (smoothProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(smoothProgress)
                        .fillMaxHeight()
                        .background(dominantColor)
                )
            }
        }
    }
}
EOF

# --- LibraryScreen.kt: tira o fundo preto opaco da raiz (preserva o resto do arquivo) ---
LIB="app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/LibraryScreen.kt"
if grep -q "Box(modifier.fillMaxSize().background(Color.Black))" "$LIB"; then
  sed -i "s/Box(modifier.fillMaxSize().background(Color.Black))/Box(modifier.fillMaxSize())/" "$LIB"
  echo "LibraryScreen: fundo opaco removido, glow agora aparece."
else
  echo "LibraryScreen: raiz ja sem fundo opaco, nada a fazer."
fi

echo ""
echo "Pronto. Verifica rapida:"
grep -n "size(220.dp)" app/src/main/kotlin/com/vztechcore/vztune/ui/components/feedback/EclipseBackground.kt
grep -n "smoothProgress" app/src/main/kotlin/com/vztechcore/vztune/ui/components/player/MiniPlayerBar.kt | head -5
grep -n "smoothProgress" app/src/main/kotlin/com/vztechcore/vztune/ui/components/player/CompactMiniPlayerBar.kt | head -5
grep -n "Box(modifier.fillMaxSize())" app/src/main/kotlin/com/vztechcore/vztune/ui/screens/library/LibraryScreen.kt | head -2
echo "Roda o build agora."
