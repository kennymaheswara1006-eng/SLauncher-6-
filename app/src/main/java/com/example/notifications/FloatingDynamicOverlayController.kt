package com.example.notifications

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.R
import com.example.data.AppLanguage
import com.example.data.AppRepository
import com.example.data.LocaleHelper
import com.example.ui.components.NotificationAppIcon
import com.example.ui.components.formatRelativeNotificationTime
import com.example.ui.theme.SLauncher6Theme
import com.example.ui.theme.SLauncherColors
import com.example.ui.theme.softDeepCapsule
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Manages the system-wide floating Dynamic Pop-Up capsule (`TYPE_APPLICATION_OVERLAY`)
 * hosted directly by `SLauncherNotificationListener`.
 *
 * Ensures notifications keep appearing as SLauncher 6's soft deep-shadow capsule
 * even when opening other apps (WhatsApp, YouTube, browser, games), while suppressing itself
 * whenever SLauncher 6 Home/Lock Screen is in the foreground so capsules never duplicate.
 */
class FloatingDynamicOverlayController(
  context: Context,
  private val onSystemDismiss: (String) -> Unit,
) {

  private val appContext = context.applicationContext
  private val windowManager =
    appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
  private val mainHandler = Handler(Looper.getMainLooper())
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private var observerJob: Job? = null
  private var overlayView: ComposeView? = null
  private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
  private val activeFloatingItem = MutableStateFlow<DynamicNotificationItem?>(null)
  private var lastShownBannerTriggerId: Long = 0L

  fun start() {
    if (observerJob != null) return
    lastShownBannerTriggerId = DynamicNotificationStore.bannerTriggerId.value
    observerJob =
      scope.launch {
        combine(
            DynamicNotificationStore.bannerNotification,
            DynamicNotificationStore.bannerTriggerId,
            ForegroundActivityTracker.isLauncherInForeground,
          ) { bannerItem, triggerId, launcherForeground ->
            Triple(bannerItem, triggerId, launcherForeground)
          }
          .collect { (bannerItem, triggerId, launcherForeground) ->
            if (launcherForeground) {
              activeFloatingItem.value?.let { shown ->
                DynamicNotificationStore.clearBanner(shown.key)
              }
              activeFloatingItem.value = null
              detachOverlay()
              lastShownBannerTriggerId = triggerId
              return@collect
            }

            if (triggerId > lastShownBannerTriggerId) {
              lastShownBannerTriggerId = triggerId
              if (bannerItem != null && shouldShowFloatingOverlay()) {
                activeFloatingItem.value = bannerItem
                attachOverlayIfNeeded()
              }
            } else {
              val current = activeFloatingItem.value
              if (current != null && bannerItem == null) {
                activeFloatingItem.value = null
                detachOverlay()
              }
            }
          }
      }
  }

  fun destroy() {
    observerJob?.cancel()
    observerJob = null
    activeFloatingItem.value = null
    detachOverlay()
    scope.cancel()
  }

  private fun shouldShowFloatingOverlay(): Boolean {
    if (!canDrawOverlays(appContext)) return false
    val prefs = appContext.getSharedPreferences(AppRepository.PREFS_NAME, Context.MODE_PRIVATE)
    val dynamicEnabled = prefs.getBoolean(AppRepository.KEY_DYNAMIC_NOTIF_ENABLED, true)
    val floatingEnabled = prefs.getBoolean(AppRepository.KEY_FLOATING_OVERLAY_POPUP_ENABLED, true)
    return dynamicEnabled && floatingEnabled
  }

  private fun getLocalizedContext(): Context {
    val prefs = appContext.getSharedPreferences(AppRepository.PREFS_NAME, Context.MODE_PRIVATE)
    val tag = prefs.getString("pref_language", AppLanguage.SYSTEM.tag)
    return LocaleHelper.localizedContext(appContext, AppLanguage.fromTag(tag))
  }

  private fun attachOverlayIfNeeded() {
    mainHandler.post {
      if (overlayView != null) return@post
      val wm = windowManager ?: return@post
      if (!canDrawOverlays(appContext)) return@post

      val lifecycleOwner = OverlayLifecycleOwner()
      lifecycleOwner.onCreate()
      lifecycleOwner.onStart()
      lifecycleOwner.onResume()

      val themedContext = getLocalizedContext()
      val composeView =
        ComposeView(themedContext).apply {
          setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
          setViewTreeLifecycleOwner(lifecycleOwner)
          setViewTreeViewModelStoreOwner(lifecycleOwner)
          setViewTreeSavedStateRegistryOwner(lifecycleOwner)
          setContent {
            SLauncher6Theme {
              val currentItem by activeFloatingItem.collectAsStateWithLifecycle()
              val allItems by DynamicNotificationStore.notifications.collectAsStateWithLifecycle()
              currentItem?.let { item ->
                FloatingDynamicCapsuleOverlay(
                  item = item,
                  queuedCount = (allItems.size - 1).coerceAtLeast(0),
                  onOpen = {
                    runCatching { item.contentIntent?.send() }
                    autoHideFloatingItem(item.key)
                  },
                  onAutoHide = {
                    autoHideFloatingItem(item.key)
                  },
                  onDismiss = {
                    dismissFloatingItem(item.key, removeFromStore = true)
                  },
                )
              }
            }
          }
        }

      val layoutType =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
          @Suppress("DEPRECATION")
          WindowManager.LayoutParams.TYPE_PHONE
        }

      val params =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
              WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
              WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
          )
          .apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dpToPx(appContext, 36)
          }

      runCatching {
        wm.addView(composeView, params)
        overlayView = composeView
        overlayLifecycleOwner = lifecycleOwner
      }.onFailure {
        lifecycleOwner.onDestroy()
      }
    }
  }

  private fun autoHideFloatingItem(key: String) {
    DynamicNotificationStore.clearBanner(key)
    activeFloatingItem.value = null
    detachOverlay()
  }

  private fun dismissFloatingItem(key: String, removeFromStore: Boolean) {
    if (removeFromStore) {
      onSystemDismiss(key)
      DynamicNotificationStore.remove(key)
    } else {
      DynamicNotificationStore.clearBanner(key)
    }
    activeFloatingItem.value = null
    detachOverlay()
  }

  private fun detachOverlay() {
    mainHandler.post {
      val view = overlayView ?: return@post
      overlayView = null
      runCatching { windowManager?.removeViewImmediate(view) }
      overlayLifecycleOwner?.onDestroy()
      overlayLifecycleOwner = null
    }
  }

  private fun dpToPx(context: Context, dp: Int): Int =
    (dp * context.resources.displayMetrics.density).roundToInt()

  companion object {
    fun canDrawOverlays(context: Context): Boolean =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
      } else {
        true
      }

    fun openOverlayPermissionSettings(context: Context) {
      runCatching {
        val intent =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
              Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
              Uri.parse("package:${context.packageName}"),
            )
          } else {
            Intent(Settings.ACTION_SETTINGS)
          }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
      }
    }
  }
}

