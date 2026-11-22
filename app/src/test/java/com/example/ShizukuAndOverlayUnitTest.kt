package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.adb.AdbConnectionState
import com.example.adb.AdbKeyStore
import com.example.adb.AdbWireClient
import com.example.adb.LocalAdbBridge
import com.example.adb.SystemControlRepository
import com.example.data.AppRepository
import com.example.data.LauncherSettings
import com.example.data.LockAuthMode
import com.example.data.MusicRepository
import com.example.data.WeatherRepository
import com.example.debug.DebugLogStore
import com.example.notifications.DynamicNotificationItem
import com.example.notifications.DynamicNotificationStore
import com.example.notifications.ForegroundActivityTracker
import com.example.security.PinSecurityHelper
import com.example.ui.LauncherViewModel
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShizukuAndOverlayUnitTest {

  @Before
  fun setUp() {
    DynamicNotificationStore.resetForTesting()
  }

  @Test
  fun `foreground tracker toggles cleanly without underflow`() {
    ForegroundActivityTracker.onActivityPaused()
    assertFalse(ForegroundActivityTracker.isLauncherInForeground.value)

    ForegroundActivityTracker.onActivityResumed()
    assertTrue(ForegroundActivityTracker.isLauncherInForeground.value)

    ForegroundActivityTracker.onActivityResumed()
    ForegroundActivityTracker.onActivityPaused()
    assertTrue(ForegroundActivityTracker.isLauncherInForeground.value)

    ForegroundActivityTracker.onActivityPaused()
    assertFalse(ForegroundActivityTracker.isLauncherInForeground.value)
  }

  @Test
  fun `repository persists biometric floating overlay hidden code and adb lock settings`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = AppRepository(context)
    val updated =
      LauncherSettings(
        lockAuthMode = LockAuthMode.PIN_ONLY,
        biometricUnlockEnabled = false,
        biometricAutoPrompt = false,
        shizukuRealLockEnabled = true,
        floatingOverlayPopupEnabled = true,
        suppressSystemHeadsUp = true,
        hiddenComponentKeys = setOf("com.secret.app/.MainActivity"),
        hiddenAppsCode = "#7788",
      )
    repo.saveSettings(updated)

    val loaded = repo.loadSettings()
    assertEquals(LockAuthMode.PIN_ONLY, loaded.lockAuthMode)
    assertFalse(loaded.biometricUnlockEnabled)
    assertFalse(loaded.biometricAutoPrompt)
    assertTrue(loaded.shizukuRealLockEnabled)
    assertTrue(loaded.floatingOverlayPopupEnabled)
    assertTrue(loaded.suppressSystemHeadsUp)
    assertEquals("#7788", loaded.hiddenAppsCode)
    assertTrue(loaded.hiddenComponentKeys.contains("com.secret.app/.MainActivity"))
  }

  @Test
  fun `pin security helper validates length and verifies salted sha256 hashes`() {
    assertFalse(PinSecurityHelper.isValidPinFormat("123"))
    assertFalse(PinSecurityHelper.isValidPinFormat("1234567"))
    assertFalse(PinSecurityHelper.isValidPinFormat("12ab"))
    assertTrue(PinSecurityHelper.isValidPinFormat("2580"))
    assertTrue(PinSecurityHelper.isValidPinFormat("123456"))

    val salt = PinSecurityHelper.generateSalt()
    val hash = PinSecurityHelper.hashPin("258019", salt)
    assertTrue(PinSecurityHelper.verifyPin("258019", hash, salt))
    assertFalse(PinSecurityHelper.verifyPin("000000", hash, salt))
    assertFalse(PinSecurityHelper.verifyPin("2580", hash, salt))
  }

  @Test
  fun `repository and viewmodel manage lock pin and lock auth mode cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = AppRepository(context)
    val vm =
      LauncherViewModel(
        repository = repo,
        weatherRepository = WeatherRepository(context),
        musicRepository = MusicRepository(context),
        systemControlRepository = SystemControlRepository(context),
      )
    ShadowLooper.idleMainLooper()

    assertTrue(vm.saveLockPin("4826"))
    assertTrue(vm.uiState.value.settings.hasLockPinConfigured)
    assertTrue(repo.verifyLockPin("4826"))
    assertFalse(repo.verifyLockPin("9999"))

    vm.setLockAuthMode(LockAuthMode.PIN_ONLY)
    assertEquals(LockAuthMode.PIN_ONLY, vm.uiState.value.settings.lockAuthMode)
    assertFalse(vm.uiState.value.settings.biometricUnlockEnabled)

    vm.setLockAuthMode(LockAuthMode.BIOMETRIC_AND_PIN)
    assertTrue(vm.uiState.value.settings.biometricUnlockEnabled)

    vm.clearLockPin()
    assertFalse(vm.uiState.value.settings.hasLockPinConfigured)
    assertFalse(repo.verifyLockPin("4826"))
  }

  @Test
  fun `debug log store enforces bounded history and viewmodel opens debug console`() {
    DebugLogStore.clear()
    repeat(220) { idx ->
      DebugLogStore.log("Test", "Entry $idx", isError = idx % 2 == 0)
    }
    assertEquals(200, DebugLogStore.logs.value.size)
    assertTrue(DebugLogStore.formatAllAsText().contains("Entry 219"))

    val context = ApplicationProvider.getApplicationContext<Context>()
    val sysControl = SystemControlRepository(context)
    val snapshot = kotlinx.coroutines.runBlocking { sysControl.collectSystemDiagnostics(isDefaultHome = false) }
    assertNotNull(snapshot)

    val vm =
      LauncherViewModel(
        repository = AppRepository(context),
        weatherRepository = WeatherRepository(context),
        musicRepository = MusicRepository(context),
        systemControlRepository = sysControl,
      )
    vm.refreshPermissionStatuses(
      postNotificationsGranted = true,
      notificationAccessGranted = true,
      notificationPolicyAccessGranted = true,
      overlayPermissionGranted = true,
      locationGranted = true,
    )
    assertTrue(vm.uiState.value.isNotificationPolicyAccessGranted)

    vm.openDebugConsole()
    ShadowLooper.idleMainLooper()
    assertTrue(vm.uiState.value.isDebugConsoleOpen)

    vm.closeDebugConsole()
    assertFalse(vm.uiState.value.isDebugConsoleOpen)
  }

  @Test
  fun `secret code in search activates hidden apps vault only on exact match`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vm =
      LauncherViewModel(
        repository = AppRepository(context),
        weatherRepository = WeatherRepository(context),
        musicRepository = MusicRepository(context),
        systemControlRepository = SystemControlRepository(context),
      )
    ShadowLooper.idleMainLooper()

    vm.setHiddenAppsCode("#4242")
    vm.onSearchQueryChange("something_else")
    assertFalse(vm.uiState.value.isSecretVaultMatched)

    vm.onSearchQueryChange(" #4242 ")
    assertTrue(vm.uiState.value.isSecretVaultMatched)

    vm.clearSearch()
    assertFalse(vm.uiState.value.isSecretVaultMatched)
  }

  @Test
  fun `local adb bridge initializes cleanly and reports disabled when adb global settings are off`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    LocalAdbBridge.init(context)
    val state = LocalAdbBridge.refreshStatus()
    assertTrue(
      state == AdbConnectionState.DISABLED_IN_SETTINGS ||
        state == AdbConnectionState.DISCONNECTED
    )
    assertTrue(LocalAdbBridge.getPublicKeyFingerprint().isNotBlank())
  }

  @Test
  fun `adb key store generates valid 524 byte android rsa struct and verifies sha1 token signature`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val keyStore = AdbKeyStore(context)
    val pair = keyStore.getOrCreateKeyPair()
    val structBytes = AdbKeyStore.encodeAndroidRsaPublicKey(pair.public as RSAPublicKey)
    assertEquals(524, structBytes.size)
    val buf = ByteBuffer.wrap(structBytes).order(ByteOrder.LITTLE_ENDIAN)
    assertEquals(64, buf.getInt()) // 2048-bit modulus words

    val packet = keyStore.getAndroidPublicKeyPacketPayload()
    assertEquals(0.toByte(), packet.last())
    assertTrue(String(packet, Charsets.UTF_8).contains(AdbKeyStore.DEFAULT_KEY_LABEL))

    val token = ByteArray(20) { (it + 1).toByte() }
    val sigBytes = keyStore.signToken(token)
    assertEquals(256, sigBytes.size)

    // Built-in ADB signing encodes SHA-1 DigestInfo raw RSA signature that matches standard SHA1withRSA verification
    val verifier = Signature.getInstance("SHA1withRSA")
    verifier.initVerify(pair.public)
    // Wait: SHA1withRSA hashes the message before verifying; ADB signs the raw 20-byte SHA-1 digest directly!
    // Verify via NONEwithRSA + SHA-1 DigestInfo prefix check via public key decryption
    val cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(javax.crypto.Cipher.DECRYPT_MODE, pair.public)
    val recovered = cipher.doFinal(sigBytes)
    assertEquals(35, recovered.size)
    assertTrue(recovered.takeLast(20).toByteArray().contentEquals(token))
    assertNotNull(keyStore.createTlsSslContext())
  }

  @Test
  fun `adb wire client completes auth handshake and parses shell exit code over loopback socket`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val keyStore = AdbKeyStore(context)
    keyStore.getOrCreateKeyPair()

    val serverSocket = ServerSocket(0)
    val port = serverSocket.localPort
    val executor = Executors.newSingleThreadExecutor()

    val serverFuture =
      executor.submit<Boolean> {
        serverSocket.use { srv ->
          val sock = srv.accept()
          sock.use { conn ->
            val inp = conn.getInputStream()
            val out = conn.getOutputStream()

            fun writePkt(cmd: Int, a0: Int, a1: Int, data: ByteArray = ByteArray(0)) {
              out.write(AdbWireClient.encodePacketHeader(cmd, a0, a1, data))
              if (data.isNotEmpty()) out.write(data)
              out.flush()
            }

            // 1. Read A_CNXN from client
            val cnxn = AdbWireClient.readPacket(inp)
            assertEquals(AdbWireClient.A_CNXN, cnxn.command)

            // 2. Send A_AUTH TOKEN challenge
            val challenge = ByteArray(20) { 0x5A }
            writePkt(AdbWireClient.A_AUTH, AdbWireClient.AUTH_TOKEN, 0, challenge)

            // 3. Read A_AUTH SIGNATURE -> respond A_CNXN
            val sigPkt = AdbWireClient.readPacket(inp)
            assertEquals(AdbWireClient.A_AUTH, sigPkt.command)
            assertEquals(AdbWireClient.AUTH_SIGNATURE, sigPkt.arg0)
            writePkt(
              AdbWireClient.A_CNXN,
              AdbWireClient.A_VERSION,
              65536,
              "device::ro.product.name=test\u0000".toByteArray(Charsets.UTF_8),
            )

            // 4. Read A_OPEN shell command -> respond A_OKAY + A_WRTE + A_CLSE
            val openPkt = AdbWireClient.readPacket(inp)
            assertEquals(AdbWireClient.A_OPEN, openPkt.command)
            val localId = openPkt.arg0
            val remoteId = 99
            writePkt(AdbWireClient.A_OKAY, remoteId, localId)
            writePkt(
              AdbWireClient.A_WRTE,
              remoteId,
              localId,
              "uid=2000(shell)\n__S6EXIT__:0\n".toByteArray(Charsets.UTF_8),
            )
            val ackOkay = AdbWireClient.readPacket(inp)
            assertEquals(AdbWireClient.A_OKAY, ackOkay.command)
            writePkt(AdbWireClient.A_CLSE, remoteId, localId)
            val ackClse = AdbWireClient.readPacket(inp)
            assertEquals(AdbWireClient.A_CLSE, ackClse.command)
            true
          }
        }
      }

    val client = AdbWireClient(keyStore)
    client.connect(host = "127.0.0.1", port = port)
    assertTrue(client.isSocketOpen)
    val res = client.runShell("id")
    assertTrue(res.success)
    assertEquals(0, res.exitCode)
    assertEquals("uid=2000(shell)", res.output)
    client.close()
    assertTrue(serverFuture.get())
    executor.shutdownNow()
  }

  @Test
  fun `notification store deduplicates rapid duplicate banners while keeping lock screen list`() {
    val msg1 =
      DynamicNotificationItem(
        key = "0|com.whatsapp|1|user1",
        packageName = "com.whatsapp",
        appName = "WhatsApp",
        title = "Budi",
        text = "Halo bro!",
        postTime = 1_000_000L,
        contentIntent = null,
      )
    assertTrue(DynamicNotificationStore.upsertWithTime(msg1, nowMs = 1_000_000L))
    assertEquals(1L, DynamicNotificationStore.bannerTriggerId.value)
    assertNotNull(DynamicNotificationStore.bannerNotification.value)

    // Exact same notification re-dispatched -> must NOT double pop
    assertFalse(DynamicNotificationStore.upsertWithTime(msg1, nowMs = 1_000_500L))
    assertEquals(1L, DynamicNotificationStore.bannerTriggerId.value)

    // Summary or sibling notification with identical title+text within 8s -> must NOT double pop
    val sibling = msg1.copy(key = "0|com.whatsapp|summary|0", postTime = 1_002_000L)
    assertFalse(DynamicNotificationStore.upsertWithTime(sibling, nowMs = 1_002_000L))
    assertEquals(1L, DynamicNotificationStore.bannerTriggerId.value)

    // Clearing transient banner hides pop-up without erasing notifications list for Lock Screen
    DynamicNotificationStore.clearBanner(msg1.key)
    assertNull(DynamicNotificationStore.bannerNotification.value)
    assertEquals(2, DynamicNotificationStore.notifications.value.size)

    // Different new message -> pops normally
    val msg2 = msg1.copy(key = "0|com.whatsapp|2|user1", text = "Jadi main?", postTime = 1_003_000L)
    assertTrue(DynamicNotificationStore.upsertWithTime(msg2, nowMs = 1_003_000L))
    assertEquals(2L, DynamicNotificationStore.bannerTriggerId.value)
  }
}
