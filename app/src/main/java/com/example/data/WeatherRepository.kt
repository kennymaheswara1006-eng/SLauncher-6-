package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Lightweight, zero-bloat Weather & Reverse Geocoding repository.
 * Uses Android's built-in LocationManager + Geocoder (with BigDataCloud fallback)
 * and Open-Meteo's keyless forecast API, cached for 30 minutes in SharedPreferences.
 */
class WeatherRepository(context: Context) {

  private val appContext = context.applicationContext
  private val prefs = appContext.getSharedPreferences(AppRepository.PREFS_NAME, Context.MODE_PRIVATE)

  fun hasLocationPermission(): Boolean {
    val coarse =
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val fine =
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return coarse || fine
  }

  fun loadCachedWeather(): WeatherData? {
    val location = prefs.getString(KEY_WEATHER_LOCATION, null) ?: return null
    val temp = prefs.getInt(KEY_WEATHER_TEMP, Int.MIN_VALUE)
    if (temp == Int.MIN_VALUE) return null
    val condition = WeatherCondition.fromKey(prefs.getString(KEY_WEATHER_CONDITION, null))
    val isDay = prefs.getBoolean(KEY_WEATHER_IS_DAY, true)
    val updatedAt = prefs.getLong(KEY_WEATHER_UPDATED_AT, 0L)
    return WeatherData(
      locationLabel = location,
      temperatureCelsius = temp,
      condition = condition,
      isDay = isDay,
      updatedAtMillis = updatedAt,
    )
  }

  suspend fun fetchWeather(forceRefresh: Boolean = false): WeatherData? =
    withContext(Dispatchers.IO) {
      if (!hasLocationPermission()) return@withContext null

      val cached = loadCachedWeather()
      val ageMillis = System.currentTimeMillis() - (cached?.updatedAtMillis ?: 0L)
      if (!forceRefresh && cached != null && ageMillis < CACHE_TTL_MILLIS) {
        return@withContext cached
      }

      val location = getCurrentOrLastLocation() ?: return@withContext cached
      val lat = location.latitude
      val lon = location.longitude

      val weatherPair = fetchOpenMeteo(lat, lon) ?: return@withContext cached
      val cityDistrict = resolveCityAndDistrict(lat, lon) ?: cached?.locationLabel.orEmpty()
      val formattedLocation =
        cityDistrict.ifBlank {
          String.format(Locale.US, "%.2f, %.2f", lat, lon)
        }

      val fresh =
        WeatherData(
          locationLabel = formattedLocation,
          temperatureCelsius = weatherPair.first,
          condition = weatherPair.second,
          isDay = weatherPair.third,
          updatedAtMillis = System.currentTimeMillis(),
        )
      saveCachedWeather(fresh)
      fresh
    }

  private fun saveCachedWeather(data: WeatherData) {
    prefs
      .edit()
      .putString(KEY_WEATHER_LOCATION, data.locationLabel)
      .putInt(KEY_WEATHER_TEMP, data.temperatureCelsius)
      .putString(KEY_WEATHER_CONDITION, data.condition.key)
      .putBoolean(KEY_WEATHER_IS_DAY, data.isDay)
      .putLong(KEY_WEATHER_UPDATED_AT, data.updatedAtMillis)
      .apply()
  }

  @SuppressLint("MissingPermission")
  private suspend fun getCurrentOrLastLocation(): Location? {
    val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

    val providers =
      listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
      )
    val bestLast =
      providers
        .mapNotNull { provider ->
          runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull { it.time }

    if (bestLast != null && System.currentTimeMillis() - bestLast.time < LOCATION_MAX_AGE_MILLIS) {
      return bestLast
    }

    val activeProvider =
      when {
        runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
          LocationManager.NETWORK_PROVIDER
        runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
          LocationManager.GPS_PROVIDER
        else -> null
      } ?: return bestLast

    val fresh =
      withTimeoutOrNull(6500L) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          suspendCancellableCoroutine { cont ->
            val cancellationSignal = android.os.CancellationSignal()
            cont.invokeOnCancellation { cancellationSignal.cancel() }
            try {
              manager.getCurrentLocation(
                activeProvider,
                cancellationSignal,
                appContext.mainExecutor,
              ) { loc ->
                if (cont.isActive) cont.resume(loc)
              }
            } catch (_: Exception) {
              if (cont.isActive) cont.resume(null)
            }
          }
        } else {
          suspendCancellableCoroutine { cont ->
            val listener =
              object : LocationListener {
                override fun onLocationChanged(location: Location) {
                  if (cont.isActive) cont.resume(location)
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
              }
            cont.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
            try {
              @Suppress("DEPRECATION")
              manager.requestSingleUpdate(activeProvider, listener, Looper.getMainLooper())
            } catch (_: Exception) {
              if (cont.isActive) cont.resume(null)
            }
          }
        }
      }

