package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.debug.DebugLogStore
import com.example.debug.SystemDiagnosticsSnapshot
import com.example.shizuku.ShizukuCommandResult
import com.example.shizuku.ShizukuConnectionState
import com.example.ui.theme.SLauncherColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Built-in SLauncher 6 Debugging Console showing:
 * - Real-time health & diagnostic badges (Shizuku binder, API_V23 perm, UserService UID,
 *   Overlay, Notification Listener, Default Home, Biometric HW, Keyguard Secure / lockscreen.disabled)
 * - 1-tap Re-bind Shizuku & Anti Double-Lock diagnostic actions
 * - Interactive ADB/Shizuku shell command runner with quick preset commands
 * - Auto-scrolling monospace live log viewer with Copy & Clear
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugConsoleDialog(
  shizukuState: ShizukuConnectionState,
  diagnostics: SystemDiagnosticsSnapshot?,
  onRefreshDiagnostics: () -> Unit,
  onRebindShizuku: ((ShizukuCommandResult) -> Unit) -> Unit,
  onTestDisableKeyguard: ((ShizukuCommandResult) -> Unit) -> Unit,
  onRunCommand: (String, (ShizukuCommandResult) -> Unit) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val logs by DebugLogStore.logs.collectAsStateWithLifecycle()
  val logListState = rememberLazyListState()

  var commandInput by remember { mutableStateOf("id") }
  var lastCommandResult by remember { mutableStateOf<ShizukuCommandResult?>(null) }
  var isRunning by remember { mutableStateOf(false) }

  LaunchedEffect(logs.size) {
    if (logs.isNotEmpty()) {
      logListState.animateScrollToItem(logs.lastIndex)
    }
  }

  fun executeShell(cmd: String) {
    val trimmed = cmd.trim()
    if (trimmed.isEmpty() || isRunning) return
    isRunning = true
    onRunCommand(trimmed) { result ->
      lastCommandResult = result
      isRunning = false
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      contentAlignment = Alignment.TopCenter,
      modifier =
        Modifier.fillMaxSize()
          .background(Color(0xF5070A12))
          .statusBarsPadding()
          .navigationBarsPadding(),
    ) {
      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
          Modifier.fillMaxSize()
            .widthIn(max = 620.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("debug_console_dialog"),
      ) {
        // Header
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(40.dp)
                .clip(CircleShape)
                .background(SLauncherColors.AccentContainer)
                .border(1.dp, SLauncherColors.AccentPrimary.copy(alpha = 0.5f), CircleShape),
          ) {
            Icon(
              imageVector = Icons.Rounded.BugReport,
              contentDescription = null,
              tint = SLauncherColors.AccentPrimary,
              modifier = Modifier.size(20.dp),
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.debug_console_title),
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
              color = SLauncherColors.TextPrimary,
            )
            Text(
              text = stringResource(R.string.debug_console_subtitle),
              style = MaterialTheme.typography.labelMedium,
              color = SLauncherColors.TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          IconButton(
            onClick = onRefreshDiagnostics,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(SLauncherColors.DarkGlassPill)
                .testTag("debug_refresh_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Refresh,
              contentDescription = stringResource(R.string.debug_section_diagnostics),
              tint = SLauncherColors.AccentPrimary,
            )
          }
          Spacer(modifier = Modifier.width(6.dp))
          IconButton(
            onClick = onDismiss,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(SLauncherColors.DarkGlassPill)
                .testTag("debug_close_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(R.string.action_done),
              tint = SLauncherColors.TextPrimary,
            )
          }
        }

        // System Status Diagnostic Pills
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(20.dp))
              .background(SLauncherColors.DarkGlassCard)
              .border(1.dp, SLauncherColors.GlassBorder, RoundedCornerShape(20.dp))
              .padding(12.dp),
        ) {
          Text(
            text = "${stringResource(R.string.debug_section_diagnostics)} • ${shizukuState.name}",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = SLauncherColors.AccentPrimary,
          )
          val snap = diagnostics
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            DiagnosticBadge("Installed", snap?.shizukuInstalled == true)
            DiagnosticBadge("Binder", snap?.shizukuBinderAlive == true)
            DiagnosticBadge("API_V23", snap?.shizukuPermissionGranted == true)
            DiagnosticBadge("UserService", snap?.userServiceBound == true)
            DiagnosticBadge("DefaultHome", snap?.isDefaultHome == true)
            DiagnosticBadge("Overlay", snap?.hasOverlayPermission == true)
            DiagnosticBadge("NotifListener", snap?.hasNotificationAccess == true)
            DiagnosticBadge(
              "Bio:${snap?.biometricStatusLabel?.ifBlank { "?" } ?: "?"}",
              snap?.biometricStatusLabel == "READY",
            )
            DiagnosticBadge(
              if (snap?.systemKeyguardSecure == true) "SysLock:ON" else "SysLock:OFF",
              snap?.systemKeyguardSecure == false,
            )
          }
          if (!snap?.userServiceUidOutput.isNullOrBlank()) {
            Text(
              text = "UID: ${snap?.userServiceUidOutput} • lockscreen.disabled=${snap?.systemLockscreenDisabledSetting}",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
              color = SLauncherColors.TextSecondary,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Button(
              onClick = {
                if (!isRunning) {
                  isRunning = true
                  onRebindShizuku { res ->
                    lastCommandResult = res
                    isRunning = false
                  }
                }
              },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = SLauncherColors.AccentPrimary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
              shape = RoundedCornerShape(50),
              modifier = Modifier.weight(1f).testTag("debug_rebind_button"),
            ) {
              Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(stringResource(R.string.debug_btn_rebind), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
              onClick = {
                if (!isRunning) {
                  isRunning = true
                  onTestDisableKeyguard { res ->
                    lastCommandResult = res
                    isRunning = false
                  }
                }
              },
              shape = RoundedCornerShape(50),
              modifier = Modifier.weight(1f).testTag("debug_disable_keyguard_button"),
            ) {
              Icon(
                Icons.Rounded.LockOpen,
                contentDescription = null,
                tint = SLauncherColors.AccentPrimary,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = stringResource(R.string.debug_btn_disable_keyguard),
                color = SLauncherColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        // Interactive ADB / Shizuku Shell Bar
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(20.dp))
              .background(SLauncherColors.DarkGlassCard)
              .border(1.dp, SLauncherColors.GlassBorder, RoundedCornerShape(20.dp))
              .padding(12.dp),
        ) {
          Text(
            text = stringResource(R.string.debug_section_shell),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = SLauncherColors.AccentPrimary,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedTextField(
              value = commandInput,
              onValueChange = { commandInput = it },
              singleLine = true,
              placeholder = {
                Text(
                  text = stringResource(R.string.debug_cmd_hint),
                  color = SLauncherColors.TextMuted,
                  fontFamily = FontFamily.Monospace,
                  fontSize = 12.sp,
                )
              },
              textStyle =
                MaterialTheme.typography.bodyMedium.copy(
                  fontFamily = FontFamily.Monospace,
                  color = SLauncherColors.TextPrimary,
                ),
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
              keyboardActions = KeyboardActions(onSend = { executeShell(commandInput) }),
              shape = RoundedCornerShape(14.dp),
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = SLauncherColors.AccentPrimary,
                  unfocusedBorderColor = SLauncherColors.GlassBorder,
                  cursorColor = SLauncherColors.AccentPrimary,
                ),
              modifier = Modifier.weight(1f).testTag("debug_shell_input"),
            )
            Button(
              onClick = { executeShell(commandInput) },
              enabled = !isRunning && commandInput.isNotBlank(),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = SLauncherColors.AccentPrimary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.height(52.dp).testTag("debug_shell_run_button"),
            ) {
              Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.debug_cmd_run))
            }
          }

          // Quick preset ADB commands
          val presets =
            listOf(
              "id",
              "settings get secure lockscreen.disabled",
              "dumpsys window policy",
              "appops get com.aistudio.slauncher6.kxzqvm SYSTEM_ALERT_WINDOW",
            )
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          ) {
            presets.forEachIndexed { idx, preset ->
              Box(
                modifier =
                  Modifier.clip(RoundedCornerShape(50))
                    .background(SLauncherColors.DarkGlassPill)
                    .border(1.dp, SLauncherColors.GlassBorder, RoundedCornerShape(50))
                    .clickable {
                      commandInput = preset
                      executeShell(preset)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("debug_preset_$idx"),
              ) {
                Text(
                  text = preset,
                  style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                  color = SLauncherColors.TextSecondary,
                  maxLines = 1,
                )
              }
            }
          }

          lastCommandResult?.let { res ->
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .background(Color.Black.copy(alpha = 0.55f))
                  .border(
                    1.dp,
                    if (res.success) SLauncherColors.SoftCapsuleEmerald.copy(alpha = 0.45f)
                    else SLauncherColors.WarningSoft.copy(alpha = 0.45f),
                    RoundedCornerShape(12.dp),
                  )
                  .padding(10.dp),
            ) {
              Text(
                text = "[exit=${res.exitCode}] ${res.output.ifBlank { "(no output)" }}",
                style =
                  MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                  ),
                color =
                  if (res.success) SLauncherColors.SoftCapsuleEmerald
                  else SLauncherColors.WarningSoft,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        // Live Event Log Stream
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .clip(RoundedCornerShape(20.dp))
              .background(Color(0xCC060910))
              .border(1.dp, SLauncherColors.GlassBorder, RoundedCornerShape(20.dp))
              .padding(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = stringResource(R.string.debug_section_logs, logs.size),
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = SLauncherColors.TextPrimary,
              modifier = Modifier.weight(1f),
            )
            OutlinedButton(
              onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val text = DebugLogStore.formatAllAsText()
                cm?.setPrimaryClip(ClipData.newPlainText("SLauncher6_DebugLogs", text))
                Toast.makeText(
                    context,
                    context.getString(R.string.debug_logs_copied),
                    Toast.LENGTH_SHORT,
                  )
                  .show()
              },
              shape = RoundedCornerShape(50),
              modifier = Modifier.height(34.dp).testTag("debug_copy_logs_button"),
            ) {
              Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.debug_btn_copy), fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.width(6.dp))
            OutlinedButton(
              onClick = { DebugLogStore.clear() },
              shape = RoundedCornerShape(50),
              modifier = Modifier.height(34.dp).testTag("debug_clear_logs_button"),
            ) {
              Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.debug_btn_clear), fontSize = 11.sp)
            }
          }

          if (logs.isEmpty()) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
              Text(
                text = stringResource(R.string.debug_no_logs),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = SLauncherColors.TextMuted,
              )
            }
          } else {
            val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
            LazyColumn(
              state = logListState,
              verticalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
              items(items = logs, key = { it.id }) { entry ->
                val ts = timeFmt.format(Date(entry.timestampMs))
                Text(
                  text = "[$ts][${entry.category}] ${entry.message}",
                  style =
                    MaterialTheme.typography.labelSmall.copy(
                      fontFamily = FontFamily.Monospace,
                      fontSize = 11.sp,
                      lineHeight = 15.sp,
                    ),
                  color =
                    if (entry.isError) SLauncherColors.WarningSoft
                    else SLauncherColors.SoftCapsuleEmerald,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DiagnosticBadge(label: String, ok: Boolean) {
  val bg =
    if (ok) SLauncherColors.SoftCapsuleEmerald.copy(alpha = 0.14f)
    else SLauncherColors.WarningSoft.copy(alpha = 0.14f)
  val border =
    if (ok) SLauncherColors.SoftCapsuleEmerald.copy(alpha = 0.45f)
    else SLauncherColors.WarningSoft.copy(alpha = 0.45f)
  val fg = if (ok) SLauncherColors.SoftCapsuleEmerald else SLauncherColors.WarningSoft

  Box(
    modifier =
      Modifier.clip(RoundedCornerShape(50))
        .background(bg)
        .border(0.9.dp, border, RoundedCornerShape(50))
        .padding(horizontal = 9.dp, vertical = 4.dp),
  ) {
    Text(
      text = if (ok) "● $label" else "○ $label",
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp),
      color = fg,
    )
  }
}
