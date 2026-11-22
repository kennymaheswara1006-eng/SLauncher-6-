package com.example.adb

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import com.example.debug.DebugLogStore
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DiscoveredAdbEndpoint(
  val serviceName: String,
  val host: String,
  val port: Int,
  val isTlsConnect: Boolean,
)

data class UsbAdbDeviceInfo(
  val deviceName: String,
  val productName: String,
  val vendorId: Int,
  val productId: Int,
)

data class AdbEnvironmentStatus(
  val developerOptionsEnabled: Boolean,
  val usbDebuggingEnabled: Boolean,
  val wirelessDebuggingEnabled: Boolean,
  val localIpv4: String,
  val usbOtgAdbDevices: List<UsbAdbDeviceInfo>,
)

/**
 * Inspects Android's native USB Debugging, Wireless Debugging, USB OTG ADB host interfaces,
 * and auto-discovers Wireless ADB endpoints (`_adb-tls-connect._tcp.`) via `NsdManager`
 * completely inside SLauncher 6 without any external helper app.
 */
object AdbDiscoveryHelper {

  private const val SERVICE_ADB_TLS_CONNECT = "_adb-tls-connect._tcp."
  private const val ADB_USB_SUBCLASS = 0x42
  private const val ADB_USB_PROTOCOL = 0x01

  private val _discoveredEndpoints = MutableStateFlow<List<DiscoveredAdbEndpoint>>(emptyList())
  val discoveredEndpoints: StateFlow<List<DiscoveredAdbEndpoint>> =
    _discoveredEndpoints.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  @Volatile private var activeNsdManager: NsdManager? = null
  @Volatile private var activeDiscoveryListener: NsdManager.DiscoveryListener? = null

  fun readEnvironmentStatus(context: Context): AdbEnvironmentStatus {
    val resolver = context.contentResolver
    val devEnabled =
      runCatching {
          Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        }
        .getOrDefault(false)
    val adbEnabled =
      runCatching {
          Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0) == 1
        }
        .getOrDefault(false)
    val wirelessEnabled =
      runCatching {
          Settings.Global.getInt(resolver, "adb_wifi_enabled", 0) == 1
        }
        .getOrDefault(false)

    val usbDevices = scanUsbOtgAdbDevices(context)
    val ip = resolveLocalIpv4Address()

    return AdbEnvironmentStatus(
      developerOptionsEnabled = devEnabled,
      usbDebuggingEnabled = adbEnabled,
      wirelessDebuggingEnabled = wirelessEnabled,
      localIpv4 = ip,
      usbOtgAdbDevices = usbDevices,
    )
  }

  fun scanUsbOtgAdbDevices(context: Context): List<UsbAdbDeviceInfo> {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
    return runCatching {
        usbManager.deviceList.values.mapNotNull { device ->
          var hasAdbInterface = false
          for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (
              intf.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC &&
                intf.interfaceSubclass == ADB_USB_SUBCLASS &&
                intf.interfaceProtocol == ADB_USB_PROTOCOL
            ) {
              hasAdbInterface = true
              break
            }
          }
          if (hasAdbInterface) {
            UsbAdbDeviceInfo(
              deviceName = device.deviceName,
              productName = device.productName?.takeIf { it.isNotBlank() } ?: "USB ADB Device",
              vendorId = device.vendorId,
              productId = device.productId,
            )
          } else {
            null
          }
        }
      }
      .getOrDefault(emptyList())
  }

  fun startWirelessDiscovery(context: Context) {
    stopWirelessDiscovery()
    val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    if (nsd == null) {
      DebugLogStore.log("ADB-Scan", "NsdManager unavailable on this device", isError = true)
      return
    }

    _discoveredEndpoints.value = emptyList()
    val listener =
      object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
          _isScanning.value = false
          DebugLogStore.log("ADB-Scan", "Discovery failed ($errorCode)", isError = true)
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
          _isScanning.value = false
        }

        override fun onDiscoveryStarted(serviceType: String?) {
          _isScanning.value = true
          DebugLogStore.log("ADB-Scan", "Scanning Wireless ADB mDNS (_adb-tls-connect._tcp)...")
        }

        override fun onDiscoveryStopped(serviceType: String?) {
          _isScanning.value = false
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
          val info = serviceInfo ?: return
          @Suppress("DEPRECATION")
          nsd.resolveService(
            info,
            object : NsdManager.ResolveListener {
              override fun onResolveFailed(service: NsdServiceInfo?, errorCode: Int) = Unit

              override fun onServiceResolved(resolved: NsdServiceInfo?) {
                val res = resolved ?: return
                val hostAddr =
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    res.hostAddresses.firstOrNull()?.hostAddress
                  } else {
                    @Suppress("DEPRECATION")
                    res.host?.hostAddress
                  }
                    ?.takeIf { it.isNotBlank() }
                    ?: return
                val port = res.port
                if (port <= 0) return

                val endpoint =
                  DiscoveredAdbEndpoint(
                    serviceName = res.serviceName.ifBlank { "Wireless ADB" },
                    host = hostAddr,
                    port = port,
                    isTlsConnect = true,
                  )
                _discoveredEndpoints.update { current ->
                  (current.filterNot { it.host == hostAddr && it.port == port } + endpoint).take(8)
                }
                DebugLogStore.log("ADB-Scan", "Found Wireless ADB at $hostAddr:$port")
              }
            },
          )
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo?) = Unit
      }

    activeNsdManager = nsd
    activeDiscoveryListener = listener
    runCatching {
        nsd.discoverServices(SERVICE_ADB_TLS_CONNECT, NsdManager.PROTOCOL_DNS_SD, listener)
      }
      .onFailure { err ->
        _isScanning.value = false
        DebugLogStore.log("ADB-Scan", "mDNS scan error: ${err.message}", isError = true)
      }
  }

  fun stopWirelessDiscovery() {
    val nsd = activeNsdManager
    val listener = activeDiscoveryListener
    activeNsdManager = null
    activeDiscoveryListener = null
    _isScanning.value = false
    if (nsd != null && listener != null) {
      runCatching { nsd.stopServiceDiscovery(listener) }
    }
  }

  fun openDeveloperOptions(context: Context) {
    val intents =
      listOf(
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
      )
    for (intent in intents) {
      val ok =
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
          }
          .getOrDefault(false)
      if (ok) return
    }
  }

  private fun resolveLocalIpv4Address(): String {
    return runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching "127.0.0.1"
        for (intf in interfaces) {
          if (!intf.isUp || intf.isLoopback) continue
          for (addr in intf.inetAddresses) {
            if (addr is Inet4Address && !addr.isLoopbackAddress) {
              val host = addr.hostAddress
              if (!host.isNullOrBlank()) return@runCatching host
            }
          }
        }
        "127.0.0.1"
      }
      .getOrDefault("127.0.0.1")
  }
}
