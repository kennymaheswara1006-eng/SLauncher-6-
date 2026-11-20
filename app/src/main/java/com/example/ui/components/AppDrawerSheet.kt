package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppModel
import com.example.ui.theme.SLauncherColors
import kotlinx.coroutines.launch

private val ALPHABET_RAIL_CHARS: List<Char> = ('A'..'Z').toList() + '#'

/**
 * Full-screen Dark Glass App Drawer powered by a lightweight LazyColumn
 * and an interactive A–Z vertical index rail on the right edge for lag-free jumping.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerSheet(
  visible: Boolean,
  allApps: List<AppModel>,
  alphabetIndexMap: Map<Char, Int>,
  loadIcon: suspend (AppModel, Int) -> ImageBitmap?,
  onAppClick: (AppModel) -> Unit,
  onOpenAppInfo: (AppModel) -> Unit,
  onOpenSettings: () -> Unit,
  onClose: () -> Unit,
) {
  AnimatedVisibility(
    visible = visible,
    enter =
      fadeIn() +
        slideInVertically(
          animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow),
          initialOffsetY = { it / 5 },
        ),
    exit = fadeOut() + slideOutVertically { it / 5 },
  ) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var activeLetterBubble by remember { mutableStateOf<Char?>(null) }

    fun jumpToLetter(letter: Char) {
      activeLetterBubble = letter
      val exactIndex = alphabetIndexMap[letter]
      val targetIndex =
        exactIndex
          ?: findNearestSectionIndex(letter = letter, alphabetIndexMap = alphabetIndexMap)
      if (targetIndex != null) {
        coroutineScope.launch { listState.scrollToItem(targetIndex) }
      }
    }

    Box(
      contentAlignment = Alignment.TopCenter,
      modifier =
        Modifier.fillMaxSize()
          .background(SLauncherColors.DarkGlassDrawer)
          .statusBarsPadding()
          .navigationBarsPadding(),
    ) {
      Column(
        modifier = Modifier.fillMaxSize().widthIn(max = 600.dp).padding(horizontal = 16.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
        ) {
          Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
              text = stringResource(R.string.all_apps_title),
              style = MaterialTheme.typography.headlineSmall,
              color = SLauncherColors.TextPrimary,
            )
            Text(
              text = stringResource(R.string.all_apps_count, allApps.size),
              style = MaterialTheme.typography.bodyMedium,
              color = SLauncherColors.TextSecondary,
            )
          }
          IconButton(
            onClick = onOpenSettings,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(SLauncherColors.DarkGlassPill)
                .border(1.dp, SLauncherColors.GlassBorder, CircleShape)
                .testTag("drawer_settings_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Tune,
              contentDescription = stringResource(R.string.cd_open_settings),
              tint = SLauncherColors.AccentPrimary,
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          IconButton(
            onClick = onClose,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(SLauncherColors.DarkGlassPill)
                .border(1.dp, SLauncherColors.GlassBorder, CircleShape)
                .testTag("close_drawer_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(R.string.cd_close_drawer),
              tint = SLauncherColors.TextPrimary,
            )
          }
        }

        Text(
          text = stringResource(R.string.drawer_hint_long_press),
          style = MaterialTheme.typography.labelSmall,
          color = SLauncherColors.TextMuted,
          modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp, end = 38.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize().testTag("app_drawer_list"),
          ) {
            itemsIndexed(
              items = allApps,
              key = { _, app -> app.componentKey },
              contentType = { _, _ -> "drawer_app" },
            ) { index, app ->
              val showLetterGutter =
                index == 0 || allApps[index - 1].sectionLetter != app.sectionLetter
              var menuExpanded by remember { mutableStateOf(false) }

              Box {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier =
                    Modifier.fillMaxWidth()
                      .heightIn(min = 54.dp)
                      .clip(RoundedCornerShape(16.dp))
                      .combinedClickable(
                        onClick = { onAppClick(app) },
                        onLongClick = { menuExpanded = true },
                      )
                      .padding(horizontal = 6.dp, vertical = 7.dp)
                      .testTag("drawer_app_row_${app.packageName}"),
                ) {
                  Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.width(26.dp),
                  ) {
                    if (showLetterGutter) {
                      Text(
                        text = app.sectionLetter.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = SLauncherColors.AccentPrimary,
                        textAlign = TextAlign.Center,
                      )
                    }
                  }

                  Spacer(modifier = Modifier.width(6.dp))
                  AppIconImage(app = app, loadIcon = loadIcon, size = 38.dp)
                  Spacer(modifier = Modifier.width(14.dp))

                  Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = SLauncherColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                  )
                }

                DropdownMenu(
                  expanded = menuExpanded,
                  onDismissRequest = { menuExpanded = false },
                  modifier = Modifier.background(SLauncherColors.DarkGlassElevated),
                ) {
                  DropdownMenuItem(
                    text = {
                      Text(
                        text = stringResource(R.string.action_app_info),
                        color = SLauncherColors.TextPrimary,
                      )
                    },
                    leadingIcon = {
                      Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = SLauncherColors.TextSecondary,
                      )
                    },
                    onClick = {
                      menuExpanded = false
                      onOpenAppInfo(app)
                    },
                  )
                }
              }
            }
          }

          val bubbleChar = activeLetterBubble
          if (bubbleChar != null) {
            Box(
              contentAlignment = Alignment.Center,
              modifier =
                Modifier.align(Alignment.CenterEnd)
                  .padding(end = 52.dp)
                  .size(64.dp)
                  .clip(RoundedCornerShape(20.dp))
                  .background(SLauncherColors.DarkGlassElevated)
                  .border(1.dp, SLauncherColors.AccentPrimary, RoundedCornerShape(20.dp)),
            ) {
              Text(
                text = bubbleChar.toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = SLauncherColors.AccentPrimary,
              )
            }
          }

          AlphabetIndexRail(
            availableLetters = alphabetIndexMap.keys,
            activeLetter = activeLetterBubble,
            onLetterSelected = { letter -> jumpToLetter(letter) },
            onScrubEnded = { activeLetterBubble = null },
            modifier =
              Modifier.align(Alignment.CenterEnd)
                .padding(vertical = 8.dp)
                .testTag("alphabet_index_rail"),
          )
        }
      }
    }
  }
}

@Composable
private fun AlphabetIndexRail(
  availableLetters: Set<Char>,
  activeLetter: Char?,
  onLetterSelected: (Char) -> Unit,
  onScrubEnded: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var railHeightPx by remember { mutableIntStateOf(1) }

  fun letterForYOffset(yOffset: Float): Char {
    val clamped = yOffset.coerceIn(0f, (railHeightPx - 1).toFloat().coerceAtLeast(1f))
    val ratio = clamped / railHeightPx.coerceAtLeast(1)
    val index = (ratio * ALPHABET_RAIL_CHARS.size).toInt().coerceIn(0, ALPHABET_RAIL_CHARS.lastIndex)
    return ALPHABET_RAIL_CHARS[index]
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceEvenly,
    modifier =
      modifier
        .width(28.dp)
        .fillMaxHeight()
        .clip(RoundedCornerShape(14.dp))
        .background(SLauncherColors.DarkGlassPill)
        .border(1.dp, SLauncherColors.GlassBorder, RoundedCornerShape(14.dp))
        .padding(vertical = 6.dp)
        .onSizeChanged { railHeightPx = it.height }
        .pointerInput(availableLetters) {
          detectTapGestures(
            onPress = { offset ->
              onLetterSelected(letterForYOffset(offset.y))
              tryAwaitRelease()
              onScrubEnded()
            }
          )
        }
        .pointerInput(availableLetters) {
          detectVerticalDragGestures(
            onDragStart = { offset -> onLetterSelected(letterForYOffset(offset.y)) },
            onVerticalDrag = { change, _ ->
              change.consume()
              onLetterSelected(letterForYOffset(change.position.y))
            },
            onDragEnd = { onScrubEnded() },
            onDragCancel = { onScrubEnded() },
          )
        },
  ) {
    for (letter in ALPHABET_RAIL_CHARS) {
      val hasApps = availableLetters.contains(letter)
      val isActive = activeLetter == letter
      Text(
        text = letter.toString(),
        fontSize = 10.sp,
        lineHeight = 11.sp,
        fontWeight = if (isActive || hasApps) FontWeight.Bold else FontWeight.Normal,
        color =
          when {
            isActive -> SLauncherColors.AccentPrimary
            hasApps -> SLauncherColors.TextPrimary
            else -> SLauncherColors.TextMuted.copy(alpha = 0.4f)
          },
      )
    }
  }
}

private fun findNearestSectionIndex(letter: Char, alphabetIndexMap: Map<Char, Int>): Int? {
  if (alphabetIndexMap.isEmpty()) return null
  val targetPos = ALPHABET_RAIL_CHARS.indexOf(letter)
  for (offset in 0 until ALPHABET_RAIL_CHARS.size) {
    val forward = ALPHABET_RAIL_CHARS.getOrNull(targetPos + offset)
    if (forward != null && alphabetIndexMap.containsKey(forward)) {
      return alphabetIndexMap[forward]
    }
    val backward = ALPHABET_RAIL_CHARS.getOrNull(targetPos - offset)
    if (backward != null && alphabetIndexMap.containsKey(backward)) {
      return alphabetIndexMap[backward]
    }
  }
  return alphabetIndexMap.values.firstOrNull()
}
