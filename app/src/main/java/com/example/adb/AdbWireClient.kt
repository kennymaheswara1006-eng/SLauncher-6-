package com.example.adb

import com.example.debug.DebugLogStore
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocket

data class AdbCommandResult(
  val success: Boolean,
  val exitCode: Int,
  val output: String,
)

data class AdbPacket(
  val command: Int,
  val arg0: Int,
  val arg1: Int,
  val data: ByteArray,
)

/**
 * Pure-Kotlin implementation of Android's ADB v1 wire protocol supporting:
 * - Local/Network TCP ADB (`127.0.0.1:5555` via `adb tcpip 5555`) with RSA-2048 `A_AUTH`
 * - Android 11+ Wireless Debugging TLS upgrade (`A_STLS`)
 * - Stream-multiplexed `"shell:<command>"` execution with reliable exit code extraction
 */
class AdbWireClient(private val keyStore: AdbKeyStore) {

  private var rawSocket: Socket? = null
  private var input: InputStream? = null
  private var output: OutputStream? = null
  private var maxPayload: Int = DEFAULT_MAX_DATA
  private var protocolVersion: Int = A_VERSION
  private val nextLocalStreamId = AtomicInteger(1)

  @Volatile var connectedHost: String = ""
    private set
  @Volatile var connectedPort: Int = 0
    private set
  @Volatile var banner: String = ""
    private set

  val isSocketOpen: Boolean
    get() {
      val s = rawSocket ?: return false
      return !s.isClosed && s.isConnected
    }

  @Synchronized
  fun connect(
    host: String,
    port: Int,
    connectTimeoutMs: Int = 3500,
    authTimeoutMs: Int = 18_000,
    onAwaitingAuthorization: () -> Unit = {},
  ) {
    close()
    val socket = Socket()
    try {
      socket.tcpNoDelay = true
      socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
      socket.soTimeout = authTimeoutMs
      rawSocket = socket
      input = BufferedInputStream(socket.getInputStream())
      output = socket.getOutputStream()

      sendPacket(
        command = A_CNXN,
        arg0 = A_VERSION,
        arg1 = DEFAULT_MAX_DATA,
        data = "host::features=shell_v2,cmd,stat_v2\u0000".toByteArray(Charsets.UTF_8),
      )

      var sentSignature = false
      var sentPublicKey = false

      while (true) {
        val pkt = readPacket(input!!)
        when (pkt.command) {
          A_CNXN -> {
            protocolVersion = pkt.arg0
            maxPayload = pkt.arg1.coerceIn(4096, 1024 * 1024)
            banner = String(pkt.data, Charsets.UTF_8).trimEnd('\u0000').trim()
            connectedHost = host
            connectedPort = port
            socket.soTimeout = SHELL_READ_TIMEOUT_MS
            DebugLogStore.log("ADB", "Connected to $host:$port (${banner.ifBlank { "adbd" }})")
            return
          }

          A_STLS -> {
            DebugLogStore.log("ADB", "Server requested TLS upgrade (Wireless ADB)")
            sendPacket(A_STLS, A_VERSION, 0, ByteArray(0))
            val sslCtx = keyStore.createTlsSslContext()
            val sslSocket =
              sslCtx.socketFactory.createSocket(socket, host, port, true) as SSLSocket
            sslSocket.useClientMode = true
            sslSocket.soTimeout = authTimeoutMs
            sslSocket.startHandshake()
            rawSocket = sslSocket
            input = BufferedInputStream(sslSocket.getInputStream())
            output = sslSocket.getOutputStream()
          }

          A_AUTH -> {
            if (pkt.arg0 == AUTH_TOKEN) {
              if (!sentSignature) {
                sentSignature = true
                val signature = keyStore.signToken(pkt.data)
                sendPacket(A_AUTH, AUTH_SIGNATURE, 0, signature)
              } else if (!sentPublicKey) {
                sentPublicKey = true
                onAwaitingAuthorization()
                DebugLogStore.log("ADB", "Sent RSA pubkey — tap 'Allow USB Debugging' on screen")
                val pubPayload = keyStore.getAndroidPublicKeyPacketPayload()
                sendPacket(A_AUTH, AUTH_RSAPUBLICKEY, 0, pubPayload)
              } else {
                throw IllegalStateException("ADB authentication rejected by device")
              }
            }
          }

          else -> {
            throw IllegalStateException("Unexpected ADB packet 0x${pkt.command.toString(16)}")
          }
        }
      }
    } catch (t: Throwable) {
      close()
      throw t
    }
  }

