package com.example.data

import android.app.ActivityOptions
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Lightweight, memory-safe repository for querying installed apps via PackageManager,
 * downscaling & caching icons in a bounded LruCache, applying wallpapers, and persisting settings
 * in SharedPreferences.
 */
class AppRepository(context: Context) {

  private val appContext = context.applicationContext
  private val packageManager: PackageManager = appContext.packageManager
  private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  val selfPackageName: String = appContext.packageName

  // Bound icon memory cache to ~4 MB max (or 1/32 of available heap) for 2GB RAM devices
  private val iconCache: LruCache<String, ImageBitmap> = run {
    val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
    val cacheSizeKb = minOf(maxOf(maxMemoryKb / 32, 1024), 4096)
    object : LruCache<String, ImageBitmap>(cacheSizeKb) {
      override fun sizeOf(key: String, value: ImageBitmap): Int {
        return maxOf((value.width * value.height * 4) / 1024, 1)
      }
    }
  }

  /**
   * Queries all launchable applications including SLauncher 6 itself so it remains visible
   * and accessible even after SLauncher 6 is enabled as the active Home Launcher.
   */
  suspend fun loadInstalledApps(): List<AppModel> =
    withContext(Dispatchers.IO) {
      val launcherIntent =
        Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }

      val resolveInfos =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
          )
        } else {
          @Suppress("DEPRECATION")
          packageManager.queryIntentActivities(launcherIntent, 0)
        }

      resolveInfos
        .asSequence()
        .filter { it.activityInfo != null }
        .map { resolveInfo ->
          val activityInfo = resolveInfo.activityInfo
          val rawLabel = resolveInfo.loadLabel(packageManager)?.toString()?.trim().orEmpty()
          val safeLabel = rawLabel.ifEmpty { activityInfo.packageName }
          AppModel(
            packageName = activityInfo.packageName,
            activityName = activityInfo.name,
            label = safeLabel,
            sectionLetter = normalizeSectionLetter(safeLabel),
          )
        }
        .distinctBy { it.componentKey }
        .sortedWith(
          compareBy<AppModel> { it.sectionLetter == '#' }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
        )
        .toList()
    }

  suspend fun loadAppIcon(app: AppModel, iconSizePx: Int = DEFAULT_ICON_PX): ImageBitmap? {
    iconCache.get(app.componentKey)?.let { return it }

    return withContext(Dispatchers.IO) {
      iconCache.get(app.componentKey)?.let { return@withContext it }
      try {
        val componentName = ComponentName(app.packageName, app.activityName)
        val drawable = packageManager.getActivityIcon(componentName)
        val bitmap = drawableToScaledBitmap(drawable, iconSizePx)
        val imageBitmap = bitmap.asImageBitmap()
        iconCache.put(app.componentKey, imageBitmap)
        imageBitmap
      } catch (_: Exception) {
        null
      } catch (_: OutOfMemoryError) {
        iconCache.evictAll()
        null
      }
    }
  }

  fun pruneCache(validKeys: Set<String>) {
    val snapshotKeys = iconCache.snapshot().keys
    for (key in snapshotKeys) {
      if (key !in validKeys) {
        iconCache.remove(key)
      }
    }
  }

  fun getPinnedComponentKeys(installedApps: List<AppModel>): List<String> {
    if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
      val seeded = seedDefaultFavorites(installedApps)
      savePinnedComponentKeys(seeded)
      return seeded
    }
    val raw = prefs.getString(KEY_PINNED_COMPONENTS, "").orEmpty()
    if (raw.isBlank()) return emptyList()
    val validKeys = installedApps.map { it.componentKey }.toSet()
    return raw
      .split(DELIMITER)
      .filter { it.isNotBlank() && it in validKeys }
      .distinct()
      .take(MAX_FAVORITES)
  }

  fun savePinnedComponentKeys(componentKeys: List<String>) {
    val normalized = componentKeys.distinct().take(MAX_FAVORITES)
    prefs
      .edit()
      .putBoolean(KEY_INITIALIZED, true)
      .putString(KEY_PINNED_COMPONENTS, normalized.joinToString(DELIMITER))
      .apply()
  }

  fun loadSettings(): LauncherSettings {
    val customPaths =
      prefs.getString(KEY_CUSTOM_WALLPAPER_PATHS, "")
        .orEmpty()
        .split(DELIMITER)
        .filter { it.isNotBlank() && File(it).exists() }
        .distinct()
    val rawSelectedCustom = prefs.getString(KEY_SELECTED_CUSTOM_WALLPAPER, null)
    val validSelectedCustom =
      rawSelectedCustom?.takeIf { it in customPaths && File(it).exists() }
        ?: customPaths.firstOrNull()
    val rawChoice =
      WallpaperChoice.fromKey(
        prefs.getString(KEY_WALLPAPER_CHOICE, WallpaperChoice.ALPINE_PEAKS.key)
      )
    val resolvedChoice =
      if (rawChoice == WallpaperChoice.CUSTOM && validSelectedCustom == null) {
        WallpaperChoice.ALPINE_PEAKS
      } else {
        rawChoice
      }

    return LauncherSettings(
      language = AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.tag)),
      homeAppsLayout =
        HomeAppsLayout.fromKey(prefs.getString(KEY_HOME_LAYOUT, HomeAppsLayout.GRID.key)),
      wallpaperChoice = resolvedChoice,
      customWallpaperPaths = customPaths,
      selectedCustomWallpaperPath = validSelectedCustom,
      welcomePopupEnabled = prefs.getBoolean(KEY_WELCOME_ENABLED, true),
      customWelcomeText = prefs.getString(KEY_WELCOME_TEXT, "").orEmpty(),
      lockScreenEnabled = prefs.getBoolean(KEY_LOCKSCREEN_ENABLED, false),
      dynamicNotificationsEnabled = prefs.getBoolean(KEY_DYNAMIC_NOTIF_ENABLED, true),
      appIconOpensSettingsOnly = prefs.getBoolean(KEY_APP_ICON_SETTINGS_ONLY, true),
      bootWelcomeEnabled = prefs.getBoolean(KEY_BOOT_WELCOME_ENABLED, true),
      permissionsPromptShown = prefs.getBoolean(KEY_PERMISSIONS_PROMPT_SHOWN, false),
      hiddenComponentKeys =
        prefs.getString(KEY_HIDDEN_COMPONENTS, "")
          .orEmpty()
          .split(DELIMITER)
          .filter { it.isNotBlank() }
          .toSet(),
    )
  }

  fun saveSettings(settings: LauncherSettings) {
    prefs
      .edit()
      .putString(KEY_LANGUAGE, settings.language.tag)
      .putString(KEY_HOME_LAYOUT, settings.homeAppsLayout.key)
      .putString(KEY_WALLPAPER_CHOICE, settings.wallpaperChoice.key)
      .putString(KEY_CUSTOM_WALLPAPER_PATHS, settings.customWallpaperPaths.joinToString(DELIMITER))
      .putString(KEY_SELECTED_CUSTOM_WALLPAPER, settings.selectedCustomWallpaperPath)
      .putBoolean(KEY_WELCOME_ENABLED, settings.welcomePopupEnabled)
      .putString(KEY_WELCOME_TEXT, settings.customWelcomeText)
      .putBoolean(KEY_LOCKSCREEN_ENABLED, settings.lockScreenEnabled)
      .putBoolean(KEY_DYNAMIC_NOTIF_ENABLED, settings.dynamicNotificationsEnabled)
      .putBoolean(KEY_APP_ICON_SETTINGS_ONLY, settings.appIconOpensSettingsOnly)
      .putBoolean(KEY_BOOT_WELCOME_ENABLED, settings.bootWelcomeEnabled)
      .putBoolean(KEY_PERMISSIONS_PROMPT_SHOWN, settings.permissionsPromptShown)
      .putString(KEY_HIDDEN_COMPONENTS, settings.hiddenComponentKeys.joinToString(DELIMITER))
      .apply()
  }

  fun isBootWelcomeEnabled(): Boolean =
    prefs.getBoolean(KEY_BOOT_WELCOME_ENABLED, true)

  fun markPendingBootWelcome() {
    prefs.edit().putBoolean(KEY_PENDING_BOOT_WELCOME, true).apply()
  }

  fun consumePendingBootWelcome(): Boolean {
    val pending = prefs.getBoolean(KEY_PENDING_BOOT_WELCOME, false)
    if (pending) {
      prefs.edit().putBoolean(KEY_PENDING_BOOT_WELCOME, false).apply()
    }
    return pending
  }

  /**
   * Imports a user-chosen photo from Android Photo Picker into app-private storage,
   * downscaled cleanly to <= 1600px on the longest edge so 2GB RAM devices never OOM.
   */
  suspend fun importCustomWallpaper(uri: Uri): String? =
    withContext(Dispatchers.IO) {
      try {
        val dir = File(appContext.filesDir, CUSTOM_WALLPAPER_DIR)
        if (!dir.exists()) dir.mkdirs()

        val decodedBitmap = decodeScaledBitmapFromUri(uri, maxDimension = 1600) ?: return@withContext null
        val outFile = File(dir, "wp_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { stream ->
          decodedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        }
        decodedBitmap.recycle()
        outFile.absolutePath
      } catch (_: Exception) {
        null
      } catch (_: OutOfMemoryError) {
        null
      }
    }

  fun deleteCustomWallpaper(path: String) {
    runCatching {
      val dir = File(appContext.filesDir, CUSTOM_WALLPAPER_DIR).canonicalFile
      val target = File(path).canonicalFile
      if (target.parentFile == dir && target.exists()) {
        target.delete()
      }
    }
  }

  private fun decodeScaledBitmapFromUri(uri: Uri, maxDimension: Int): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val source = ImageDecoder.createSource(appContext.contentResolver, uri)
      return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val srcW = info.size.width
        val srcH = info.size.height
        val longest = max(srcW, srcH)
        if (longest > maxDimension && longest > 0) {
          val scale = maxDimension.toFloat() / longest.toFloat()
          decoder.setTargetSize(
            (srcW * scale).roundToInt().coerceAtLeast(1),
            (srcH * scale).roundToInt().coerceAtLeast(1),
          )
        }
      }
    }

    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    appContext.contentResolver.openInputStream(uri)?.use { stream ->
      BitmapFactory.decodeStream(stream, null, boundsOpts)
    }
    var sampleSize = 1
    val longest = max(boundsOpts.outWidth, boundsOpts.outHeight)
    while (longest / sampleSize > maxDimension * 2) {
      sampleSize *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return appContext.contentResolver.openInputStream(uri)?.use { stream ->
      BitmapFactory.decodeStream(stream, null, decodeOpts)
    }
  }

  suspend fun applySystemWallpaper(choice: WallpaperChoice, customPath: String? = null): Boolean =
    withContext(Dispatchers.IO) {
      val manager = WallpaperManager.getInstance(appContext)
      if (choice == WallpaperChoice.CUSTOM && !customPath.isNullOrBlank()) {
        val file = File(customPath)
        if (!file.exists()) return@withContext false
        return@withContext try {
          file.inputStream().use { stream ->
            manager.setStream(stream, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
          }
          true
        } catch (_: Exception) {
          try {
            file.inputStream().use { stream -> manager.setStream(stream) }
            true
          } catch (_: Exception) {
            false
          }
        }
      }

      val resId = choice.drawableRes ?: return@withContext false
      try {
        manager.setResource(resId, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        true
      } catch (_: Exception) {
        try {
          manager.setResource(resId)
          true
        } catch (_: Exception) {
          false
        }
      }
    }

  fun isDefaultHomeLauncher(): Boolean {
    return try {
      val homeIntent =
        Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
      val resolved =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          packageManager.resolveActivity(
            homeIntent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
          )
        } else {
          @Suppress("DEPRECATION")
          packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
      resolved?.activityInfo?.packageName == selfPackageName
    } catch (_: Exception) {
      false
    }
  }

  fun launchApp(app: AppModel): Boolean {
    return try {
      val intent =
        Intent(Intent.ACTION_MAIN).apply {
          addCategory(Intent.CATEGORY_LAUNCHER)
          component = ComponentName(app.packageName, app.activityName)
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
      val options =
        ActivityOptions.makeCustomAnimation(
          appContext,
          android.R.anim.fade_in,
          android.R.anim.fade_out,
        )
      appContext.startActivity(intent, options.toBundle())
      true
    } catch (_: Exception) {
      val fallback = packageManager.getLaunchIntentForPackage(app.packageName)
      if (fallback != null) {
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(fallback)
        true
      } else {
        false
      }
    }
  }

  fun openAppInfo(packageName: String) {
    try {
      val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
          data = Uri.fromParts("package", packageName, null)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      appContext.startActivity(intent)
    } catch (_: Exception) {
      // Ignore if Settings activity is unavailable
    }
  }

  fun openDefaultHomeSettings() {
    try {
      val intent =
        Intent(Settings.ACTION_HOME_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
      appContext.startActivity(intent)
    } catch (_: Exception) {
      runCatching {
        appContext.startActivity(
          Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
      }
    }
  }

  fun openSystemSecuritySettings() {
    try {
      val intent =
        Intent(Settings.ACTION_SECURITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
      appContext.startActivity(intent)
    } catch (_: Exception) {
      runCatching {
        appContext.startActivity(
          Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
      }
    }
  }

  private fun seedDefaultFavorites(installedApps: List<AppModel>): List<String> {
    if (installedApps.isEmpty()) return emptyList()
    val nonSelfApps = installedApps.filterNot { it.packageName == selfPackageName }
    val pool = nonSelfApps.ifEmpty { installedApps }

    val preferredKeywords =
      listOf("dialer", "phone", "messaging", "mms", "chrome", "browser", "camera", "gallery", "photos", "settings")

    val matched = mutableListOf<String>()
    for (keyword in preferredKeywords) {
      if (matched.size >= DEFAULT_SEED_COUNT) break
      val found =
        pool.firstOrNull { app ->
          app.packageName.contains(keyword, ignoreCase = true) && app.componentKey !in matched
        }
      if (found != null) {
        matched.add(found.componentKey)
      }
    }

    for (app in pool) {
      if (matched.size >= DEFAULT_SEED_COUNT) break
      if (app.componentKey !in matched) {
        matched.add(app.componentKey)
      }
    }
    return matched
  }

  private fun drawableToScaledBitmap(drawable: Drawable, targetSizePx: Int): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
      val src = drawable.bitmap
      if (src.width <= targetSizePx && src.height <= targetSizePx) {
        return src
      }
      return Bitmap.createScaledBitmap(src, targetSizePx, targetSizePx, true)
    }

    val bitmap = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, targetSizePx, targetSizePx)
    drawable.draw(canvas)
    return bitmap
  }

  private fun normalizeSectionLetter(label: String): Char {
    val first = label.firstOrNull()?.uppercaseChar() ?: return '#'
    return if (first in 'A'..'Z') first else '#'
  }

  companion object {
    const val PREFS_NAME = "slauncher6_prefs"
    private const val KEY_PINNED_COMPONENTS = "pinned_components"
    private const val KEY_INITIALIZED = "favorites_initialized"
    private const val KEY_LANGUAGE = "pref_language"
    private const val KEY_HOME_LAYOUT = "pref_home_layout"
    private const val KEY_WALLPAPER_CHOICE = "pref_wallpaper_choice"
    private const val KEY_CUSTOM_WALLPAPER_PATHS = "pref_custom_wallpaper_paths"
    private const val KEY_SELECTED_CUSTOM_WALLPAPER = "pref_selected_custom_wallpaper"
    private const val CUSTOM_WALLPAPER_DIR = "custom_wallpapers"
    const val KEY_WELCOME_ENABLED = "pref_welcome_enabled"
    private const val KEY_WELCOME_TEXT = "pref_welcome_text"
    const val KEY_LOCKSCREEN_ENABLED = "pref_lockscreen_enabled"
    const val KEY_DYNAMIC_NOTIF_ENABLED = "pref_dynamic_notif_enabled"
    private const val KEY_APP_ICON_SETTINGS_ONLY = "pref_app_icon_settings_only"
    private const val KEY_BOOT_WELCOME_ENABLED = "pref_boot_welcome_enabled"
    private const val KEY_PENDING_BOOT_WELCOME = "pref_pending_boot_welcome"
    private const val KEY_PERMISSIONS_PROMPT_SHOWN = "pref_permissions_prompt_shown"
    private const val KEY_HIDDEN_COMPONENTS = "pref_hidden_components"

    private const val DELIMITER = "|"
    private const val DEFAULT_ICON_PX = 108
    private const val DEFAULT_SEED_COUNT = 5
    const val MAX_FAVORITES = 7
  }
}
