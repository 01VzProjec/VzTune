package com.vztechcore.vztune.data.prefs.store

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.vztechcore.vztune.data.prefs.models.FollowedArtist

@Singleton
class FollowedArtistsStore @Inject constructor(
    @ApplicationContext context: Context,
) : JsonListStore<FollowedArtist>(
    context = context,
    storeName = "vztune_followed_artists",
    keyName = "artists",
    serializer = FollowedArtist.serializer(),
    idOf = { it.id },
) {
    fun isFollowing(artistId: String) = contains(artistId)
}