  @Synchronized
  fun runShell(command: String): AdbCommandResult {
    val inStream = input
    val outStream = output
    if (!isSocketOpen || inStream == null || outStream == null) {
      return AdbCommandResult(false, -1, "ADB not connected")
    }

    val localId = nextLocalStreamId.getAndIncrement()
    val exitMarker = "__S6EXIT__:"
    val wrapped = "($command) 2>&1; echo $exitMarker\$?"
    val openPayload = "shell:$wrapped\u0000".toByteArray(Charsets.UTF_8)

    val outBuffer = ByteArrayOutputStream()
    var remoteId = 0

    sendPacket(A_OPEN, localId, 0, openPayload)

    while (true) {
      val pkt = readPacket(inStream)
      when (pkt.command) {
        A_OKAY -> {
          if (pkt.arg1 == localId) {
            remoteId = pkt.arg0
          }
        }

        A_WRTE -> {
          if (pkt.arg1 == localId) {
            if (pkt.data.isNotEmpty()) {
              outBuffer.write(pkt.data)
            }
            sendPacket(A_OKAY, localId, pkt.arg0, ByteArray(0))
          }
        }

        A_CLSE -> {
          if (pkt.arg1 == localId || (remoteId != 0 && pkt.arg0 == remoteId)) {
            sendPacket(A_CLSE, localId, pkt.arg0, ByteArray(0))
            break
          }
        }

        A_AUTH -> Unit
      }
    }

    val rawOutput = String(outBuffer.toByteArray(), Charsets.UTF_8)
    val markerIdx = rawOutput.lastIndexOf(exitMarker)
    return if (markerIdx >= 0) {
      val exitStr = rawOutput.substring(markerIdx + exitMarker.length).trim().lineSequence().firstOrNull().orEmpty()
      val exitCode = exitStr.toIntOrNull() ?: 0
      val body = rawOutput.substring(0, markerIdx).trimEnd()
      AdbCommandResult(success = exitCode == 0, exitCode = exitCode, output = body.trim())
    } else {
      AdbCommandResult(success = true, exitCode = 0, output = rawOutput.trim())
    }
  }

  @Synchronized
  fun close() {
    runCatching { input?.close() }
    runCatching { output?.close() }
    runCatching { rawSocket?.close() }
    input = null
    output = null
    rawSocket = null
    connectedHost = ""
    connectedPort = 0
    banner = ""
  }

  private fun sendPacket(command: Int, arg0: Int, arg1: Int, data: ByteArray) {
    val out = output ?: throw IllegalStateException("ADB stream closed")
    val header = encodePacketHeader(command, arg0, arg1, data)
    out.write(header)
    if (data.isNotEmpty()) {
      out.write(data)
    }
    out.flush()
  }

  companion object {
    const val A_SYNC = 0x434e5953
    const val A_CNXN = 0x4e584e43
    const val A_OPEN = 0x4e45504f
    const val A_OKAY = 0x59414b4f
    const val A_CLSE = 0x45534c43
    const val A_WRTE = 0x45545257
    const val A_AUTH = 0x48545541
    const val A_STLS = 0x534c5453

    const val A_VERSION = 0x01000000
    const val DEFAULT_MAX_DATA = 256 * 1024
    const val SHELL_READ_TIMEOUT_MS = 10_000

    const val AUTH_TOKEN = 1
    const val AUTH_SIGNATURE = 2
    const val AUTH_RSAPUBLICKEY = 3

    fun computeChecksum(data: ByteArray): Int {
      var sum = 0L
      for (b in data) {
        sum += (b.toInt() and 0xFF)
      }
      return (sum and 0xFFFFFFFFL).toInt()
    }

    fun encodePacketHeader(command: Int, arg0: Int, arg1: Int, data: ByteArray): ByteArray {
      val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
      buf.putInt(command)
      buf.putInt(arg0)
      buf.putInt(arg1)
      buf.putInt(data.size)
      buf.putInt(computeChecksum(data))
      buf.putInt(command.inv())
      return buf.array()
    }

    fun readPacket(stream: InputStream): AdbPacket {
      val headerBytes = readFully(stream, 24)
      val buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
      val command = buf.getInt()
      val arg0 = buf.getInt()
      val arg1 = buf.getInt()
      val dataLength = buf.getInt()
      @Suppress("UNUSED_VARIABLE")
      val dataChecksum = buf.getInt()
      val magic = buf.getInt()

      if (magic != command.inv()) {
        throw IllegalStateException("Corrupt ADB header magic")
      }
      if (dataLength < 0 || dataLength > 4 * 1024 * 1024) {
        throw IllegalStateException("Invalid ADB payload size: $dataLength")
      }
      val payload = if (dataLength > 0) readFully(stream, dataLength) else ByteArray(0)
      return AdbPacket(command = command, arg0 = arg0, arg1 = arg1, data = payload)
    }

    private fun readFully(stream: InputStream, size: Int): ByteArray {
      val buffer = ByteArray(size)
      var offset = 0
      while (offset < size) {
        val read = stream.read(buffer, offset, size - offset)
        if (read < 0) throw EOFException("ADB stream ended unexpectedly")
        offset += read
      }
      return buffer
    }
  }
}