@Composable
private fun FloatingDynamicCapsuleOverlay(
  item: DynamicNotificationItem,
  queuedCount: Int,
  onOpen: () -> Unit,
  onAutoHide: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val scope = rememberCoroutineScope()
  var isExpanded by remember(item.key) { mutableStateOf(false) }
  val swipeOffsetX = remember(item.key) { Animatable(0f) }
  val timerRemaining = remember(item.key) { Animatable(1f) }
  val exitAnim = remember(item.key) { Animatable(0f) }

  LaunchedEffect(item.key, item.postTime, isExpanded) {
    if (!isExpanded) {
      timerRemaining.snapTo(1f)
      timerRemaining.animateTo(
        targetValue = 0f,
        animationSpec = tween(durationMillis = 3200, easing = LinearEasing),
      )
      exitAnim.animateTo(1f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
      onAutoHide()
    }
  }

  val swipeAlpha = (1f - (swipeOffsetX.value / 520f)).coerceIn(0f, 1f)
  val exitAlpha = (1f - exitAnim.value).coerceIn(0f, 1f)

  Box(
    contentAlignment = Alignment.TopCenter,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
  ) {
    AnimatedContent(
      targetState = item,
      transitionSpec = {
        (slideInVertically(tween(260)) { -it / 2 } +
          scaleIn(initialScale = 0.92f, animationSpec = tween(260)) +
          fadeIn(tween(220))) togetherWith fadeOut(tween(150))
      },
      label = "floating_dynamic_capsule",
    ) { current ->
      Box(
        modifier =
          Modifier.widthIn(max = 380.dp)
            .fillMaxWidth()
            .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
            .graphicsLayer {
              alpha = swipeAlpha * exitAlpha
              translationY = -exitAnim.value * 24.dp.toPx()
              scaleX = 1f - exitAnim.value * 0.06f
              scaleY = 1f - exitAnim.value * 0.06f
            }
            .softDeepCapsule(
              cornerRadius = if (isExpanded) 28.dp else 32.dp,
              elevation = 24.dp,
              isActive = true,
              accentColor = SLauncherColors.AccentPrimary,
            )
            .animateContentSize(
              animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
            )
            .pointerInput(current.key) {
              detectHorizontalDragGestures(
                onHorizontalDrag = { change, dragAmount ->
                  change.consume()
                  scope.launch {
                    swipeOffsetX.snapTo((swipeOffsetX.value + dragAmount).coerceAtLeast(0f))
                  }
                },
                onDragEnd = {
                  scope.launch {
                    if (swipeOffsetX.value > 140f) {
                      swipeOffsetX.animateTo(900f, tween(180))
                      onDismiss()
                    } else {
                      swipeOffsetX.animateTo(0f, spring(dampingRatio = 0.78f))
                    }
                  }
                },
                onDragCancel = {
                  scope.launch { swipeOffsetX.animateTo(0f, spring()) }
                },
              )
            }
            .clickable { isExpanded = !isExpanded },
      ) {
        // Subtle ambient cyan glow & countdown progress hairline
        Canvas(modifier = Modifier.matchParentSize()) {
          val glowCenter = Offset(34.dp.toPx(), size.height / 2f)
          drawCircle(
            brush =
              Brush.radialGradient(
                colors = listOf(SLauncherColors.AccentPrimary.copy(alpha = 0.24f), Color.Transparent),
                center = glowCenter,
                radius = 58.dp.toPx(),
              ),
            center = glowCenter,
            radius = 58.dp.toPx(),
          )
          if (!isExpanded && timerRemaining.value > 0.02f) {
            val usableW = (size.width - 48.dp.toPx()).coerceAtLeast(0f) * timerRemaining.value
            val lineY = size.height - 2.5.dp.toPx()
            val cx = size.width / 2f
            drawLine(
              color = SLauncherColors.AccentPrimary.copy(alpha = 0.75f),
              start = Offset(cx - usableW / 2f, lineY),
              end = Offset(cx + usableW / 2f, lineY),
              strokeWidth = 2.dp.toPx(),
              cap = StrokeCap.Round,
            )
          }
        }

        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            NotificationAppIcon(
              item = current,
              size = 40.dp,
              showLiveDot = true,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = current.appName,
                  style =
                    MaterialTheme.typography.labelSmall.copy(
                      fontWeight = FontWeight.SemiBold,
                      fontSize = 11.sp,
                    ),
                  color = SLauncherColors.AccentPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "•",
                  style = MaterialTheme.typography.labelSmall,
                  color = SLauncherColors.TextMuted,
                )
                Text(
                  text = formatRelativeNotificationTime(context, current.postTime),
                  style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                  color = SLauncherColors.TextMuted,
                  maxLines = 1,
                )
              }

              Spacer(modifier = Modifier.height(1.dp))

              Text(
                text = current.title,
                style =
                  MaterialTheme.typography.titleMedium.copy(
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                  ),
                color = SLauncherColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )

              if (current.text.isNotBlank()) {
                Text(
                  text = current.text,
                  style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                  color = SLauncherColors.TextSecondary,
                  maxLines = if (isExpanded) 4 else 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            if (queuedCount > 0) {
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "+$queuedCount",
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                  ),
                color = SLauncherColors.AccentPrimary,
                modifier =
                  Modifier.clip(CircleShape)
                    .background(SLauncherColors.AccentContainer)
                    .border(0.8.dp, SLauncherColors.AccentPrimary.copy(alpha = 0.45f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }

          if (isExpanded) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                  Modifier.clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = null,
                  tint = SLauncherColors.TextSecondary,
                  modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                  text = stringResource(R.string.action_dismiss),
                  style = MaterialTheme.typography.labelMedium,
                  color = SLauncherColors.TextSecondary,
                )
              }

              if (current.contentIntent != null) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier =
                    Modifier.clip(RoundedCornerShape(50))
                      .background(SLauncherColors.AccentContainer)
                      .border(
                        0.8.dp,
                        SLauncherColors.AccentPrimary.copy(alpha = 0.55f),
                        RoundedCornerShape(50),
                      )
                      .clickable { onOpen() }
                      .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = SLauncherColors.AccentPrimary,
                    modifier = Modifier.size(14.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = stringResource(R.string.action_open),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = SLauncherColors.AccentPrimary,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

private class OverlayLifecycleOwner :
  LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

  private val lifecycleRegistry = LifecycleRegistry(this)
  private val store = ViewModelStore()
  private val savedStateController = SavedStateRegistryController.create(this)

  override val lifecycle: Lifecycle
    get() = lifecycleRegistry

  override val viewModelStore: ViewModelStore
    get() = store

  override val savedStateRegistry: SavedStateRegistry
    get() = savedStateController.savedStateRegistry

  fun onCreate() {
    savedStateController.performAttach()
    savedStateController.performRestore(Bundle())
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
  }

  fun onStart() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
  }

  fun onResume() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
  }

  fun onDestroy() {
    runCatching {
      lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
      lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
      lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
    store.clear()
  }
}
