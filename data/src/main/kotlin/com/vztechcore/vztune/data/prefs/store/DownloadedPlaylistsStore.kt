package com.vztechcore.vztune.data.prefs.store

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.vztechcore.vztune.data.prefs.models.DownloadedPlaylist

/** Playlists/álbuns baixados; mostrados na aba "Playlists baixadas" da Biblioteca. */
@Singleton
class DownloadedPlaylistsStore @Inject constructor(
    @ApplicationContext context: Context,
) : JsonListStore<DownloadedPlaylist>(
    context = context,
    storeName = "vztune_downloaded_playlists",
    keyName = "playlists",
    serializer = DownloadedPlaylist.serializer(),
    idOf = { it.id },
)
