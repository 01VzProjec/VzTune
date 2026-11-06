package com.vztechcore.vztune.data.ytmusic
​import com.vztechcore.vztune.core.domain.music.Source
import com.vztechcore.vztune.core.domain.music.Track
import org.json.JSONArray
import org.json.JSONObject
​internal object YTMusicPlaylistParser {
​data class PlaylistDetails(
val title: String,
val owner: String,
val ownerAvatarUrl: String?,
val description: String?,
val coverUrl: String?,
val trackCount: Int,
val year: String?,
val views: String?,
val tracks: List<Track>,
val continuationToken: String?
)
​fun parsePlaylist(json: JSONObject): PlaylistDetails {
val header = YTMusicTextUtils.findFirst(json, "musicDetailHeaderRenderer")
?: YTMusicTextUtils.findFirst(json, "musicResponsiveHeaderRenderer")
?: YTMusicTextUtils.findFirst(json, "musicEditablePlaylistDetailHeaderRenderer")
?: YTMusicTextUtils.findFirst(json, "playlistHeaderRenderer")
​val title = YTMusicArtistParser.extractHeaderTitle(header) ?: "Playlist"
val coverUrl = header?.let { YTMusicThumbnailUtils.extractHeaderCover(it) }
​val (owner, ownerAvatarUrl, year, views) = extractPlaylistMetadata(header)
val trackCount = extractTrackCount(
YTMusicTextUtils.runsText(header?.optJSONObject("secondSubtitle")) + " " +
YTMusicTextUtils.runsText(header?.optJSONObject("subtitle"))
)
​val shelf = YTMusicTextUtils.findFirst(json, "musicPlaylistShelfRenderer")
?: YTMusicTextUtils.findFirst(json, "musicShelfRenderer")
​val tracks = mutableListOf<Track>()
shelf?.optJSONArray("contents")?.let { contents ->
for (i in 0 until contents.length()) {
val renderer = contents.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer")
if (renderer != null) {
parsePlaylistItem(renderer, tracks)
}
}
}
​val continuation = extractContinuation(shelf)
​return PlaylistDetails(
title = title,
owner = owner,
ownerAvatarUrl = ownerAvatarUrl,
description = extractDescription(json, header),
coverUrl = coverUrl,
trackCount = trackCount,
year = year,
views = views,
tracks = tracks,
continuationToken = continuation
)
}
​private fun extractPlaylistMetadata(header: JSONObject?): Quadruple<String, String?, String?, String?> {
if (header == null) return Quadruple("Criador Desconhecido", null, null, null)
​var owner: String? = null
var ownerAvatarUrl: String? = null
var year: String? = null
var views: String? = null
​// 1. Extract owner avatar thumbnail if present
val avatarNode = header.optJSONObject("straplineThumbnail")
?: header.optJSONObject("authorThumbnail")
?: header.optJSONObject("owner")?.optJSONObject("musicArtistCardModel")
if (avatarNode != null) {
ownerAvatarUrl = YTMusicThumbnailUtils.thumbnailOf(avatarNode)
}
​// 2. Extract owner name from strapline / ownerText / author
val strapline = YTMusicTextUtils.runsText(header.optJSONObject("straplineTextOne"))
if (strapline.isNotBlank() && !YTMusicTextUtils.isTypeWord(strapline)) {
owner = strapline
}
​if (owner == null) {
val ownerText = YTMusicTextUtils.runsText(header.optJSONObject("ownerText"))
if (ownerText.isNotBlank() && !YTMusicTextUtils.isTypeWord(ownerText)) {
owner = ownerText
}
}
​if (owner == null) {
val author = YTMusicTextUtils.runsText(header.optJSONObject("author"))
if (author.isNotBlank() && !YTMusicTextUtils.isTypeWord(author)) {
owner = author
}
}
​// 3. Search for channel runs (UC... browse endpoint)
if (owner == null) {
YTMusicTextUtils.walk(header) { node ->
if (owner != null) return@walk
val runs = node.optJSONArray("runs") ?: return@walk
for (i in 0 until runs.length()) {
val run = runs.optJSONObject(i) ?: continue
val text = run.optString("text").trim()
val browseId = run.optJSONObject("navigationEndpoint")
?.optJSONObject("browseEndpoint")
?.optString("browseId")
if (browseId?.startsWith("UC") == true || browseId?.startsWith("MPAD") == true) {
if (text.isNotBlank() && !YTMusicTextUtils.isTypeWord(text)) {
owner = text
break
}
}
}
}
}
​// 4. Subtitle runs fallback
val subtitleRuns = header.optJSONObject("subtitle")?.optJSONArray("runs")
?: header.optJSONObject("secondSubtitle")?.optJSONArray("runs")
​if (subtitleRuns != null) {
val parts = mutableListOf<String>()
for (i in 0 until subtitleRuns.length()) {
val run = subtitleRuns.optJSONObject(i) ?: continue
val text = run.optString("text").trim()
if (text.isNotBlank() && text != "•" && text != "·") {
parts.add(text)
}
}
​for (part in parts) {
when {
YTMusicTextUtils.isYear(part) -> year = part
YTMusicTextUtils.isViewCount(part) -> views = part
owner == null && !YTMusicTextUtils.isTypeWord(part) && !YTMusicTextUtils.isDuration(part) && !YTMusicTextUtils.isDate(part) -> {
owner = part
}
}
}
}
​return Quadruple(owner ?: "Criador", ownerAvatarUrl, year, views)
}
​private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
​fun parsePlaylistContinuation(json: JSONObject): Pair<List<Track>, String?> {
val tracks = mutableListOf<Track>()
val contents = YTMusicTextUtils.findFirst(json, "musicPlaylistShelfContinuation")
?.optJSONArray("contents") ?: json.optJSONArray("continuationContents")
​if (contents != null) {
for (i in 0 until contents.length()) {
val renderer = contents.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer")
if (renderer != null) {
parsePlaylistItem(renderer, tracks)
}
}
}
​val continuation = extractContinuation(YTMusicTextUtils.findFirst(json, "musicPlaylistShelfContinuation"))
return Pair(tracks, continuation)
}
​private fun parsePlaylistItem(r: JSONObject, out: MutableList<Track>) {
val columns = r.optJSONArray("flexColumns") ?: return
val title = YTMusicTextUtils.flexText(columns, 0) ?: return
​var videoId = r.optJSONObject("playlistItemData")?.optString("videoId")
if (videoId.isNullOrBlank() || videoId == "null") {
videoId = r.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
}
​if (!videoId.isNullOrBlank() && videoId != "null") {
val subtitleCol = columns.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
val subtitle = YTMusicTextUtils.runsText(subtitleCol)
​val uploader = YTMusicSearchParser.extractArtistFromSubtitle(subtitleCol, subtitle)
?: subtitle.substringBefore("•").trim().ifBlank { "YouTube" }
​val source = YTMusicSearchParser.detectSource(videoId, r)
​out.add(
Track(
id = videoId,
title = title,
artist = uploader,
thumbnailUrl = YTMusicThumbnailUtils.thumbnailOf(r.optJSONObject("thumbnail")),
durationSeconds = YTMusicTextUtils.parseDuration(YTMusicTextUtils.fixedText(r)),
source = source
)
)
}
}
​private fun extractTrackCount(text: String): Int {
val match = Regex("(\d+)\s*(músicas|songs|faixas|tracks)", RegexOption.IGNORE_CASE).find(text)
return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
}
​private fun extractDescription(json: JSONObject, header: JSONObject?): String? {
val shelf = YTMusicTextUtils.findFirst(json, "musicDescriptionShelfRenderer")
val shelfDesc = YTMusicTextUtils.extractRunsText(shelf?.optJSONObject("description"))
if (shelfDesc.isNotBlank()) return shelfDesc
​val headerDesc = YTMusicTextUtils.extractRunsText(header?.optJSONObject("description"))
if (headerDesc.isNotBlank()) return headerDesc
​return null
}
​private fun extractContinuation(node: JSONObject?): String? {
val continuations = node?.optJSONArray("continuations") ?: return null
return continuations.optJSONObject(0)
?.optJSONObject("nextContinuationData")
?.optString("continuation")
?.takeIf { it.isNotBlank() }
}
}