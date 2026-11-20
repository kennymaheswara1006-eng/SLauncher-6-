package com.example.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.notifications.DynamicNotificationStore
import com.example.notifications.SLauncherNotificationListener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Universal media session & lyrics repository supporting Spotify, YouTube Music, Apple Music,
 * SoundCloud, local music players, and any Android app publishing a MediaSession.
 * Uses MediaSessionManager + NotificationListenerService (zero polling background services)
 * and fetches synced/plain lyrics via LRCLIB with a small bounded LruCache.
 */
class MusicRepository(context: Context) {

  private val appContext = context.applicationContext
  private val listenerComponent = ComponentName(appContext, SLauncherNotificationListener::class.java)
  private val mediaSessionManager =
    appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
  private val audioManager =
    appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
  private val mainHandler = Handler(Looper.getMainLooper())

  private val _currentTrack = MutableStateFlow<MusicPlaybackInfo?>(null)
  val currentTrack: StateFlow<MusicPlaybackInfo?> = _currentTrack.asStateFlow()

  private val _requiresNotificationAccess = MutableStateFlow(false)
  val requiresNotificationAccess: StateFlow<Boolean> = _requiresNotificationAccess.asStateFlow()

  private val lyricsCache = LruCache<String, LyricsState>(12)

  private var activeController: MediaController? = null
  private var isObserving = false

  private val controllerCallback =
    object : MediaController.Callback() {
      override fun onPlaybackStateChanged(state: PlaybackState?) {
        publishFromActiveController()
      }

      override fun onMetadataChanged(metadata: MediaMetadata?) {
        publishFromActiveController()
      }

      override fun onSessionDestroyed() {
        refreshSessions()
      }
    }

