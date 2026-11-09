package com.vztechcore.vztune.data.prefs.models

import java.util.UUID

private const val LOCAL_PLAYLIST_PREFIX = "local:"

fun isLocalPlaylistId(id: String): Boolean = id.startsWith(LOCAL_PLAYLIST_PREFIX)

fun newLocalPlaylistId(): String = LOCAL_PLAYLIST_PREFIX + UUID.randomUUID()