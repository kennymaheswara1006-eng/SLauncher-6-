package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.AppModel
import com.example.ui.theme.SLauncherColors
import com.example.ui.theme.softDeepCapsule

/**
 * Soft Modern Capsule context modal displayed when long-pressing an app icon on Home Screen
 * or in the App Drawer, offering App Info, Hide App, and Uninstall actions.
 */
@Composable
fun AppHoldActionDialog(
  app: AppModel,
  loadIcon: suspend (AppModel, Int) -> ImageBitmap?,
  onOpenAppInfo: (AppModel) -> Unit,
  onHideApp: (AppModel) -> Unit,
  onUninstallApp: (AppModel) -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .widthIn(max = 360.dp)
          .padding(horizontal = 24.dp)
          .softDeepCapsule(
            cornerRadius = 30.dp,
            elevation = 24.dp,
            surfaceBrush =
              Brush.verticalGradient(
                colors = listOf(SLauncherColors.SoftCapsulePanelTop, SLauncherColors.SoftCapsulePanelBottom)
              ),
          )
          .padding(20.dp)
          .testTag("app_hold_action_dialog"),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        AppIconImage(app = app, loadIcon = loadIcon, size = 44.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = app.label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = SLauncherColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = app.packageName,
            style = MaterialTheme.typography.labelSmall,
            color = SLauncherColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppHoldActionRow(
          icon = Icons.Rounded.Info,
          label = stringResource(R.string.action_app_info),
          accentColor = SLauncherColors.AccentPrimary,
          onClick = {
            onDismiss()
            onOpenAppInfo(app)
          },
          testTag = "hold_action_info",
        )

        AppHoldActionRow(
          icon = Icons.Rounded.VisibilityOff,
          label = stringResource(R.string.action_hide_app),
          accentColor = SLauncherColors.WarningSoft,
          onClick = {
            onDismiss()
            onHideApp(app)
          },
          testTag = "hold_action_hide",
        )

        if (app.canUninstall) {
          AppHoldActionRow(
            icon = Icons.Rounded.DeleteOutline,
            label = stringResource(R.string.action_uninstall_app),
            accentColor = SLauncherColors.DestructiveSoft,
            onClick = {
              onDismiss()
              onUninstallApp(app)
            },
            testTag = "hold_action_uninstall",
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth(),
      ) {
        TextButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("hold_action_cancel"),
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
private fun AppHoldActionRow(
  icon: ImageVector,
  label: String,
  accentColor: Color,
  onClick: () -> Unit,
  testTag: String,
) {
  val rowShape = RoundedCornerShape(18.dp)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = 52.dp)
        .clip(rowShape)
        .background(accentColor.copy(alpha = 0.14f))
        .border(1.dp, accentColor.copy(alpha = 0.34f), rowShape)
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 12.dp)
        .testTag(testTag),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = accentColor,
      modifier = Modifier.size(20.dp),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
      color = SLauncherColors.TextPrimary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