  private val sessionsChangedListener =
    MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
      selectController(controllers.orEmpty())
    }

  fun startOrRefreshObserving() {
    val manager = mediaSessionManager ?: return
    if (!DynamicNotificationStore.isNotificationAccessGranted(appContext)) {
      _requiresNotificationAccess.value = true
      detachActiveController()
      _currentTrack.value = null
      return
    }

    _requiresNotificationAccess.value = false
    if (!isObserving) {
      try {
        manager.addOnActiveSessionsChangedListener(
          sessionsChangedListener,
          listenerComponent,
          mainHandler,
        )
        isObserving = true
      } catch (_: SecurityException) {
        _requiresNotificationAccess.value = true
      }
    }
    refreshSessions()
  }

  fun refreshSessions() {
    val manager = mediaSessionManager ?: return
    if (!DynamicNotificationStore.isNotificationAccessGranted(appContext)) {
      _requiresNotificationAccess.value = true
      detachActiveController()
      _currentTrack.value = null
      return
    }

    _requiresNotificationAccess.value = false
    try {
      val controllers = manager.getActiveSessions(listenerComponent)
      selectController(controllers)
    } catch (_: SecurityException) {
      _requiresNotificationAccess.value = true
      detachActiveController()
      _currentTrack.value = null
    }
  }

  fun stopObserving() {
    if (isObserving) {
      runCatching { mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener) }
      isObserving = false
    }
    detachActiveController()
  }

  private fun selectController(controllers: List<MediaController>) {
    val chosen =
      controllers.firstOrNull { ctrl ->
        val state = ctrl.playbackState?.state
        state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
      } ?: controllers.firstOrNull { ctrl ->
        val meta = ctrl.metadata
        !meta?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() ||
          !meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).isNullOrBlank()
      } ?: controllers.firstOrNull()

    if (chosen?.sessionToken != activeController?.sessionToken) {
      detachActiveController()
      activeController = chosen
      activeController?.registerCallback(controllerCallback, mainHandler)
    }
    publishFromActiveController()
  }

  private fun detachActiveController() {
    activeController?.let { runCatching { it.unregisterCallback(controllerCallback) } }
    activeController = null
  }

  private fun publishFromActiveController() {
    val controller = activeController
    if (controller == null) {
      _currentTrack.value = null
      return
    }

    val metadata = controller.metadata
    val playbackState = controller.playbackState
    val rawTitle =
      metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotEmpty() }
        ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.trim()?.takeIf {
          it.isNotEmpty()
        }

    if (rawTitle == null) {
      _currentTrack.value = null
      return
    }

    val artist =
      metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()?.takeIf { it.isNotEmpty() }
        ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.trim()?.takeIf {
          it.isNotEmpty()
        }
        ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.trim().orEmpty()

    val album =
      metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim().orEmpty()

    val durationMs =
      (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L)
    val stateCode = playbackState?.state ?: PlaybackState.STATE_NONE
    val isPlaying =
      stateCode == PlaybackState.STATE_PLAYING || stateCode == PlaybackState.STATE_BUFFERING
    val positionMs = (playbackState?.position ?: 0L).coerceAtLeast(0L)
    val updateTime =
      playbackState?.lastPositionUpdateTime?.takeIf { it > 0L } ?: SystemClock.elapsedRealtime()

    val embeddedLyrics =
      metadata?.getString("android.media.metadata.LYRIC")?.trim()?.takeIf { it.isNotEmpty() }

    val artworkBitmap = extractScaledArtwork(metadata)
    val appLabel = resolveAppLabel(controller.packageName)

    _currentTrack.value =
      MusicPlaybackInfo(
        packageName = controller.packageName,
        appName = appLabel,
        title = rawTitle,
        artist = artist,
        album = album,
        isPlaying = isPlaying,
        durationMs = durationMs,
        positionMs = positionMs,
        positionUpdatedAtElapsedMs = updateTime,
        artwork = artworkBitmap,
        embeddedLyrics = embeddedLyrics,
      )
  }

  private fun extractScaledArtwork(metadata: MediaMetadata?): ImageBitmap? {
    if (metadata == null) return null
    val raw =
      metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
        ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        ?: return null
    return try {
      val maxDim = 180
      val scaled =
        if (raw.width <= maxDim && raw.height <= maxDim) {
          raw
        } else {
          Bitmap.createScaledBitmap(raw, maxDim, maxDim, true)
        }
      scaled.asImageBitmap()
    } catch (_: Exception) {
      null
    } catch (_: OutOfMemoryError) {
      null
    }
  }

  private fun resolveAppLabel(packageName: String): String {
    return try {
      val info = appContext.packageManager.getApplicationInfo(packageName, 0)
      appContext.packageManager.getApplicationLabel(info).toString()
    } catch (_: Exception) {
      packageName
    }
  }

  fun togglePlayPause() {
    val controller = activeController
    if (controller != null) {
      val state = controller.playbackState?.state
      val playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
      if (playing) {
        controller.transportControls.pause()
      } else {
        controller.transportControls.play()
      }
    } else {
      dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }
  }

  fun skipToNext() {
    val controller = activeController
    if (controller != null) {
      controller.transportControls.skipToNext()
    } else {
      dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    }
  }

  fun skipToPrevious() {
    val controller = activeController
    if (controller != null) {
      controller.transportControls.skipToPrevious()
    } else {
      dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }
  }

  fun seekTo(positionMs: Long) {
    activeController?.transportControls?.seekTo(positionMs.coerceAtLeast(0L))
  }

  fun openPlayerApp(packageName: String): Boolean {
    return try {
      val launchIntent =
        appContext.packageManager.getLaunchIntentForPackage(packageName)?.apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false
      appContext.startActivity(launchIntent)
      true
    } catch (_: Exception) {
      false
    }
  }

  private fun dispatchMediaKey(keyCode: Int) {
    val manager = audioManager ?: return
    val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
    val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
    runCatching {
      manager.dispatchMediaKeyEvent(down)
      manager.dispatchMediaKeyEvent(up)
    }
  }

  suspend fun fetchLyrics(track: MusicPlaybackInfo): LyricsState {
    if (!track.embeddedLyrics.isNullOrBlank()) {
      return parseRawLyrics(synced = track.embeddedLyrics, plain = track.embeddedLyrics)
    }

    val cacheKey = track.trackCacheKey
    lyricsCache.get(cacheKey)?.let { return it }

    val resolved =
      withContext(Dispatchers.IO) {
        fetchFromLrcLibGet(track) ?: fetchFromLrcLibSearch(track) ?: LyricsState.NotFound
      }

    lyricsCache.put(cacheKey, resolved)
    return resolved
  }

  private fun fetchFromLrcLibGet(track: MusicPlaybackInfo): LyricsState? {
    if (track.title.isBlank() || track.artist.isBlank()) return null
    return try {
      val titleEnc = URLEncoder.encode(track.title, "UTF-8")
      val artistEnc = URLEncoder.encode(track.artist, "UTF-8")
      val sb = StringBuilder("https://lrclib.net/api/get?track_name=$titleEnc&artist_name=$artistEnc")
      if (track.album.isNotBlank()) {
        sb.append("&album_name=").append(URLEncoder.encode(track.album, "UTF-8"))
      }
      if (track.durationMs > 0L) {
        sb.append("&duration=").append(track.durationMs / 1000L)
      }
      val jsonText = httpGet(URL(sb.toString())) ?: return null
      val json = JSONObject(jsonText)
      parseJsonLyrics(json)
    } catch (_: Exception) {
      null
    }
  }

  private fun fetchFromLrcLibSearch(track: MusicPlaybackInfo): LyricsState? {
    val query = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" ").trim()
    if (query.isEmpty()) return null
    return try {
      val qEnc = URLEncoder.encode(query, "UTF-8")
      val jsonText = httpGet(URL("https://lrclib.net/api/search?q=$qEnc")) ?: return null
      val array = JSONArray(jsonText)
      for (i in 0 until minOf(array.length(), 4)) {
        val item = array.optJSONObject(i) ?: continue
        val parsed = parseJsonLyrics(item)
        if (parsed != null) return parsed
      }
      null
    } catch (_: Exception) {
      null
    }
  }

  private fun parseJsonLyrics(json: JSONObject): LyricsState? {
    val synced = json.optString("syncedLyrics").trim().takeIf { it.isNotEmpty() && it != "null" }
    val plain = json.optString("plainLyrics").trim().takeIf { it.isNotEmpty() && it != "null" }
    if (synced == null && plain == null) return null
    return parseRawLyrics(synced = synced, plain = plain)
  }

  private fun parseRawLyrics(synced: String?, plain: String?): LyricsState {
    if (!synced.isNullOrBlank()) {
      val parsedSynced = parseLrcLines(synced)
      if (parsedSynced.isNotEmpty()) {
        return LyricsState.Synced(parsedSynced)
      }
    }
    val plainSource = plain ?: synced.orEmpty()
    val plainLines =
      plainSource
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return if (plainLines.isNotEmpty()) LyricsState.Plain(plainLines) else LyricsState.NotFound
  }

  private fun parseLrcLines(lrcText: String): List<LyricLine> {
    val regex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]""")
    val result = mutableListOf<LyricLine>()
    for (rawLine in lrcText.lines()) {
      val matches = regex.findAll(rawLine).toList()
      if (matches.isEmpty()) continue
      val text = rawLine.replace(regex, "").trim()
      if (text.isEmpty()) continue
      for (match in matches) {
        val min = match.groupValues[1].toLongOrNull() ?: continue
        val sec = match.groupValues[2].toLongOrNull() ?: continue
        val fracStr = match.groupValues.getOrNull(3).orEmpty()
        val millis =
          when (fracStr.length) {
            1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
            2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
            3 -> fracStr.toLongOrNull() ?: 0L
            else -> 0L
          }
        result.add(LyricLine(timeMs = (min * 60_000L) + (sec * 1000L) + millis, text = text))
      }
    }
    return result.sortedBy { it.timeMs }
  }

  private fun httpGet(url: URL): String? {
    var connection: HttpURLConnection? = null
    return try {
      connection =
        (url.openConnection() as HttpURLConnection).apply {
          requestMethod = "GET"
          setRequestProperty("User-Agent", "SLauncher6/1.0 (Android)")
          connectTimeout = 6000
          readTimeout = 6000
        }
      if (connection.responseCode !in 200..299) return null
      BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
    } catch (_: Exception) {
      null
    } finally {
      connection?.disconnect()
    }
  }
}
