package com.example.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.BatteryStatus
import com.example.data.WeatherCondition
import com.example.data.WeatherUiState
import com.example.ui.theme.SLauncherColors
import com.example.ui.theme.WallpaperLegibilityShadow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ambient header keeping the crisp digital clock, date & battery on the left,
 * paired with a live Weather & Location ("Kota, Kecamatan") module on the right side of the clock.
 */
@Composable
fun ClockDateBatteryHeader(
  weatherState: WeatherUiState,
  onRequestLocationPermission: () -> Unit,
  onRefreshWeather: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
  var batteryStatus by remember { mutableStateOf(readBatteryStatus(context)) }

  DisposableEffect(context, lifecycleOwner) {
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
          when (intent?.action) {
            Intent.ACTION_TIME_TICK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED -> {
              currentTimeMillis = System.currentTimeMillis()
            }
            Intent.ACTION_BATTERY_CHANGED -> {
              batteryStatus = parseBatteryIntent(intent)
            }
          }
        }
      }

    val filter =
      IntentFilter().apply {
        addAction(Intent.ACTION_TIME_TICK)
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
        addAction(Intent.ACTION_DATE_CHANGED)
        addAction(Intent.ACTION_BATTERY_CHANGED)
      }

    var isRegistered = false
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> {
          currentTimeMillis = System.currentTimeMillis()
          batteryStatus = readBatteryStatus(context)
          if (!isRegistered) {
            context.registerReceiver(receiver, filter)
            isRegistered = true
          }
        }
        Lifecycle.Event.ON_STOP -> {
          if (isRegistered) {
            runCatching { context.unregisterReceiver(receiver) }
            isRegistered = false
          }
        }
        else -> Unit
      }
    }

    lifecycleOwner.lifecycle.addObserver(observer)
    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
      context.registerReceiver(receiver, filter)
      isRegistered = true
    }

    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      if (isRegistered) {
        runCatching { context.unregisterReceiver(receiver) }
      }
    }
  }

  val is24Hour = DateFormat.is24HourFormat(context)
  val locale =
    remember(context) {
      context.resources.configuration.locales.get(0) ?: Locale.getDefault()
    }
  val timePattern = if (is24Hour) "HH:mm" else "h:mm"
  val timeText =
    remember(currentTimeMillis, timePattern, locale) {
      SimpleDateFormat(timePattern, locale).format(Date(currentTimeMillis))
    }
  val dateText =
    remember(currentTimeMillis, locale) {
      SimpleDateFormat("EEE, d MMM", locale).format(Date(currentTimeMillis))
    }

  val batteryCd = stringResource(R.string.cd_battery_indicator, batteryStatus.levelPercent)

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
  ) {
    // Left Column: Unchanged oversized Digital Clock + Date + Battery pill
    Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.weight(1f),
    ) {
      Text(
        text = timeText,
        style =
          MaterialTheme.typography.displayLarge.copy(
            color = SLauncherColors.TextPrimary,
            fontFeatureSettings = "tnum",
          ),
        maxLines = 1,
      )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = dateText,
          style =
            TextStyle(
              fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
              fontWeight = FontWeight.Medium,
              fontSize = 14.sp,
              color = SLauncherColors.TextPrimary.copy(alpha = 0.92f),
              shadow = WallpaperLegibilityShadow,
            ),
          maxLines = 1,
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier.clip(RoundedCornerShape(50))
              .background(SLauncherColors.GlassSurfaceGradient)
              .border(1.dp, SLauncherColors.GlassBorderGradient, RoundedCornerShape(50))
              .padding(horizontal = 10.dp, vertical = 4.dp)
              .semantics { contentDescription = batteryCd },
        ) {
          val batteryIcon =
            when {
              batteryStatus.isCharging -> Icons.Rounded.BatteryChargingFull
              batteryStatus.levelPercent >= 90 -> Icons.Rounded.BatteryFull
              else -> Icons.Rounded.BatteryStd
            }
          val batteryTint =
            when {
              batteryStatus.isCharging -> SLauncherColors.BatteryCharging
              batteryStatus.levelPercent <= 15 -> SLauncherColors.WarningSoft
              else -> SLauncherColors.TextPrimary
            }
          Icon(
            imageVector = batteryIcon,
            contentDescription = null,
            tint = batteryTint,
            modifier = Modifier.size(14.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text =
              if (batteryStatus.isCharging) {
                stringResource(R.string.battery_charging_format, batteryStatus.levelPercent)
              } else {
                stringResource(R.string.battery_format, batteryStatus.levelPercent)
              },
            style =
              MaterialTheme.typography.labelSmall.copy(
                color = SLauncherColors.TextPrimary,
                fontFeatureSettings = "tnum",
              ),
            maxLines = 1,
          )
        }
      }
    }

    Spacer(modifier = Modifier.width(10.dp))

    // Right Column: Kota, Kecamatan above Expressive Cloud Icon + Temperature
    WeatherLocationBadge(
      weatherState = weatherState,
      onRequestLocationPermission = onRequestLocationPermission,
      onRefreshWeather = onRefreshWeather,
    )
  }
}

