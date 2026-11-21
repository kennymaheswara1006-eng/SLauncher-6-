package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.security.PinSecurityHelper
import com.example.ui.theme.SLauncherColors
import com.example.ui.theme.softDeepCapsule
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Two-step 4–6 digit numeric PIN setup dialog styled in Dark Minimalist glassmorphism.
 * Step 1: Enter PIN -> Tap Confirm/Next -> Step 2: Re-enter PIN to verify match and save.
 */
@Composable
fun PinSetupDialog(
  hasExistingPin: Boolean,
  onSavePin: (String) -> Unit,
  onRemovePin: () -> Unit,
  onDismiss: () -> Unit,
) {
  val haptic = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  val shakeOffset = remember { Animatable(0f) }

  var firstPin by remember { mutableStateOf<String?>(null) }
  var currentInput by remember { mutableStateOf("") }
  var showMismatchError by remember { mutableStateOf(false) }

  val isConfirmStep = firstPin != null
  val maxLen = if (isConfirmStep) (firstPin?.length ?: PinSecurityHelper.MAX_PIN_LENGTH) else PinSecurityHelper.MAX_PIN_LENGTH

  fun triggerShake() {
    scope.launch {
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
      shakeOffset.snapTo(0f)
      for (x in listOf(-18f, 18f, -12f, 12f, -6f, 0f)) {
        shakeOffset.animateTo(x, tween(42))
      }
    }
  }

  fun onDigitTap(digit: String) {
    showMismatchError = false
    if (currentInput.length >= maxLen) return
    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    val next = currentInput + digit
    currentInput = next

    if (isConfirmStep && next.length == firstPin?.length) {
      if (next == firstPin) {
        onSavePin(next)
      } else {
        showMismatchError = true
        currentInput = ""
        triggerShake()
      }
    }
  }

  fun onBackspace() {
    showMismatchError = false
    if (currentInput.isNotEmpty()) {
      currentInput = currentInput.dropLast(1)
    }
  }

  fun onContinueToConfirm() {
    if (!PinSecurityHelper.isValidPinFormat(currentInput)) return
    firstPin = currentInput
    currentInput = ""
    showMismatchError = false
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier =
        Modifier.padding(horizontal = 24.dp)
          .widthIn(max = 380.dp)
          .fillMaxWidth()
          .softDeepCapsule(cornerRadius = 30.dp, elevation = 24.dp)
          .padding(horizontal = 22.dp, vertical = 22.dp)
          .testTag("pin_setup_dialog"),
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.size(46.dp)
            .clip(CircleShape)
            .background(SLauncherColors.AccentContainer)
            .border(1.dp, SLauncherColors.AccentPrimary.copy(alpha = 0.45f), CircleShape),
      ) {
        Icon(
          imageVector = Icons.Rounded.Lock,
          contentDescription = null,
          tint = SLauncherColors.AccentPrimary,
          modifier = Modifier.size(22.dp),
        )
      }

      Text(
        text =
          if (isConfirmStep) stringResource(R.string.pin_setup_title_confirm)
          else stringResource(R.string.pin_setup_title_create),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = SLauncherColors.TextPrimary,
        textAlign = TextAlign.Center,
      )

      Text(
        text =
          if (isConfirmStep) {
            stringResource(R.string.pin_setup_subtitle_confirm, firstPin?.length ?: 4)
          } else {
            stringResource(R.string.pin_setup_subtitle_create)
          },
        style = MaterialTheme.typography.bodyMedium,
        color = SLauncherColors.TextSecondary,
        textAlign = TextAlign.Center,
      )

      if (showMismatchError) {
        Text(
          text = stringResource(R.string.pin_setup_mismatch),
          style = MaterialTheme.typography.labelMedium,
          color = SLauncherColors.WarningSoft,
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag("pin_setup_error_text"),
        )
      }

      // Luminous PIN Dots Indicator
      Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
          Modifier.padding(vertical = 6.dp)
            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
      ) {
        val dotCount = if (isConfirmStep) (firstPin?.length ?: 4) else PinSecurityHelper.MAX_PIN_LENGTH
        for (i in 0 until dotCount) {
          val filled = i < currentInput.length
          val optionalSlot = !isConfirmStep && i >= PinSecurityHelper.MIN_PIN_LENGTH
          Box(
            modifier =
              Modifier.size(if (filled) 15.dp else 13.dp)
                .clip(CircleShape)
                .background(
                  when {
                    filled -> SLauncherColors.AccentPrimary
                    optionalSlot -> Color.White.copy(alpha = 0.08f)
                    else -> Color.White.copy(alpha = 0.22f)
                  }
                )
                .border(
                  width = 1.dp,
                  color =
                    if (filled) SLauncherColors.AccentPrimary
                    else SLauncherColors.GlassBorder,
                  shape = CircleShape,
                )
          )
        }
      }

      // 3x4 Numeric Keypad
      val rows =
        listOf(
          listOf("1", "2", "3"),
          listOf("4", "5", "6"),
          listOf("7", "8", "9"),
        )
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        rows.forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            row.forEach { digit ->
              PinSetupKeyButton(
                label = digit,
                onClick = { onDigitTap(digit) },
                modifier = Modifier.testTag("pin_setup_key_$digit"),
              )
            }
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          // Left bottom: Confirm Checkmark on Step 1 when 4-6 digits typed
          val canAdvance = !isConfirmStep && PinSecurityHelper.isValidPinFormat(currentInput)
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(62.dp)
                .clip(CircleShape)
                .background(
                  if (canAdvance) SLauncherColors.AccentContainer else Color.Transparent
                )
                .border(
                  width = 1.dp,
                  color = if (canAdvance) SLauncherColors.AccentPrimary else Color.Transparent,
                  shape = CircleShape,
                )
                .clickable(enabled = canAdvance) { onContinueToConfirm() }
                .testTag("pin_setup_next_key"),
          ) {
            if (canAdvance) {
              Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.action_done),
                tint = SLauncherColors.AccentPrimary,
                modifier = Modifier.size(24.dp),
              )
            }
          }

          PinSetupKeyButton(
            label = "0",
            onClick = { onDigitTap("0") },
            modifier = Modifier.testTag("pin_setup_key_0"),
          )

          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(62.dp)
                .clip(CircleShape)
                .background(SLauncherColors.DarkGlassPill)
                .border(1.dp, SLauncherColors.GlassBorder, CircleShape)
                .clickable { onBackspace() }
                .testTag("pin_setup_backspace_key"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.Backspace,
              contentDescription = stringResource(R.string.cd_clear_search),
              tint = SLauncherColors.TextPrimary,
              modifier = Modifier.size(22.dp),
            )
          }
        }
      }

      if (!isConfirmStep && PinSecurityHelper.isValidPinFormat(currentInput)) {
        Button(
          onClick = { onContinueToConfirm() },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = SLauncherColors.AccentPrimary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          shape = RoundedCornerShape(50),
          modifier = Modifier.fillMaxWidth().testTag("pin_setup_continue_button"),
        ) {
          Text(stringResource(R.string.pin_setup_title_confirm))
        }
      }

      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        if (hasExistingPin && !isConfirmStep) {
          TextButton(
            onClick = onRemovePin,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("pin_setup_remove_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.DeleteOutline,
              contentDescription = null,
              tint = SLauncherColors.WarningSoft,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = " ${stringResource(R.string.pin_setup_remove_btn)}",
              color = SLauncherColors.WarningSoft,
            )
          }
        } else {
          Spacer(modifier = Modifier.size(1.dp))
        }

        TextButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("pin_setup_cancel_button"),
        ) {
          Text(
            text = stringResource(R.string.action_cancel),
            color = SLauncherColors.TextSecondary,
          )
        }
      }
    }
  }
}

@Composable
private fun PinSetupKeyButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .size(62.dp)
        .clip(CircleShape)
        .background(SLauncherColors.DarkGlassPill)
        .border(1.dp, SLauncherColors.GlassBorder, CircleShape)
        .clickable(onClick = onClick),
  ) {
    Text(
      text = label,
      style =
        MaterialTheme.typography.headlineSmall.copy(
          fontSize = 23.sp,
          fontWeight = FontWeight.Medium,
          fontFeatureSettings = "tnum",
        ),
      color = SLauncherColors.TextPrimary,
    )
  }
}
