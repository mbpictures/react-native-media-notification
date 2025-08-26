package com.mediacontrols

import android.content.Context
import android.content.SharedPreferences
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.google.common.collect.ImmutableList
import androidx.core.content.edit

class MediaStore {
    interface Listener {
        fun onMediaItemsUpdated(parentId: String?, itemCount: Int)
    }

    companion object {
        var Instance: MediaStore = MediaStore()
        private const val PREFS_NAME = "MediaStorePrefs"
        private const val KEY_HIERARCHY = "media_items_hierarchy"
        private var sharedPrefs: SharedPreferences? = null
        fun init(context: Context) {
            if (sharedPrefs != null) return
            sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            Instance.loadHierarchy()
        }
    }

    private var mediaItemsHierarchy: MediaElement? = null
    private val listeners = mutableListOf<Listener>()

    private fun saveHierarchy() {
        if (sharedPrefs == null) return
        val json = mediaItemsHierarchy?.toJson() ?: return
        sharedPrefs!!.edit { putString(KEY_HIERARCHY, json) }
    }

    private fun loadHierarchy() {
        if (sharedPrefs == null) return
        val json = sharedPrefs!!.getString(KEY_HIERARCHY, null) ?: return
        mediaItemsHierarchy = MediaElement.fromJson(json)
    }

    fun build(data: ReadableMap?) {
        mediaItemsHierarchy = MediaElement.from(data)
        saveHierarchy()
        val rootId = mediaItemsHierarchy?.id ?: "root"

        onMediaItemsUpdated(rootId, itemCount(rootId))
    }

    fun itemCount(parentId: String): Int {
        if (mediaItemsHierarchy == null) return 0
        val parentElement = findElementById(mediaItemsHierarchy!!, parentId) ?: return 0

        fun countRecursive(element: MediaElement?): Int {
            if (element?.items == null) return 0
            return element.items.size + element.items.sumOf { countRecursive(it) }
        }

        return countRecursive(parentElement)
    }

    fun getRoot(): LibraryResult<MediaItem>? {
        if (mediaItemsHierarchy == null) return LibraryResult.ofItem(
            MediaItem.Builder()
                .setMediaMetadata(MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
                )
                .setMediaId("root")
                .build(),
            null
        )
        return LibraryResult.ofItem(
            MediaItem.Builder()
                .setMediaId(mediaItemsHierarchy!!.id)
                .setMediaMetadata(mediaItemsHierarchy!!)
                .build(),
            null
        )
    }

    fun getChildren(parentId: String, page: Int, pageSize: Int): LibraryResult<ImmutableList<MediaItem>>? {
        if (mediaItemsHierarchy == null) return null

        val parentElement = findElementById(mediaItemsHierarchy!!, parentId) ?: return null
        val items = parentElement.items ?: return LibraryResult.ofItemList(emptyList(), null)

        val mediaItems = items.paginate(page, pageSize).map { element ->
            buildMediaItem(element)
        }

        return LibraryResult.ofItemList(mediaItems, null)
    }

    fun getItem(id: String): LibraryResult<MediaItem>? {
        if (mediaItemsHierarchy == null) return null
        val element = findElementById(mediaItemsHierarchy!!, id) ?: return null
        return LibraryResult.ofItem(
            buildMediaItem(element),
            null
        )
    }

    fun search(query: String, page: Int, pageSize: Int): LibraryResult<ImmutableList<MediaItem>>? {
        if (mediaItemsHierarchy == null) return null
        val words = query.split(" ").map { it.trim().lowercase() }.filter { it.length > 1 }

        val results = searchElements(mediaItemsHierarchy!!, words)
        return LibraryResult.ofItemList(results.paginate(page, pageSize).map { buildMediaItem(it) }, null)
    }

    private fun findElementById(element: MediaElement, id: String): MediaElement? {
        if (element.id == id) return element
        element.items?.forEach { child ->
            val found = findElementById(child, id)
            if (found != null) return found
        }
        return null
    }

    private fun searchElements(element: MediaElement, words: List<String>): MutableList<MediaElement> {
        val results = mutableListOf<MediaElement>()
        if (elementMatchesWords(element, words)) {
            results.add(element)
        }
        element.items?.forEach { child ->
            searchElements(child, words)
        }

        return results
    }

    private fun elementMatchesWords(element: MediaElement, words: List<String>): Boolean {
        val searchText = (element.title ?: "") + " " + (element.artist ?: "") + " " + (element.album ?: "")
        val lowerSearchText = searchText.lowercase()
        for (word in words) {
            if (!lowerSearchText.contains(word)) {
                return false
            }
        }
        return true
    }

