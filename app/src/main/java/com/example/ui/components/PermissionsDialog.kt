package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.SLauncherColors

/**
 * Clean Dark Glass onboarding & permissions checklist dialog guiding the user through
 * Notifications (API 33+), Coarse Location (Weather), Notification Listener Access,
 * and Default Home Launcher setup.
 */
@Composable
fun PermissionsDialog(
  isPostNotificationsGranted: Boolean,
  isLocationGranted: Boolean,
  isNotificationAccessGranted: Boolean,
  isNotificationPolicyAccessGranted: Boolean,
  isOverlayPermissionGranted: Boolean,
  adbState: com.example.adb.AdbConnectionState,
  isDefaultHome: Boolean,
  onRequestPostNotifications: () -> Unit,
  onRequestLocation: () -> Unit,
  onRequestNotificationAccess: () -> Unit,
  onRequestNotificationPolicyAccess: () -> Unit,
  onRequestOverlayPermission: () -> Unit,
  onConnectAdb: () -> Unit,
  onRequestDefaultHome: () -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    val dialogShape = RoundedCornerShape(28.dp)
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 24.dp)
          .clip(dialogShape)
          .background(SLauncherColors.DarkGlassElevated)
          .border(1.dp, SLauncherColors.GlassBorderGradient, dialogShape)
          .padding(20.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.size(40.dp)
              .clip(CircleShape)
              .background(SLauncherColors.AccentContainer),
        ) {
          Icon(
            imageVector = Icons.Rounded.VerifiedUser,
            contentDescription = null,
            tint = SLauncherColors.AccentPrimary,
            modifier = Modifier.size(22.dp),
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = stringResource(R.string.permissions_title),
            style = MaterialTheme.typography.headlineSmall,
            color = SLauncherColors.TextPrimary,
          )
          Text(
            text = stringResource(R.string.permissions_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = SLauncherColors.TextSecondary,
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
      ) {
        PermissionItemCard(
          icon = Icons.Rounded.LocationOn,
          title = stringResource(R.string.perm_location_title),
          description = stringResource(R.string.perm_location_desc),
          isGranted = isLocationGranted,
          actionLabel = stringResource(R.string.perm_action_allow),
          onAction = onRequestLocation,
          testTag = "perm_location_btn",
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          PermissionItemCard(
            icon = Icons.Rounded.Notifications,
            title = stringResource(R.string.perm_notifications_title),
            description = stringResource(R.string.perm_notifications_desc),
            isGranted = isPostNotificationsGranted,
            actionLabel = stringResource(R.string.perm_action_allow),
            onAction = onRequestPostNotifications,
            testTag = "perm_post_notifications_btn",
          )
        }

        PermissionItemCard(
          icon = Icons.Rounded.NotificationsActive,
          title = stringResource(R.string.perm_notif_listener_title),
          description = stringResource(R.string.perm_notif_listener_desc),
          isGranted = isNotificationAccessGranted,
          actionLabel = stringResource(R.string.perm_action_setup),
          onAction = onRequestNotificationAccess,
          testTag = "perm_notif_listener_btn",
        )

        PermissionItemCard(
          icon = Icons.Rounded.NotificationsActive,
          title = stringResource(R.string.perm_overlay_title),
          description = stringResource(R.string.perm_overlay_desc),
          isGranted = isOverlayPermissionGranted,
          actionLabel = stringResource(R.string.perm_action_setup),
          onAction = onRequestOverlayPermission,
          testTag = "perm_overlay_btn",
        )

        PermissionItemCard(
          icon = Icons.Rounded.Notifications,
          title = stringResource(R.string.perm_notif_policy_title),
          description = stringResource(R.string.perm_notif_policy_desc),
          isGranted = isNotificationPolicyAccessGranted,
          actionLabel = stringResource(R.string.perm_action_setup),
          onAction = onRequestNotificationPolicyAccess,
          testTag = "perm_notif_policy_btn",
        )

        PermissionItemCard(
          icon = Icons.Rounded.VerifiedUser,
          title = stringResource(R.string.perm_adb_title),
          description = stringResource(R.string.perm_adb_desc),
          isGranted = adbState == com.example.adb.AdbConnectionState.CONNECTED,
          actionLabel = stringResource(R.string.perm_action_setup),
          onAction = onConnectAdb,
          testTag = "perm_adb_btn",
        )

        PermissionItemCard(
          icon = Icons.Rounded.Home,
          title = stringResource(R.string.perm_default_home_title),
          description = stringResource(R.string.perm_default_home_desc),
          isGranted = isDefaultHome,
          actionLabel = stringResource(R.string.perm_action_setup),
          onAction = onRequestDefaultHome,
          testTag = "perm_default_home_btn",
        )
      }

      Spacer(modifier = Modifier.height(18.dp))

      Button(
        onClick = onDismiss,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = SLauncherColors.AccentPrimary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        shape = RoundedCornerShape(50),
        modifier =
          Modifier.fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("close_permissions_button"),
      ) {
        Text(
          text = stringResource(R.string.action_done),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}

@Composable
private fun PermissionItemCard(
  icon: ImageVector,
  title: String,
  description: String,
  isGranted: Boolean,
  actionLabel: String,
  onAction: () -> Unit,
  testTag: String,
) {
  val cardShape = RoundedCornerShape(20.dp)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth()
        .clip(cardShape)
        .background(SLauncherColors.GlassSurfaceGradient)
        .border(
          1.dp,
          if (isGranted) SLauncherColors.BatteryCharging.copy(alpha = 0.45f)
          else SLauncherColors.GlassBorder,
          cardShape,
        )
        .padding(14.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (isGranted) SLauncherColors.BatteryCharging else SLauncherColors.AccentPrimary,
      modifier = Modifier.size(22.dp),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        color = SLauncherColors.TextPrimary,
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = SLauncherColors.TextSecondary,
      )
    }

    if (isGranted) {
      Icon(
        imageVector = Icons.Rounded.CheckCircle,
        contentDescription = stringResource(R.string.perm_status_granted),
        tint = SLauncherColors.BatteryCharging,
        modifier = Modifier.size(20.dp),
      )
    } else {
      OutlinedButton(
        onClick = onAction,
        shape = RoundedCornerShape(50),
        modifier = Modifier.minimumInteractiveComponentSize().testTag(testTag),
      ) {
        Text(
          text = actionLabel,
          style = MaterialTheme.typography.labelLarge,
          color = SLauncherColors.AccentPrimary,
        )
      }
    }
  }
}