@Composable
private fun WeatherLocationBadge(
  weatherState: WeatherUiState,
  onRequestLocationPermission: () -> Unit,
  onRefreshWeather: () -> Unit,
) {
  val cardShape = RoundedCornerShape(22.dp)

  Column(
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.Center,
    modifier =
      Modifier.clip(cardShape)
        .background(SLauncherColors.GlassFloatingCardGradient)
        .border(1.dp, SLauncherColors.GlassBorderGradient, cardShape)
        .clickable {
          when (weatherState) {
            WeatherUiState.NeedsPermission -> onRequestLocationPermission()
            else -> onRefreshWeather()
          }
        }
        .padding(horizontal = 14.dp, vertical = 10.dp)
        .testTag("weather_location_widget"),
  ) {
    when (weatherState) {
      WeatherUiState.NeedsPermission -> {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Rounded.LocationOn,
            contentDescription = null,
            tint = SLauncherColors.AccentPrimary,
            modifier = Modifier.size(14.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = stringResource(R.string.weather_allow_location),
            style = MaterialTheme.typography.labelSmall,
            color = SLauncherColors.TextPrimary,
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        WeatherConditionIcon(
          condition = WeatherCondition.PARTLY_CLOUDY,
          modifier = Modifier.size(34.dp),
        )
      }

      WeatherUiState.Loading -> {
        Text(
          text = stringResource(R.string.weather_loading),
          style = MaterialTheme.typography.labelSmall,
          color = SLauncherColors.TextSecondary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        CircularProgressIndicator(
          color = SLauncherColors.AccentPrimary,
          strokeWidth = 2.dp,
          modifier = Modifier.size(22.dp),
        )
      }

      WeatherUiState.Unavailable -> {
        Row(verticalAlignment = Alignment.CenterVertically) {
          WeatherConditionIcon(
            condition = WeatherCondition.CLOUDY,
            modifier = Modifier.size(28.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.weather_unavailable),
            style = MaterialTheme.typography.labelSmall,
            color = SLauncherColors.TextSecondary,
          )
        }
      }

      is WeatherUiState.Ready -> {
        val data = weatherState.data
        val conditionLabel =
          when (data.condition) {
            WeatherCondition.SUNNY -> stringResource(R.string.weather_sunny)
            WeatherCondition.PARTLY_CLOUDY -> stringResource(R.string.weather_partly_cloudy)
            WeatherCondition.CLOUDY -> stringResource(R.string.weather_cloudy)
            WeatherCondition.DRIZZLE -> stringResource(R.string.weather_drizzle)
            WeatherCondition.RAIN -> stringResource(R.string.weather_rain)
            WeatherCondition.THUNDERSTORM -> stringResource(R.string.weather_thunderstorm)
          }

        // Info Lokasi ("Kota, Kecamatan") directly above the cloud icon
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.End,
        ) {
          Icon(
            imageVector = Icons.Rounded.LocationOn,
            contentDescription = null,
            tint = SLauncherColors.AccentPrimary,
            modifier = Modifier.size(12.dp),
          )
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = data.locationLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = SLauncherColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Cloud / Weather Icon + Temperature + Condition label
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          WeatherConditionIcon(
            condition = data.condition,
            modifier =
              Modifier.size(34.dp).semantics {
                contentDescription = conditionLabel
              },
          )
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = "${data.temperatureCelsius}°",
              style =
                MaterialTheme.typography.headlineSmall.copy(
                  fontSize = 21.sp,
                  fontWeight = FontWeight.Bold,
                  fontFeatureSettings = "tnum",
                ),
              color = SLauncherColors.TextPrimary,
              maxLines = 1,
            )
            Text(
              text = conditionLabel,
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              color = SLauncherColors.TextSecondary,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}

/**
 * Custom Canvas-drawn weather icons representing Sunny (Terik), Partly Cloudy,
 * Cloudy (Mendung), Drizzle (Gerimis), Rain (Hujan), and Thunderstorm (Hujan Badai).
 */
@Composable
fun WeatherConditionIcon(
  condition: WeatherCondition,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height

    fun drawSun(center: Offset, radius: Float, withRays: Boolean) {
      drawCircle(color = SLauncherColors.SunAmber, radius = radius, center = center)
      if (withRays) {
        val rayInner = radius * 1.32f
        val rayOuter = radius * 1.72f
        for (i in 0 until 8) {
          val angle = (i * 45.0) * (PI / 180.0)
          val start =
            Offset(
              center.x + (cos(angle) * rayInner).toFloat(),
              center.y + (sin(angle) * rayInner).toFloat(),
            )
          val end =
            Offset(
              center.x + (cos(angle) * rayOuter).toFloat(),
              center.y + (sin(angle) * rayOuter).toFloat(),
            )
          drawLine(
            color = SLauncherColors.SunAmber,
            start = start,
            end = end,
            strokeWidth = (w * 0.065f).coerceAtLeast(1.5f),
            cap = StrokeCap.Round,
          )
        }
      }
    }

    fun drawCloud(baseTop: Float, tint: Color) {
      val cloudWidth = w * 0.78f
      val cloudLeft = (w - cloudWidth) / 2f
      val pillHeight = h * 0.28f
      drawRoundRect(
        color = tint,
        topLeft = Offset(cloudLeft, baseTop + h * 0.14f),
        size = Size(cloudWidth, pillHeight),
        cornerRadius = CornerRadius(pillHeight / 2f, pillHeight / 2f),
      )
      drawCircle(
        color = tint,
        radius = w * 0.19f,
        center = Offset(w * 0.44f, baseTop + h * 0.16f),
      )
      drawCircle(
        color = tint,
        radius = w * 0.14f,
        center = Offset(w * 0.64f, baseTop + h * 0.21f),
      )
    }

    when (condition) {
      WeatherCondition.SUNNY -> {
        drawSun(center = Offset(w * 0.5f, h * 0.5f), radius = w * 0.24f, withRays = true)
      }

      WeatherCondition.PARTLY_CLOUDY -> {
        drawSun(center = Offset(w * 0.68f, h * 0.34f), radius = w * 0.16f, withRays = true)
        drawCloud(baseTop = h * 0.26f, tint = SLauncherColors.CloudBright)
      }

      WeatherCondition.CLOUDY -> {
        drawCloud(baseTop = h * 0.22f, tint = SLauncherColors.CloudMuted.copy(alpha = 0.75f))
        drawCloud(baseTop = h * 0.34f, tint = SLauncherColors.CloudBright)
      }

      WeatherCondition.DRIZZLE -> {
        drawCloud(baseTop = h * 0.18f, tint = SLauncherColors.CloudBright)
        val dropRadius = (w * 0.045f).coerceAtLeast(1.5f)
        for (xRatio in listOf(0.34f, 0.50f, 0.66f)) {
          drawCircle(
            color = SLauncherColors.RainCyan,
            radius = dropRadius,
            center = Offset(w * xRatio, h * 0.76f),
          )
        }
      }

      WeatherCondition.RAIN -> {
        drawCloud(baseTop = h * 0.16f, tint = SLauncherColors.CloudMuted)
        val stroke = (w * 0.06f).coerceAtLeast(1.6f)
        for (xRatio in listOf(0.33f, 0.50f, 0.67f)) {
          drawLine(
            color = SLauncherColors.RainCyan,
            start = Offset(w * xRatio, h * 0.66f),
            end = Offset(w * (xRatio - 0.07f), h * 0.87f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
          )
        }
      }

      WeatherCondition.THUNDERSTORM -> {
        drawCloud(baseTop = h * 0.14f, tint = SLauncherColors.CloudMuted)
        val bolt =
          Path().apply {
            moveTo(w * 0.52f, h * 0.55f)
            lineTo(w * 0.41f, h * 0.74f)
            lineTo(w * 0.52f, h * 0.74f)
            lineTo(w * 0.45f, h * 0.94f)
            lineTo(w * 0.63f, h * 0.69f)
            lineTo(w * 0.52f, h * 0.69f)
            close()
          }
        drawPath(path = bolt, color = SLauncherColors.SunAmber)
        drawLine(
          color = SLauncherColors.RainCyan,
          start = Offset(w * 0.28f, h * 0.66f),
          end = Offset(w * 0.22f, h * 0.84f),
          strokeWidth = (w * 0.055f).coerceAtLeast(1.5f),
          cap = StrokeCap.Round,
        )
        drawLine(
          color = SLauncherColors.StormIndigo,
          start = Offset(w * 0.74f, h * 0.66f),
          end = Offset(w * 0.68f, h * 0.84f),
          strokeWidth = (w * 0.055f).coerceAtLeast(1.5f),
          cap = StrokeCap.Round,
        )
      }
    }
  }
}

private fun readBatteryStatus(context: Context): BatteryStatus {
  val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
  return if (intent != null) parseBatteryIntent(intent) else BatteryStatus()
}

private fun parseBatteryIntent(intent: Intent): BatteryStatus {
  val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
  val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
  val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
  val percent =
    if (level >= 0 && scale > 0) {
      ((level * 100f) / scale).toInt().coerceIn(0, 100)
    } else {
      100
    }
  val isCharging =
    status == BatteryManager.BATTERY_STATUS_CHARGING ||
      status == BatteryManager.BATTERY_STATUS_FULL
  return BatteryStatus(levelPercent = percent, isCharging = isCharging)
}