    inline fun MediaItem.Builder.setMediaMetadata(element: MediaElement): MediaItem.Builder {
        return this.setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(element.title)
                .setArtist(element.artist)
                .setAlbumTitle(element.album)
                .setIsPlayable(element.playable == true)
                .setIsBrowsable(element.browsable == true)
                .setDurationMs(if (element.duration != null && element.duration > 0) element.duration * 1000 else 0)
                .setArtworkUri(element.artUri?.toUri())
                .setMediaType(element.mediaType)
                .build()
        )
    }

    private fun buildMediaItem(element: MediaElement): MediaItem {
        return MediaItem.Builder()
            .setMediaId(element.id)
            .setTag(element)
            .setUri(element.artUri ?: "")
            .setMediaMetadata(element)
            .build()
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    private fun onMediaItemsUpdated(parentId: String?, itemCount: Int) {
        listeners.forEach { it.onMediaItemsUpdated(parentId, itemCount) }
    }
}

data class MediaElement(
    val id: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val duration: Long?,
    val artUri: String?,
    val playable: Boolean?,
    val browsable: Boolean?,
    val mediaType: Int?,
    val items: List<MediaElement>?
) {
    companion object {
        fun from(data: ReadableArray?): MediaElement? {
            return from(data?.getMap(0))
        }

        fun from(data: ReadableMap?): MediaElement? {
            if (data == null) return null
            val items = if (data.hasKey("items")) {
                val arr = data.getArray("items")
                val list = mutableListOf<MediaElement>()
                if (arr != null) {
                    for (i in 0 until arr.size()) {
                        val item = from(arr.getMap(i))
                        if (item != null) {
                            list.add(item)
                        }
                    }
                }
                list
            } else {
                null
            }

            return MediaElement(
                id = if (data.hasKey("id")) data.getString("id")?: "root" else "root",
                title = if (data.hasKey("title")) data.getString("title") else null,
                artist = if (data.hasKey("artist")) data.getString("artist") else null,
                album = if (data.hasKey("album")) data.getString("album") else null,
                duration = if (data.hasKey("duration")) data.getDouble("duration").toLong() else null,
                artUri = if (data.hasKey("artwork")) data.getString("artwork") else null,
                playable = if (data.hasKey("playable")) data.getBoolean("playable") else null,
                browsable = if (data.hasKey("browsable")) data.getBoolean("browsable") else null,
                mediaType = parseType(data.getString("mediaType")),
                items = items
            )
        }

        fun parseType(type: String?): Int? {
            return when (type?.lowercase()) {
                "music" -> MediaMetadata.MEDIA_TYPE_MUSIC
                "podcast" -> MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE
                "radio" -> MediaMetadata.MEDIA_TYPE_RADIO_STATION
                "album" -> MediaMetadata.MEDIA_TYPE_ALBUM
                "artist" -> MediaMetadata.MEDIA_TYPE_ARTIST
                "genre" -> MediaMetadata.MEDIA_TYPE_GENRE
                "playlist" -> MediaMetadata.MEDIA_TYPE_PLAYLIST
                else -> null
            }
        }

        fun fromJson(json: String): MediaElement? {
            try {
                val jsonObj = org.json.JSONObject(json)
                val itemsJson = jsonObj.optJSONArray("items")
                val itemsList = mutableListOf<MediaElement>()
                if (itemsJson != null) {
                    for (i in 0 until itemsJson.length()) {
                        val itemJson = itemsJson.getJSONObject(i).toString()
                        val item = fromJson(itemJson)
                        if (item != null) itemsList.add(item)
                    }
                }
                fun nullableString(s: String): String? = if (s.isEmpty()) null else s
                return MediaElement(
                    id = jsonObj.optString("id", "root"),
                    title = nullableString(jsonObj.optString("title", "")),
                    artist = nullableString(jsonObj.optString("artist", "")),
                    album = nullableString(jsonObj.optString("album", "")),
                    duration = if (jsonObj.has("duration")) jsonObj.getLong("duration") else null,
                    artUri = nullableString(jsonObj.optString("artUri", "")),
                    playable = if (jsonObj.has("playable")) jsonObj.getBoolean("playable") else null,
                    browsable = if (jsonObj.has("browsable")) jsonObj.getBoolean("browsable") else null,
                    mediaType = if (jsonObj.has("mediaType")) jsonObj.getInt("mediaType") else null,
                    items = if (itemsList.isNotEmpty()) itemsList else null
                )
            } catch (_: Exception) {
                return null
            }
        }
    }
    fun toJson(): String {
        val jsonObj = org.json.JSONObject()
        jsonObj.put("id", id)
        if (title != null) jsonObj.put("title", title)
        if (artist != null) jsonObj.put("artist", artist)
        if (album != null) jsonObj.put("album", album)
        if (duration != null) jsonObj.put("duration", duration)
        if (artUri != null) jsonObj.put("artUri", artUri)
        if (playable != null) jsonObj.put("playable", playable)
        if (browsable != null) jsonObj.put("browsable", playable)
        if (mediaType != null) jsonObj.put("mediaType", mediaType)
        if (items != null) {
            val itemsArray = org.json.JSONArray()
            for (item in items) {
                itemsArray.put(org.json.JSONObject(item.toJson()))
            }
            jsonObj.put("items", itemsArray)
        }
        return jsonObj.toString()
    }
}