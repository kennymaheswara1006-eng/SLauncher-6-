package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppModel
import com.example.ui.theme.SLauncherColors

/**
 * Floating inline search results panel anchored directly above the bottom search capsule
 * so matches stay within natural one-handed thumb reach. When the user types the secret
 * hidden-apps code, reveals the Hidden Apps Vault with launch & unhide actions.
 */
@Composable
fun InlineSearchResultsCard(
  visible: Boolean,
  query: String,
  results: List<AppModel>,
  isSecretVaultMode: Boolean = false,
  loadIcon: suspend (AppModel, Int) -> ImageBitmap?,
  onAppClick: (AppModel) -> Unit,
  onUnhideApp: ((AppModel) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn() + slideInVertically { it / 4 },
    exit = fadeOut() + slideOutVertically { it / 4 },
    modifier = modifier,
  ) {
    val cardShape = RoundedCornerShape(24.dp)
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .clip(cardShape)
          .background(SLauncherColors.GlassFloatingCardGradient)
          .border(
            width = if (isSecretVaultMode) 1.2.dp else 1.dp,
            color =
              if (isSecretVaultMode) SLauncherColors.WarningSoft.copy(alpha = 0.65f)
              else SLauncherColors.GlassBorder,
            shape = cardShape,
          )
          .testTag(if (isSecretVaultMode) "secret_vault_results_card" else "search_results_card"),
    ) {
      if (isSecretVaultMode) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier.fillMaxWidth()
              .background(SLauncherColors.WarningSoft.copy(alpha = 0.14f))
              .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.LockOpen,
            contentDescription = null,
            tint = SLauncherColors.WarningSoft,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.secret_vault_title),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = SLauncherColors.WarningSoft,
            modifier = Modifier.weight(1f),
          )
          Text(
            text = results.size.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = SLauncherColors.TextSecondary,
          )
        }
      }

      if (results.isEmpty()) {
        Box(
          contentAlignment = Alignment.CenterStart,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
          Text(
            text =
              if (isSecretVaultMode) stringResource(R.string.secret_vault_empty)
              else stringResource(R.string.no_search_results, query.trim()),
            style = MaterialTheme.typography.bodyMedium,
            color = SLauncherColors.TextSecondary,
          )
        }
      } else {
        val visibleItems = if (isSecretVaultMode) results else results.take(8)
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).padding(vertical = 6.dp),
        ) {
          items(
            items = visibleItems,
            key = { it.componentKey },
            contentType = { "search_result" },
          ) { app ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 52.dp)
                  .clickable { onAppClick(app) }
                  .padding(horizontal = 16.dp, vertical = 8.dp)
                  .testTag("search_result_row_${app.packageName}"),
            ) {
              AppIconImage(app = app, loadIcon = loadIcon, size = 36.dp)
              Spacer(modifier = Modifier.width(14.dp))
              Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                color = SLauncherColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              if (isSecretVaultMode && onUnhideApp != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .clip(RoundedCornerShape(50))
                      .background(SLauncherColors.AccentContainer)
                      .border(0.8.dp, SLauncherColors.AccentPrimary.copy(alpha = 0.45f), RoundedCornerShape(50))
                      .clickable { onUnhideApp(app) }
                      .padding(horizontal = 10.dp, vertical = 6.dp)
                      .testTag("vault_unhide_${app.packageName}"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Visibility,
                    contentDescription = null,
                    tint = SLauncherColors.AccentPrimary,
                    modifier = Modifier.size(14.dp),
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = stringResource(R.string.action_unhide_app),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = SLauncherColors.AccentPrimary,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Semi-transparent Glassmorphism capsule Instant Search Bar positioned in the thumb zone,
 * paired with a clear button and quick App Drawer trigger.
 */
@Composable
fun SearchBar(
  query: String,
  onQueryChange: (String) -> Unit,
  onClearQuery: () -> Unit,
  onSubmitFirstResult: () -> Unit,
  onOpenDrawer: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val focusManager = LocalFocusManager.current
  val pillShape = RoundedCornerShape(30.dp)

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier =
        Modifier.fillMaxWidth()
          .heightIn(min = 56.dp)
          .clip(pillShape)
          .background(SLauncherColors.GlassFloatingCardGradient)
          .border(1.dp, SLauncherColors.GlassBorderGradient, pillShape)
          .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.Search,
        contentDescription = null,
        tint = SLauncherColors.AccentPrimary,
        modifier = Modifier.size(22.dp),
      )

      Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
      ) {
        if (query.isEmpty()) {
          Text(
            text = stringResource(R.string.search_apps_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = SLauncherColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        BasicTextField(
          value = query,
          onValueChange = onQueryChange,
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyLarge.copy(color = SLauncherColors.TextPrimary),
          cursorBrush = SolidColor(SLauncherColors.AccentPrimary),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions =
            KeyboardActions(
              onSearch = {
                focusManager.clearFocus()
                onSubmitFirstResult()
              }
            ),
          modifier = Modifier.fillMaxWidth().testTag("search_input"),
        )
      }

      if (query.isNotEmpty()) {
        IconButton(
          onClick = onClearQuery,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_search_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.cd_clear_search),
            tint = SLauncherColors.TextSecondary,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      // Subtle vertical separator before App Drawer shortcut button
      HorizontalDivider(
        modifier =
          Modifier.width(1.dp)
            .heightIn(min = 24.dp, max = 24.dp)
            .background(SLauncherColors.GlassBorder),
        color = SLauncherColors.GlassBorder,
      )

      IconButton(
        onClick = {
          focusManager.clearFocus()
          onOpenDrawer()
        },
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(SLauncherColors.AccentContainer)
            .border(1.dp, SLauncherColors.AccentPrimary.copy(alpha = 0.42f), CircleShape)
            .testTag("open_drawer_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Apps,
          contentDescription = stringResource(R.string.cd_open_drawer),
          tint = SLauncherColors.AccentPrimary,
          modifier = Modifier.size(22.dp),
        )
      }
    }
  }
}
