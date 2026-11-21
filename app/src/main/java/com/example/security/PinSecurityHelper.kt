package com.example.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Salted SHA-256 helper for storing and verifying SLauncher 6 numeric Lock Screen PINs
 * without keeping plaintext passcodes in SharedPreferences.
 */
object PinSecurityHelper {

  const val MIN_PIN_LENGTH = 4
  const val MAX_PIN_LENGTH = 6

  fun isValidPinFormat(pin: String): Boolean =
    pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it.isDigit() }

  fun generateSalt(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
  }

  fun hashPin(pin: String, salt: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val payload = "$salt:$pin".toByteArray(Charsets.UTF_8)
    return Base64.encodeToString(digest.digest(payload), Base64.NO_WRAP)
  }

  fun verifyPin(pin: String, expectedHash: String?, salt: String?): Boolean {
    if (expectedHash.isNullOrBlank() || salt.isNullOrBlank() || pin.isEmpty()) return false
    val candidate = hashPin(pin, salt)
    return MessageDigest.isEqual(
      candidate.toByteArray(Charsets.UTF_8),
      expectedHash.toByteArray(Charsets.UTF_8),
    )
  }
}
