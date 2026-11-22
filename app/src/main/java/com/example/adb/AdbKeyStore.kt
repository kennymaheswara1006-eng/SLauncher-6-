package com.example.adb

import android.content.Context
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Principal
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.SSLContext
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * Generates, persists, and signs with SLauncher 6's own 2048-bit RSA ADB identity key.
 * Produces:
 * 1) Exact 524-byte Android `RSAPublicKey` Base64 struct (`<b64> SLauncher6@android\0`) for `A_AUTH`.
 * 2) PKCS#1 v1.5 SHA-1 DigestInfo raw RSA signature (`NONEwithRSA`) matching `adbd` `RSA_verify`.
 * 3) Self-signed X.509 DER certificate + `SSLContext` for Android 11+ TLS Wireless Debugging.
 */
class AdbKeyStore(private val keyDir: File) {

  constructor(context: Context) : this(File(context.applicationContext.filesDir, "adb_rsa"))

  private val privateKeyFile = File(keyDir, "adbkey.pk8")
  private val publicKeyFile = File(keyDir, "adbkey.x509.pub")

  @Volatile private var cachedKeyPair: KeyPair? = null
  @Volatile private var cachedCert: X509Certificate? = null

  @Synchronized
  fun getOrCreateKeyPair(): KeyPair {
    cachedKeyPair?.let { return it }
    if (!keyDir.exists()) keyDir.mkdirs()

    if (privateKeyFile.exists() && publicKeyFile.exists()) {
      runCatching {
        val kf = KeyFactory.getInstance("RSA")
        val privSpec = PKCS8EncodedKeySpec(privateKeyFile.readBytes())
        val pubSpec = X509EncodedKeySpec(publicKeyFile.readBytes())
        val loaded = KeyPair(kf.generatePublic(pubSpec), kf.generatePrivate(privSpec))
        cachedKeyPair = loaded
        return loaded
      }
    }

    val gen = KeyPairGenerator.getInstance("RSA")
    gen.initialize(2048)
    val created = gen.generateKeyPair()
    privateKeyFile.writeBytes(created.private.encoded)
    publicKeyFile.writeBytes(created.public.encoded)
    cachedKeyPair = created
    cachedCert = null
    return created
  }

  /**
   * Signs the 20-byte ADB `A_AUTH` challenge token without re-hashing (raw SHA-1 DigestInfo + RSA).
   */
  fun signToken(token: ByteArray): ByteArray {
    val pair = getOrCreateKeyPair()
    val digestInfo = SHA1_DIGEST_INFO_PREFIX + token
    val sig = Signature.getInstance("NONEwithRSA")
    sig.initSign(pair.private)
    sig.update(digestInfo)
    return sig.sign()
  }

  /**
   * Returns the null-terminated ASCII public key packet payload required by `A_AUTH(RSAPUBLICKEY)`.
   */
  fun getAndroidPublicKeyPacketPayload(label: String = DEFAULT_KEY_LABEL): ByteArray {
    val rsaPub = getOrCreateKeyPair().public as RSAPublicKey
    val binaryStruct = encodeAndroidRsaPublicKey(rsaPub)
    val base64 = Base64.encodeToString(binaryStruct, Base64.NO_WRAP)
    val line = "$base64 $label\u0000"
    return line.toByteArray(Charsets.UTF_8)
  }

  fun getKeyFingerprintPreview(): String {
    val rsaPub = getOrCreateKeyPair().public as RSAPublicKey
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val digest = md.digest(rsaPub.encoded)
    return digest.take(8).joinToString(":") { "%02X".format(it) }
  }

  fun createTlsSslContext(): SSLContext {
    val pair = getOrCreateKeyPair()
    val cert = getOrCreateSelfSignedCert(pair)
    val keyManager =
      object : X509ExtendedKeyManager() {
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) =
          arrayOf("slauncher_adb")

        override fun chooseClientAlias(
          keyType: Array<out String>?,
          issuers: Array<out Principal>?,
          socket: Socket?,
        ) = "slauncher_adb"

        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
          null

        override fun chooseServerAlias(
          keyType: String?,
          issuers: Array<out Principal>?,
          socket: Socket?,
        ): String? = null

        override fun getCertificateChain(alias: String?): Array<X509Certificate> = arrayOf(cert)

        override fun getPrivateKey(alias: String?): PrivateKey = pair.private
      }

