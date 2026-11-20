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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.AppModel
import com.example.ui.theme.SLauncherColors

/**
 * Clean Dark Glass dialog inside SLauncher Settings for hiding or unhiding installed apps
 * from the Home Screen, App Drawer, and search results.
 */
@Composable
fun HiddenAppsDialog(
  allInstalledApps: List<AppModel>,
  currentlyHiddenKeys: Set<String>,
  loadIcon: suspend (AppModel, Int) -> ImageBitmap?,
  onDismiss: () -> Unit,
  onSaveSelection: (Set<String>) -> Unit,
) {
  val hiddenKeys =
    remember(currentlyHiddenKeys) {
      mutableStateListOf<String>().apply { addAll(currentlyHiddenKeys) }
    }
  var filterText by remember { mutableStateOf("") }

  val filteredApps =
    remember(allInstalledApps, filterText) {
      val trimmed = filterText.trim()
      if (trimmed.isEmpty()) allInstalledApps
      else allInstalledApps.filter { it.label.contains(trimmed, ignoreCase = true) }
    }

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
      Text(
        text = stringResource(R.string.hide_apps_dialog_title),
        style = MaterialTheme.typography.headlineSmall,
        color = SLauncherColors.TextPrimary,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = stringResource(R.string.hide_apps_dialog_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = SLauncherColors.TextSecondary,
      )

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = filterText,
        onValueChange = { filterText = it },
        singleLine = true,
        placeholder = {
          Text(
            text = stringResource(R.string.search_apps_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = SLauncherColors.TextMuted,
          )
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = SLauncherColors.TextSecondary,
          )
        },
        shape = RoundedCornerShape(16.dp),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SLauncherColors.AccentPrimary,
            unfocusedBorderColor = SLauncherColors.GlassBorder,
            focusedTextColor = SLauncherColors.TextPrimary,
            unfocusedTextColor = SLauncherColors.TextPrimary,
            cursorColor = SLauncherColors.AccentPrimary,
          ),
        modifier = Modifier.fillMaxWidth().testTag("hidden_apps_filter_input"),
      )

      Spacer(modifier = Modifier.height(12.dp))

      LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        items(
          items = filteredApps,
          key = { it.componentKey },
          contentType = { "hide_app_choice" },
        ) { app ->
          val isHidden = hiddenKeys.contains(app.componentKey)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                  if (isHidden) SLauncherColors.AccentContainer else Color.Transparent
                )
                .clickable {
                  if (isHidden) hiddenKeys.remove(app.componentKey)
                  else hiddenKeys.add(app.componentKey)
                }
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("hide_app_toggle_${app.packageName}"),
          ) {
            AppIconImage(app = app, loadIcon = loadIcon, size = 34.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = app.label,
              style = MaterialTheme.typography.bodyLarge,
              color = if (isHidden) SLauncherColors.TextMuted else SLauncherColors.TextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
              imageVector =
                if (isHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
              contentDescription = null,
              tint = if (isHidden) SLauncherColors.WarningSoft else SLauncherColors.TextSecondary,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("cancel_hidden_apps_button"),
        ) {
          Text(
            text = stringResource(R.string.action_cancel),
            color = SLauncherColors.TextSecondary,
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
          onClick = { onSaveSelection(hiddenKeys.toSet()) },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = SLauncherColors.AccentPrimary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          shape = RoundedCornerShape(50),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("save_hidden_apps_button"),
        ) {
          Text(
            text = stringResource(R.string.action_done),
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }
  }
}
