package com.example

import com.example.adb.AdbKeyStore
import com.example.adb.AdbWireClient
import com.example.debug.DebugLogStore
import com.example.notifications.DynamicNotificationItem
import com.example.notifications.DynamicNotificationStore
import com.example.notifications.ForegroundActivityTracker
import com.example.security.PinSecurityHelper
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.Executors
import javax.crypto.Cipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AdbAndNotificationUnitTest {

  @get:Rule val tempFolder = TemporaryFolder()

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
  fun `pin security helper validates format and guards invalid inputs`() {
    assertFalse(PinSecurityHelper.isValidPinFormat("123"))
    assertFalse(PinSecurityHelper.isValidPinFormat("1234567"))
    assertFalse(PinSecurityHelper.isValidPinFormat("12ab"))
    assertTrue(PinSecurityHelper.isValidPinFormat("2580"))
    assertTrue(PinSecurityHelper.isValidPinFormat("123456"))

    assertFalse(PinSecurityHelper.verifyPin("", "abc", "salt"))
    assertFalse(PinSecurityHelper.verifyPin("2580", null, "salt"))
    assertFalse(PinSecurityHelper.verifyPin("2580", "abc", null))
  }

  @Test
  fun `adb key store creates 524-byte android struct signs 20-byte token and builds tls context`() {
    val keyStore = AdbKeyStore(tempFolder.newFolder("adb_keys"))
    val pair = keyStore.getOrCreateKeyPair()
    val structBytes = AdbKeyStore.encodeAndroidRsaPublicKey(pair.public as RSAPublicKey)
    assertEquals(524, structBytes.size)
    val buf = ByteBuffer.wrap(structBytes).order(ByteOrder.LITTLE_ENDIAN)
    assertEquals(64, buf.getInt())

    val token = ByteArray(20) { (it + 7).toByte() }
    val signature = keyStore.signToken(token)
    assertEquals(256, signature.size)

    val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.DECRYPT_MODE, pair.public)
    val recovered = cipher.doFinal(signature)
    assertEquals(35, recovered.size)
    assertTrue(recovered.takeLast(20).toByteArray().contentEquals(token))

    assertTrue(keyStore.getKeyFingerprintPreview().contains(":"))
    assertNotNull(keyStore.createTlsSslContext())
  }

  @Test
  fun `adb wire client completes auth handshake and extracts exit code over loopback tcp`() {
    val keyStore = AdbKeyStore(tempFolder.newFolder("adb_wire_keys"))
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

            val cnxn = AdbWireClient.readPacket(inp)
            assertEquals(AdbWireClient.A_CNXN, cnxn.command)

            val challenge = ByteArray(20) { 0x3C }
            writePkt(AdbWireClient.A_AUTH, AdbWireClient.AUTH_TOKEN, 0, challenge)

            val sigPkt = AdbWireClient.readPacket(inp)
            assertEquals(AdbWireClient.A_AUTH, sigPkt.command)
            assertEquals(AdbWireClient.AUTH_SIGNATURE, sigPkt.arg0)
            writePkt(
              AdbWireClient.A_CNXN,
              AdbWireClient.A_VERSION,
              65536,
              "device::ro.product.name=slauncher_test\u0000".toByteArray(Charsets.UTF_8),
            )

            val openPkt = AdbWireClient.readPacket(inp)
            assertEquals(AdbWireClient.A_OPEN, openPkt.command)
            val localId = openPkt.arg0
            val remoteId = 42
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
    assertFalse(client.isSocketOpen)
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

    // Exact duplicate re-post -> must NOT double pop
    assertFalse(DynamicNotificationStore.upsertWithTime(msg1, nowMs = 1_000_500L))
    assertEquals(1L, DynamicNotificationStore.bannerTriggerId.value)

    // Summary or sibling notification with identical content within 8s -> must NOT double pop
    val sibling = msg1.copy(key = "0|com.whatsapp|summary|0", postTime = 1_002_000L)
    assertFalse(DynamicNotificationStore.upsertWithTime(sibling, nowMs = 1_002_000L))
    assertEquals(1L, DynamicNotificationStore.bannerTriggerId.value)

    // Clearing transient banner hides floating/Home pop-up without erasing LockScreen card list
    DynamicNotificationStore.clearBanner(msg1.key)
    assertNull(DynamicNotificationStore.bannerNotification.value)
    assertEquals(2, DynamicNotificationStore.notifications.value.size)

    // Distinct message -> pops normally
    val msg2 = msg1.copy(key = "0|com.whatsapp|2|user1", text = "Jadi main?", postTime = 1_003_000L)
    assertTrue(DynamicNotificationStore.upsertWithTime(msg2, nowMs = 1_003_000L))
    assertEquals(2L, DynamicNotificationStore.bannerTriggerId.value)
  }

  @Test
  fun `debug log store enforces bounded 200 entry history`() {
    DebugLogStore.clear()
    repeat(220) { idx ->
      DebugLogStore.log("Test", "Entry $idx", isError = idx % 2 == 0)
    }
    assertEquals(200, DebugLogStore.logs.value.size)
    assertTrue(DebugLogStore.formatAllAsText().contains("Entry 219"))
  }
}