    val trustAllManager =
      object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
      }

    return SSLContext.getInstance("TLS").apply {
      init(arrayOf(keyManager), arrayOf(trustAllManager), null)
    }
  }

  @Synchronized
  private fun getOrCreateSelfSignedCert(pair: KeyPair): X509Certificate {
    cachedCert?.let { return it }
    val derCert = buildSelfSignedX509Der(pair)
    val factory = CertificateFactory.getInstance("X.509")
    val cert = factory.generateCertificate(ByteArrayInputStream(derCert)) as X509Certificate
    cachedCert = cert
    return cert
  }

  companion object {
    const val DEFAULT_KEY_LABEL = "SLauncher6@android"
    private const val MODULUS_BYTES = 256
    private const val MODULUS_WORDS = 64

    // ASN.1 DER DigestInfo prefix for SHA-1 (OID 1.3.14.3.2.26, NULL params, 20-byte octet string)
    private val SHA1_DIGEST_INFO_PREFIX =
      byteArrayOf(
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
        0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
      )

    fun encodeAndroidRsaPublicKey(rsaPub: RSAPublicKey): ByteArray {
      val n = rsaPub.modulus
      val r32 = BigInteger.ONE.shiftLeft(32)
      // n0inv = -N^(-1) mod 2^32
      val n0inv = n.negate().modInverse(r32).toLong()
      val r = BigInteger.ONE.shiftLeft(2048)
      val rr = r.multiply(r).mod(n)

      val nLittleEndian = toUnsignedLittleEndian(n, MODULUS_BYTES)
      val rrLittleEndian = toUnsignedLittleEndian(rr, MODULUS_BYTES)

      val buf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
      buf.putInt(MODULUS_WORDS)
      buf.putInt(n0inv.toInt())
      buf.put(nLittleEndian)
      buf.put(rrLittleEndian)
      buf.putInt(rsaPub.publicExponent.toInt())
      return buf.array()
    }

    private fun toUnsignedLittleEndian(value: BigInteger, length: Int): ByteArray {
      val bigEndian = value.toByteArray()
      val fixedBigEndian = ByteArray(length)
      val srcStart = if (bigEndian.size > length) bigEndian.size - length else 0
      val copyLen = minOf(bigEndian.size, length)
      val dstStart = length - copyLen
      System.arraycopy(bigEndian, srcStart, fixedBigEndian, dstStart, copyLen)
      // Reverse big-endian to little-endian (LSB first, matches uint32_t[] on little-endian)
      return fixedBigEndian.reversedArray()
    }

    private fun buildSelfSignedX509Der(pair: KeyPair): ByteArray {
      val nowMs = System.currentTimeMillis()
      val notBefore = nowMs - 86_400_000L
      val notAfter = nowMs + 3_650L * 86_400_000L

      val sha256WithRsaAlgId =
        derSequence(
          byteArrayOf(0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x0b) +
            byteArrayOf(0x05, 0x00)
        )
      // CN=SLauncher6ADB
      val cnAttr =
        derSet(
          derSequence(
            byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03) +
              derTlv(0x0C, "SLauncher6ADB".toByteArray(Charsets.UTF_8))
          )
        )
      val nameSeq = derSequence(cnAttr)

      val fmt =
        SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
          timeZone = TimeZone.getTimeZone("UTC")
        }
      val validitySeq =
        derSequence(
          derTlv(0x17, fmt.format(Date(notBefore)).toByteArray(Charsets.US_ASCII)) +
            derTlv(0x17, fmt.format(Date(notAfter)).toByteArray(Charsets.US_ASCII))
        )

      val versionExplicit = derTlv(0xA0, byteArrayOf(0x02, 0x01, 0x02)) // v3
      val serialInt = derTlv(0x02, byteArrayOf(0x01, 0x53, 0x36))

      val tbsCert =
        derSequence(
          versionExplicit +
            serialInt +
            sha256WithRsaAlgId +
            nameSeq +
            validitySeq +
            nameSeq +
            pair.public.encoded
        )

      val signer = Signature.getInstance("SHA256withRSA")
      signer.initSign(pair.private)
      signer.update(tbsCert)
      val signatureBytes = signer.sign()

      val sigBitString = derTlv(0x03, byteArrayOf(0x00) + signatureBytes)
      return derSequence(tbsCert + sha256WithRsaAlgId + sigBitString)
    }

    private fun derSequence(content: ByteArray) = derTlv(0x30, content)
    private fun derSet(content: ByteArray) = derTlv(0x31, content)

    private fun derTlv(tag: Int, content: ByteArray): ByteArray {
      val out = ByteArrayOutputStream()
      out.write(tag)
      val len = content.size
      when {
        len < 0x80 -> out.write(len)
        len <= 0xFF -> {
          out.write(0x81)
          out.write(len)
        }
        else -> {
          out.write(0x82)
          out.write((len shr 8) and 0xFF)
          out.write(len and 0xFF)
        }
      }
      out.write(content)
      return out.toByteArray()
    }
  }
}
