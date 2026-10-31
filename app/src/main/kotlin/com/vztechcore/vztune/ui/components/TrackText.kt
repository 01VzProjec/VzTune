package com.vztechcore.vztune.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

/**
 * Subtitle line for a Track.
 *   Song  -> "Song - artist1, artist2 & lastArtist"
 *   Video -> "Video - channelName"
 * The "Song -" / "Video -" prefix is stable; the artist text marquees if too long.
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
    val prefix = if (isVideo) "Video - " else "Song - "
    val artists = if (isVideo) {
        track.artist.substringBefore("•").substringBefore(",").trim()
    } else {
        formatArtists(track.artist)
    }

    androidx.compose.foundation.layout.Row(modifier = modifier) {
        Text(text = prefix, style = style, color = prefixColor, maxLines = 1)
        MarqueeText(
            text = artists,
            modifier = Modifier.weight(1f),
            style = style,
            color = artistColor,
        )
    }
}

private fun formatArtists(raw: String): String {
    val artists = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    return when {
        artists.isEmpty() -> "Desconhecido"
        artists.size == 1 -> artists[0]
        artists.size == 2 -> "${artists[0]} & ${artists[1]}"
        else -> {
            val head = artists.dropLast(1).joinToString(", ")
            "$head & ${artists.last()}"
        }
    }
}