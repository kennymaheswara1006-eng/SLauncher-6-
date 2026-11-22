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
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.adb.AdbCommandResult
import com.example.adb.AdbConnectionState
import com.example.adb.AdbDiscoveryHelper
import com.example.adb.LocalAdbBridge
import com.example.debug.DebugLogStore
import com.example.debug.SystemDiagnosticsSnapshot
import com.example.ui.theme.SLauncherColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Self-contained SLauncher 6 Built-in USB & Wireless ADB Debugging Console showing:
 * - Real-time diagnostic pills (USB Debugging, Wireless Debugging, Built-in ADB Socket,
 *   USB OTG ADB, Notification Policy / Heads-Up Suppression, Overlay, Listener, DefaultHome, Bio)
 * - Direct Host + Port input & mDNS Wireless ADB auto-scanner (`_adb-tls-connect._tcp`)
 * - Interactive built-in ADB shell command runner with presets
 * - Live auto-scrolling monospace event log stream with Copy & Clear
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugConsoleDialog(
  adbState: AdbConnectionState,
  diagnostics: SystemDiagnosticsSnapshot?,
  onRefreshDiagnostics: () -> Unit,
  onConnectAdb: (String, Int, (AdbCommandResult) -> Unit) -> Unit,
  onScanWirelessAdb: () -> Unit,
  onOpenDeveloperOptions: () -> Unit,
  onTestDisableKeyguard: ((AdbCommandResult) -> Unit) -> Unit,
  onRunCommand: (String, (AdbCommandResult) -> Unit) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val logs by DebugLogStore.logs.collectAsStateWithLifecycle()
  val discoveredEndpoints by AdbDiscoveryHelper.discoveredEndpoints.collectAsStateWithLifecycle()
  val isScanning by AdbDiscoveryHelper.isScanning.collectAsStateWithLifecycle()
  val savedHost by LocalAdbBridge.targetHost.collectAsStateWithLifecycle()
  val savedPort by LocalAdbBridge.targetPort.collectAsStateWithLifecycle()
  val logListState = rememberLazyListState()

  var hostInput by remember(savedHost) { mutableStateOf(savedHost) }
  var portInput by remember(savedPort) { mutableStateOf(savedPort.toString()) }
  var commandInput by remember { mutableStateOf("id") }
  var lastCommandResult by remember { mutableStateOf<AdbCommandResult?>(null) }
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
          Modifier.fillMaxSize()
            .widthIn(max = 620.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
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

        // System Status Diagnostic Pills + Built-in USB/Wireless ADB Connection Controls
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
            text = "${stringResource(R.string.debug_section_diagnostics)} • ${adbState.name}",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = SLauncherColors.AccentPrimary,
          )
          val snap = diagnostics
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            DiagnosticBadge("USB_DBG", snap?.usbDebuggingEnabled == true)
            DiagnosticBadge("WIFI_DBG", snap?.wirelessDebuggingEnabled == true)
            DiagnosticBadge("ADB_CONN", snap?.adbConnected == true)
            DiagnosticBadge("HeadsUpOff", snap?.systemHeadsUpSuppressed == true)
            DiagnosticBadge("DND_Policy", snap?.hasNotificationPolicyAccess == true)
            DiagnosticBadge("Overlay", snap?.hasOverlayPermission == true)
            DiagnosticBadge("Listener", snap?.hasNotificationAccess == true)
            DiagnosticBadge("Home", snap?.isDefaultHome == true)
            DiagnosticBadge(
              "Bio:${snap?.biometricStatusLabel?.ifBlank { "?" } ?: "?"}",
              snap?.biometricStatusLabel == "READY",
            )
          }

          if (snap != null) {
            Text(
              text = "IP: ${snap.localIpAddress} • USB-OTG: ${snap.usbOtgAdbCount} • UID: ${snap.adbShellUidOutput}",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
              color = SLauncherColors.TextSecondary,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }

          // Host & Port input row
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedTextField(
              value = hostInput,
              onValueChange = { hostInput = it },
              singleLine = true,
              label = { Text(stringResource(R.string.settings_adb_host_label), fontSize = 11.sp) },
              textStyle =
                MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  color = SLauncherColors.TextPrimary,
                ),
              shape = RoundedCornerShape(12.dp),
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = SLauncherColors.AccentPrimary,
                  unfocusedBorderColor = SLauncherColors.GlassBorder,
                  cursorColor = SLauncherColors.AccentPrimary,
                ),
              modifier = Modifier.weight(1.6f).testTag("debug_adb_host_input"),
            )
            OutlinedTextField(
              value = portInput,
              onValueChange = { raw -> portInput = raw.filter { it.isDigit() }.take(5) },
              singleLine = true,
              label = { Text(stringResource(R.string.settings_adb_port_label), fontSize = 11.sp) },
              textStyle =
                MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  color = SLauncherColors.TextPrimary,
                ),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              shape = RoundedCornerShape(12.dp),
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = SLauncherColors.AccentPrimary,
                  unfocusedBorderColor = SLauncherColors.GlassBorder,
                  cursorColor = SLauncherColors.AccentPrimary,
                ),
              modifier = Modifier.weight(1f).testTag("debug_adb_port_input"),
            )
          }

          if (discoveredEndpoints.isNotEmpty()) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
              discoveredEndpoints.forEachIndexed { idx, ep ->
                Box(
                  modifier =
                    Modifier.clip(RoundedCornerShape(50))
                      .background(SLauncherColors.AccentContainer)
                      .border(1.dp, SLauncherColors.AccentPrimary.copy(alpha = 0.5f), RoundedCornerShape(50))
                      .clickable {
                        hostInput = ep.host
                        portInput = ep.port.toString()
                      }
                      .padding(horizontal = 10.dp, vertical = 5.dp)
                      .testTag("debug_discovered_ep_$idx"),
                ) {
                  Text(
                    text = "${ep.host}:${ep.port}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = SLauncherColors.AccentPrimary,
                  )
                }
              }
            }
          }

          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Button(
              onClick = {
                if (!isRunning) {
                  isRunning = true
                  val port = portInput.toIntOrNull() ?: LocalAdbBridge.DEFAULT_PORT
                  onConnectAdb(hostInput, port) { res ->
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
              modifier = Modifier.weight(1f).testTag("debug_connect_adb_button"),
            ) {
              Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.debug_btn_reconnect), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
              onClick = onScanWirelessAdb,
              shape = RoundedCornerShape(50),
              modifier = Modifier.weight(1f).testTag("debug_scan_adb_button"),
            ) {
              Icon(
                Icons.Rounded.Radar,
                contentDescription = null,
                tint = SLauncherColors.AccentPrimary,
                modifier = Modifier.size(15.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (isScanning) "Scanning…" else stringResource(R.string.settings_adb_scan_btn),
                color = SLauncherColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedButton(
              onClick = onOpenDeveloperOptions,
              shape = RoundedCornerShape(50),
              modifier = Modifier.weight(1f).testTag("debug_dev_options_button"),
            ) {
              Icon(
                Icons.Rounded.Settings,
                contentDescription = null,
                tint = SLauncherColors.AccentPrimary,
                modifier = Modifier.size(15.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = stringResource(R.string.settings_adb_dev_options_btn),
                color = SLauncherColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
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
                modifier = Modifier.size(15.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = stringResource(R.string.debug_btn_disable_keyguard),
                color = SLauncherColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        // Interactive Built-in ADB Shell Bar
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

          val presets =
            listOf(
              "id",
              "settings get global heads_up_notifications_enabled",
              "settings get secure lockscreen.disabled",
              "dumpsys notification --noredact | head -n 20",
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
