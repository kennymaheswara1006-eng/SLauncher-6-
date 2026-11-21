package com.example.shizuku

import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Privileged Shizuku UserService running in the ADB shell (UID 2000) process.
 * Executes bounded non-root system commands on behalf of SLauncher 6 Quick Settings
 * and setup helpers without requiring root or Magisk.
 */
@Keep
class ShizukuShellUserService @Keep constructor() : IShizukuShellService.Stub() {

  override fun destroy() {
    exitProcess(0)
  }

  override fun execCommand(command: String?): String {
    val cmd = command?.trim().orEmpty()
    if (cmd.isEmpty()) return "EXIT:1\nEmpty command"
    return try {
      val process =
        ProcessBuilder("sh", "-c", cmd)
          .redirectErrorStream(true)
          .start()

      val output = StringBuilder()
      BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
        var line = reader.readLine()
        while (line != null) {
          if (output.isNotEmpty()) output.append('\n')
          output.append(line)
          line = reader.readLine()
        }
      }

      val exitCode =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
          val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
          if (!finished) {
            process.destroyForcibly()
            return "EXIT:124\nCommand timed out"
          }
          process.exitValue()
        } else {
          process.waitFor()
        }
      "EXIT:$exitCode\n$output"
    } catch (t: Throwable) {
      "EXIT:1\n${t.message.orEmpty()}"
    }
  }

  companion object {
    private const val COMMAND_TIMEOUT_SECONDS = 5L
  }
}
