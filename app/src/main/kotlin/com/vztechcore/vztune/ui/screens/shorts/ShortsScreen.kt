
package com.vztechcore.vztune.ui.screens.shorts

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.vztechcore.vztune.core.domain.music.Track
import com.vztechcore.vztune.ui.components.rows.SheetOptionRow
import com.vztechcore.vztune.ui.icons.VzIcons
import com.vztechcore.vztune.ui.icons.actions.Add
import com.vztechcore.vztune.ui.icons.actions.ArrowBackIos
import com.vztechcore.vztune.ui.icons.actions.Bookmark
import com.vztechcore.vztune.ui.icons.actions.Close
import com.vztechcore.vztune.ui.icons.actions.Comment
import com.vztechcore.vztune.ui.icons.actions.FavoriteFilled
import com.vztechcore.vztune.ui.icons.actions.FavoriteOutline
import com.vztechcore.vztune.ui.icons.actions.Info
import com.vztechcore.vztune.ui.icons.actions.MoreVert
import com.vztechcore.vztune.ui.icons.actions.Remove
import com.vztechcore.vztune.ui.icons.actions.Share
import com.vztechcore.vztune.ui.icons.actions.Visibility
import com.vztechcore.vztune.ui.icons.player.Pause
import com.vztechcore.vztune.ui.icons.player.Play
import com.vztechcore.vztune.ui.icons.player.Speed
import com.vztechcore.vztune.ui.icons.player.VolumeMute
import com.vztechcore.vztune.ui.icons.player.VolumeUp
import com.vztechcore.vztune.ui.icons.settings.Tune
import kotlinx.coroutines.delay

