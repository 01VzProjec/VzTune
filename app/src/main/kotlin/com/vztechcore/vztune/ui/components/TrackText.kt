package com.vztechcore.vztune.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track

@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    Text(
        text = text,
        modifier = modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            velocity = 30.dp,
            initialDelayMillis = 1500,
            repeatDelayMillis = 1500,
        ),
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
    )
}

/** Small square "E" badge shown next to tracks flagged as explicit. */
@Composable
fun ExplicitBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.6f))
            .semantics { contentDescription = "Conteúdo explícito" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "E",
            color = Color.Black,
            fontSize = 10.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Subtitle line for a Track.
 *   Video -> "Video — <channelName>"
 *   Song  -> "Song — <artist1, artist2, ...>"
 * The prefix uses an em dash (—) and is stable; the artist text
 * marquees if it does not fit. Explicit tracks get an "E" badge before the artist.
 */
@Composable
fun TrackSubtitle(
    track: Track,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    prefixColor: Color = Color.Unspecified,
    artistColor: Color = Color.Unspecified,
) {
    val isVideo = track.source == Source.YOUTUBE
    val prefix = if (isVideo) "Video — " else "Song — "
    val artists = track.artist.ifBlank { "Unknown" }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = prefix, style = style, color = prefixColor, maxLines = 1)
        if (track.isExplicit) {
            ExplicitBadge()
            Spacer(Modifier.width(4.dp))
        }
        MarqueeText(
            text = artists,
            modifier = Modifier.weight(1f),
            style = style,
            color = artistColor,
        )
    }
}
