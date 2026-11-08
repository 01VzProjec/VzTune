package com.vztechcore.vztune.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Playlists the user favorited; shown in the Library "Playlists favoritas" tab. */
@Singleton
class FavoritePlaylistsStore @Inject constructor(
    @ApplicationContext context: Context,
) : JsonListStore<StoredPlaylist>(
    context = context,
    storeName = "vztune_favorite_playlists",
    keyName = "playlists",
    serializer = StoredPlaylist.serializer(),
    idOf = { it.id },
)