private val SheetColor = Color(0xFF121829)
private val HashtagBlue = Color(0xFF5AB0FF)
private val ShortLineBreak = Char(10)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsScreen(
    state: ShortsUiState,
    player: ExoPlayer,
    onBackClick: () -> Unit,
    onPageSettled: (Int) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleMute: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onQualitySelect: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleFollow: () -> Unit,
    onOpenChannel: () -> Unit,
) {
    val context = LocalContext.current
    if (state.items.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nenhum Short para mostrar", color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onBackClick) { Text("Voltar", color = Color.White) }
            }
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = state.index) { state.items.size }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var interfaceHidden by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var cueVisible by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.settledPage) {
        scale = 1f
        offset = Offset.Zero
        onPageSettled(pagerState.settledPage)
    }
    LaunchedEffect(state.cueToken) {
        if (state.cueToken != 0L) {
            cueVisible = true
            delay(500)
            cueVisible = false
        }
    }

    val zoomed = scale > 1.01f
    val interfaceVisible = !interfaceHidden && !zoomed
    val currentTrack = state.items.getOrElse(pagerState.settledPage) { state.items.first() }
    val bottomPad by animateDpAsState(
        targetValue = if (showDescription) 300.dp else 0.dp,
        animationSpec = tween(250),
        label = "shortsDescPad",
    )

    val gestureModifier = Modifier
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { onTogglePlayPause() },
                onDoubleTap = { scale = 1f; offset = Offset.Zero },
            )
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var prevDist = -1f
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size >= 2) {
                        val dist = (pressed[0].position - pressed[1].position).getDistance()
                        if (prevDist > 0f && dist > 0f) {
                            scale = (scale * (dist / prevDist)).coerceIn(1f, 5f)
                            if (scale <= 1.01f) offset = Offset.Zero
                            event.changes.forEach { it.consume() }
                        }
                        prevDist = dist
                    } else {
                        prevDist = -1f
                    }
                }
            }
        }
        .pointerInput(zoomed) {
            if (zoomed) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offset += drag
                }
            }
        }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().padding(bottom = bottomPad)) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !zoomed,
                beyondViewportPageCount = 1,
            ) { page ->
                val track = state.items[page]
                val isSettled = page == pagerState.settledPage
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            scaleX = if (isSettled) scale else 1f
                            scaleY = if (isSettled) scale else 1f
                            translationX = if (isSettled) offset.x else 0f
                            translationY = if (isSettled) offset.y else 0f
                        }
                    ) {
                        AsyncImage(
                            model = track.thumbnailUrl ?: ("https://i.ytimg.com/vi/" + track.id + "/hqdefault.jpg"),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                }
                            },
                            update = { pv -> pv.player = if (isSettled) player else null },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (isSettled) {
                        Box(Modifier.fillMaxSize().then(gestureModifier))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = cueVisible,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state.cueIsPause) VzIcons.Pause else VzIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.Center).size(34.dp),
            )
        }

        if (interfaceVisible) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShortsCircleButton(VzIcons.ArrowBackIos, "Voltar", onBackClick)
                Spacer(Modifier.weight(1f))
                ShortsCircleButton(
                    icon = if (state.isMuted) VzIcons.VolumeMute else VzIcons.VolumeUp,
                    contentDescription = if (state.isMuted) "Ativar áudio" else "Silenciar",
                    onClick = onToggleMute,
                )
                Spacer(Modifier.width(8.dp))
                ShortsCircleButton(VzIcons.MoreVert, "Mais opções") { showMenu = true }
            }

            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 10.dp, top = 90.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ShortAction(
                    icon = if (state.isFavorite) VzIcons.FavoriteFilled else VzIcons.FavoriteOutline,
                    label = "Favoritar",
                    onClick = onToggleFavorite,
                )
                ShortAction(VzIcons.Share, "Compartilhar") {
                    val shareText = "Olha esse Short no VzTune" + ShortLineBreak +
                        currentTrack.title + ShortLineBreak +
                        "https://www.youtube.com/shorts/" + currentTrack.id
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(send, "Compartilhar Short"))
                }
                ShortAction(VzIcons.Comment, "Comentários") {
                    Toast.makeText(context, "Comentários em breve ;)", Toast.LENGTH_SHORT).show()
                }
                ShortAction(VzIcons.Bookmark, "Salvar") {
                    Toast.makeText(context, "Salvar em breve ;)", Toast.LENGTH_SHORT).show()
                }
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .clickable { Toast.makeText(context, "Som do Short em breve ;)", Toast.LENGTH_SHORT).show() }
                ) {
                    AsyncImage(
                        model = currentTrack.thumbnailUrl,
                        contentDescription = "Som do Short",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(
                Modifier.align(Alignment.BottomStart).navigationBarsPadding()
                    .padding(start = 14.dp, end = 84.dp, bottom = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))
                            .clickable { onOpenChannel() }
                    ) {
                        val avatar = state.channelAvatarUrl ?: state.info?.channelAvatarUrl
                        if (!avatar.isNullOrBlank()) {
                            AsyncImage(model = avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = state.channelHandle?.takeIf { it.isNotBlank() } ?: state.channelName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).clickable { onOpenChannel() },
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.height(32.dp).clip(RoundedCornerShape(50))
                            .background(if (state.isFollowing) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(50))
                            .clickable { onToggleFollow() }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (state.isFollowing) "Seguindo" else "Seguir",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = currentTrack.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { showDescription = true },
                )
            }
        }

        if (!interfaceVisible) {
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 26.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                    .clickable { interfaceHidden = false; scale = 1f; offset = Offset.Zero }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(VzIcons.Visibility, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Mostrar interface", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SheetColor,
            contentColor = Color.White,
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
                SheetOptionRow(icon = VzIcons.Info, label = "Descrição", onClick = { showMenu = false; showDescription = true })
                SheetOptionRow(icon = VzIcons.Visibility, label = "Ocultar interface", onClick = { showMenu = false; interfaceHidden = true })
                SheetOptionRow(icon = VzIcons.Tune, label = "Qualidade", subtitle = state.selectedQuality ?: "Automática", onClick = { showMenu = false; showQuality = true })
                SheetOptionRow(icon = VzIcons.Speed, label = "Velocidade de reprodução", subtitle = String.format("%.2fx", state.speed), onClick = { showMenu = false; showSpeed = true })
            }
        }
    }

    if (showQuality) {
        ModalBottomSheet(
            onDismissRequest = { showQuality = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SheetColor,
            contentColor = Color.White,
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    "Qualidade",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                val streams = state.info?.streams.orEmpty()
                if (streams.isEmpty()) {
                    Text(
                        "Nenhuma qualidade encontrada ainda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                } else {
                    streams.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onQualitySelect(s.qualityLabel); showQuality = false }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = s.qualityLabel == state.selectedQuality,
                                onClick = { onQualitySelect(s.qualityLabel); showQuality = false },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF60A5FA), unselectedColor = Color.White.copy(alpha = 0.5f)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(s.qualityLabel, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showSpeed) {
        ModalBottomSheet(
            onDismissRequest = { showSpeed = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SheetColor,
            contentColor = Color.White,
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Velocidade de reprodução", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    String.format("%.2fx", state.speed),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShortsCircleButton(VzIcons.Remove, "Diminuir 0.5") { onSpeedChange(state.speed - 0.5f) }
                    Slider(
                        value = state.speed,
                        onValueChange = onSpeedChange,
                        valueRange = 0.35f..4f,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF60A5FA)),
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    )
                    ShortsCircleButton(VzIcons.Add, "Aumentar 0.5") { onSpeedChange(state.speed + 0.5f) }
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("1.0" to 1f, "1.25" to 1.25f, "1.5" to 1.5f, "2.0" to 2f, "3.0" to 3f, "4.0" to 4f)) { (label, value) ->
                        val selected = kotlin.math.abs(state.speed - value) < 0.01f
                        Box(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(if (selected) Color.White else Color.White.copy(alpha = 0.1f))
                                .clickable { onSpeedChange(value) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                label + "x",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDescription) {
        ModalBottomSheet(
            onDismissRequest = { showDescription = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = SheetColor,
            contentColor = Color.White,
            scrimColor = Color.Transparent,
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())
                    .navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        currentTrack.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showDescription = false }) {
                        Icon(VzIcons.Close, "Fechar descrição", tint = Color.White)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable { showDescription = false; onOpenChannel() }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
                        val avatar = state.channelAvatarUrl ?: state.info?.channelAvatarUrl
                        if (!avatar.isNullOrBlank()) {
                            AsyncImage(model = avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(state.channelName, style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val handle = state.channelHandle?.takeIf { it.isNotBlank() }
                        if (handle != null) {
                            Text(handle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                DescStatRow("Marcações gostei:", formatPlainCount(state.info?.likeCount))
                DescStatRow("Visualizações:", formatPlainCount(state.info?.viewCount))
                DescStatRow("Data em que foi lançado:", state.info?.publishedText ?: currentTrack.publishedText ?: "-")
                Spacer(Modifier.height(12.dp))
                val descText = state.info?.description?.takeIf { it.isNotBlank() } ?: currentTrack.title
                Text(
                    text = rememberHashtagText(descText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun ShortsCircleButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun ShortAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun DescStatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.width(180.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

private fun formatPlainCount(n: Long?): String {
    if (n == null || n <= 0L) return "-"
    return String.format("%,d", n).replace(',', '.')
}

@Composable
private fun rememberHashtagText(text: String): AnnotatedString = remember(text) {
    buildAnnotatedString {
        var last = 0
        Regex("#[A-Za-z0-9_À-ÿ]+").findAll(text).forEach { m ->
            if (m.range.first > last) append(text.substring(last, m.range.first))
            withStyle(SpanStyle(color = HashtagBlue)) { append(m.value) }
            last = m.range.last + 1
        }
        if (last < text.length) append(text.substring(last))
    }
}