    return fresh ?: bestLast
  }

  private suspend fun resolveCityAndDistrict(lat: Double, lon: Double): String? {
    // 1. Try Android Geocoder first
    if (Geocoder.isPresent()) {
      val geocoder = Geocoder(appContext, Locale.getDefault())
      val address: Address? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          withTimeoutOrNull(4000L) {
            suspendCancellableCoroutine { cont ->
              try {
                geocoder.getFromLocation(lat, lon, 1) { addresses ->
                  if (cont.isActive) cont.resume(addresses.firstOrNull())
                }
              } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
              }
            }
          }
        } else {
          @Suppress("DEPRECATION")
          runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull()
        }

      if (address != null) {
        val formatted = formatAddressToCityDistrict(address)
        if (!formatted.isNullOrBlank()) return formatted
      }
    }

    // 2. Fallback to keyless BigDataCloud reverse geocoding
    return fetchBigDataCloudCityDistrict(lat, lon)
  }

  private fun formatAddressToCityDistrict(address: Address): String? {
    val city =
      (address.subAdminArea ?: address.locality ?: address.adminArea)?.trim()?.takeIf {
        it.isNotEmpty()
      }
    val district =
      (address.subLocality ?: address.locality)?.trim()?.takeIf {
        it.isNotEmpty() && !it.equals(city, ignoreCase = true)
      }
    return when {
      city != null && district != null -> "$city, $district"
      city != null -> city
      district != null -> district
      else -> null
    }
  }

  private fun fetchBigDataCloudCityDistrict(lat: Double, lon: Double): String? {
    return try {
      val lang = Locale.getDefault().language.ifBlank { "id" }
      val url =
        URL(
          "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=$lang"
        )
      val jsonText = httpGet(url) ?: return null
      val json = JSONObject(jsonText)
      val city = json.optString("city").trim().ifEmpty { json.optString("principalSubdivision").trim() }
      val district = json.optString("locality").trim()
      when {
        city.isNotEmpty() && district.isNotEmpty() && !city.equals(district, ignoreCase = true) ->
          "$city, $district"
        city.isNotEmpty() -> city
        district.isNotEmpty() -> district
        else -> null
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun fetchOpenMeteo(lat: Double, lon: Double): Triple<Int, WeatherCondition, Boolean>? {
    return try {
      val url =
        URL(
          "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day"
        )
      val jsonText = httpGet(url) ?: return null
      val current = JSONObject(jsonText).optJSONObject("current") ?: return null
      val temp = current.optDouble("temperature_2m", Double.NaN)
      if (temp.isNaN()) return null
      val wmoCode = current.optInt("weather_code", 2)
      val isDay = current.optInt("is_day", 1) == 1
      Triple(temp.roundToInt(), WeatherCondition.fromWmoCode(wmoCode), isDay)
    } catch (_: Exception) {
      null
    }
  }

  private fun httpGet(url: URL): String? {
    var connection: HttpURLConnection? = null
    return try {
      connection =
        (url.openConnection() as HttpURLConnection).apply {
          requestMethod = "GET"
          connectTimeout = 6000
          readTimeout = 6000
        }
      if (connection.responseCode !in 200..299) return null
      BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
        reader.readText()
      }
    } catch (_: Exception) {
      null
    } finally {
      connection?.disconnect()
    }
  }

  companion object {
    private const val CACHE_TTL_MILLIS = 30L * 60L * 1000L
    private const val LOCATION_MAX_AGE_MILLIS = 60L * 60L * 1000L
    private const val KEY_WEATHER_LOCATION = "cache_weather_location"
    private const val KEY_WEATHER_TEMP = "cache_weather_temp"
    private const val KEY_WEATHER_CONDITION = "cache_weather_condition"
    private const val KEY_WEATHER_IS_DAY = "cache_weather_is_day"
    private const val KEY_WEATHER_UPDATED_AT = "cache_weather_updated_at"
  }
}
