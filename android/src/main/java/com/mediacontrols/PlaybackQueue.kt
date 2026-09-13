package com.mediacontrols

import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.util.UnstableApi
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.views.imagehelper.ResourceDrawableIdHelper
import java.io.File
import kotlin.math.abs

data class QueueEntry(
    // Unique within the queue; the same track may be queued more than once.
    val uid: String,
    val id: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val artwork: String?,
    val duration: Double?
)

/**
 * The playback queue as set from JS. It is published as the player's playlist so
 * Android Auto can show it; JS stays in charge of what actually plays.
 */
class PlaybackQueue(
    val entries: List<QueueEntry> = emptyList(),
    currentIndex: Int = 0,
    val title: String? = null
) {
    // Last resolved position, used to pick the right entry for tracks queued twice.
    @Volatile
    var currentIndex: Int = currentIndex
        private set

    fun isEmpty(): Boolean = entries.isEmpty()

    /** Index of the entry playing [mediaId], or [C.INDEX_UNSET] if it isn't queued. */
    fun resolveIndex(mediaId: String): Int {
        if (entries.getOrNull(currentIndex)?.id == mediaId) return currentIndex
        val index = entries.indices
            .filter { entries[it].id == mediaId }
            .minByOrNull { abs(it - currentIndex) }
            ?: return C.INDEX_UNSET
        currentIndex = index
        return index
    }

    companion object {
        fun from(items: ReadableArray?, currentIndex: Int, title: String?): PlaybackQueue {
            val entries = mutableListOf<QueueEntry>()
            val uids = mutableSetOf<String>()
            val size = items?.size() ?: 0
            // Keep every slot, even malformed ones, so indices match the JS array.
            for (i in 0 until size) {
                val map = items?.getMap(i)
                val id = map?.optString("id") ?: ""
                var uid = map?.optString("queueId") ?: id
                if (!uids.add(uid)) {
                    uid = "$i:$uid"
                    while (!uids.add(uid)) uid += "'"
                }
                entries.add(
                    QueueEntry(
                        uid = uid,
                        id = id,
                        title = map?.optString("title"),
                        artist = map?.optString("artist"),
                        album = map?.optString("album"),
                        artwork = map?.optString("artwork"),
                        duration = if (map?.hasKey("duration") == true && !map.isNull("duration")) {
                            map.getDouble("duration")
                        } else {
                            null
                        }
                    )
                )
            }
            val index = if (entries.isEmpty()) 0 else currentIndex.coerceIn(0, entries.size - 1)
            return PlaybackQueue(entries, index, title)
        }

        private fun ReadableMap.optString(key: String): String? =
            if (hasKey(key) && !isNull(key)) getString(key) else null
    }
}

@UnstableApi
fun QueueEntry.toMediaItemData(context: Context): MediaItemData {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setDurationMs(duration?.times(1000)?.toLong())
        // Only a URI: artwork bytes would be decoded and parceled for every queue entry.
        .setArtworkUri(artworkUri(context, artwork))
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .build()

    val mediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(metadata)
        .build()

    return MediaItemData.Builder(uid)
        .setMediaItem(mediaItem)
        .setDurationUs(duration?.times(1_000_000)?.toLong() ?: C.TIME_UNSET)
        .setIsSeekable(true)
        .build()
}

private fun artworkUri(context: Context, artwork: String?): Uri? {
    if (artwork.isNullOrEmpty()) return null
    if (URLUtil.isValidUrl(artwork)) return artwork.toUri()
    if (artwork.startsWith("/")) return Uri.fromFile(File(artwork))
    val resId = ResourceDrawableIdHelper.instance.getResourceDrawableId(context, artwork)
    return if (resId > 0) "android.resource://${context.packageName}/$resId".toUri() else null
}
