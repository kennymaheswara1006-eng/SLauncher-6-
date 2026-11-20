package com.example.ui.components

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.LyricsState
import com.example.data.MusicUiState
import com.example.ui.theme.SLauncherColors
import kotlinx.coroutines.delay

/**
 * Full-screen Spotify-style Dark Glass Lyrics Sheet with live synced scrolling,
 * tap-to-seek on synced lyric lines, and full transport controls.
 */
@Composable
fun MusicLyricsDialog(
  musicState: MusicUiState,
  onPlayPause: () -> Unit,
  onSkipPrevious: () -> Unit,
  onSkipNext: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onDismiss: () -> Unit,
) {
  val track = musicState.activeTrack ?: return

  var livePositionMs by remember(track.trackCacheKey) { mutableLongStateOf(track.positionMs) }

  LaunchedEffect(
    track.trackCacheKey,
    track.isPlaying,
    track.positionMs,
    track.positionUpdatedAtElapsedMs,
  ) {
    fun computeCurrentPosition(): Long {
      if (!track.isPlaying) return track.positionMs
      val elapsed = (SystemClock.elapsedRealtime() - track.positionUpdatedAtElapsedMs).coerceAtLeast(0L)
      val estimated = track.positionMs + elapsed
      return if (track.durationMs > 0L) estimated.coerceAtMost(track.durationMs) else estimated
    }
    livePositionMs = computeCurrentPosition()
    while (track.isPlaying) {
      delay(350L)
      livePositionMs = computeCurrentPosition()
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
          .background(
            Brush.verticalGradient(
              colors = listOf(Color(0xF51A2336), Color(0xF80D111A))
            )
          )
          .statusBarsPadding()
          .navigationBarsPadding(),
    ) {
      Column(
        modifier =
          Modifier.fillMaxSize()
            .widthIn(max = 600.dp)
            .padding(horizontal = 22.dp, vertical = 14.dp),
      ) {
        // Header: Artwork + Song Info + Close
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth(),
        ) {
          val artShape = RoundedCornerShape(14.dp)
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(48.dp)
                .clip(artShape)
                .background(SLauncherColors.AccentContainer)
                .border(1.dp, SLauncherColors.GlassBorderStrong, artShape),
          ) {
            if (track.artwork != null) {
              Image(
                bitmap = track.artwork,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
              )
            } else {
              Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = track.title,
                tint = SLauncherColors.AccentPrimary,
                modifier = Modifier.size(24.dp),
              )
            }
          }

          Spacer(modifier = Modifier.width(12.dp))

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = track.title,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = SLauncherColors.TextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = track.artist.ifBlank { track.appName },
                style = MaterialTheme.typography.bodyMedium,
                color = SLauncherColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (musicState.lyricsState is LyricsState.Synced) {
                Text(
                  text = stringResource(R.string.music_lyrics_synced_badge),
                  style = MaterialTheme.typography.labelSmall,
                  color = SLauncherColors.AccentPrimary,
                  modifier =
                    Modifier.clip(RoundedCornerShape(50))
                      .background(SLauncherColors.AccentContainer)
                      .padding(horizontal = 8.dp, vertical = 2.dp),
                )
              }
            }
          }

          Spacer(modifier = Modifier.width(8.dp))

          IconButton(
            onClick = onDismiss,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(SLauncherColors.DarkGlassPill)
                .border(1.dp, SLauncherColors.GlassBorder, CircleShape)
                .testTag("close_full_lyrics_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(R.string.action_done),
              tint = SLauncherColors.TextPrimary,
            )
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Main Lyrics Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          when (val state = musicState.lyricsState) {
            LyricsState.Idle,
            LyricsState.Loading -> {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
              ) {
                CircularProgressIndicator(
                  color = SLauncherColors.AccentPrimary,
                  strokeWidth = 2.5.dp,
                  modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                  text = stringResource(R.string.music_lyrics_loading),
                  style = MaterialTheme.typography.bodyLarge,
                  color = SLauncherColors.TextSecondary,
                )
              }
            }

            LyricsState.NotFound -> {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(24.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Lyrics,
                  contentDescription = null,
                  tint = SLauncherColors.TextMuted,
                  modifier = Modifier.size(44.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                  text = stringResource(R.string.music_lyrics_not_found),
                  style = MaterialTheme.typography.titleMedium,
                  color = SLauncherColors.TextSecondary,
                  textAlign = TextAlign.Center,
                )
              }
            }

            is LyricsState.Synced -> {
              val listState = rememberLazyListState()
              val activeIndex = findActiveLyricIndex(state.lines, livePositionMs)

              LaunchedEffect(activeIndex) {
                if (activeIndex >= 0) {
                  listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
                }
              }

              LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
              ) {
                itemsIndexed(state.lines) { index, line ->
                  val isActive = index == activeIndex
                  val color by
                    animateColorAsState(
                      targetValue =
                        when {
                          isActive -> SLauncherColors.TextPrimary
                          index < activeIndex -> SLauncherColors.TextMuted.copy(alpha = 0.55f)
                          else -> SLauncherColors.TextSecondary.copy(alpha = 0.72f)
                        },
                      label = "full_lyric_color",
                    )
                  Text(
                    text = line.text,
                    style =
                      MaterialTheme.typography.headlineSmall.copy(
                        fontSize = if (isActive) 24.sp else 20.sp,
                        lineHeight = if (isActive) 31.sp else 27.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                      ),
                    color = color,
                    modifier =
                      Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSeekTo(line.timeMs) }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                  )
                }
              }
            }

            is LyricsState.Plain -> {
              LazyColumn(
                contentPadding = PaddingValues(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
              ) {
                itemsIndexed(state.lines) { _, line ->
                  Text(
                    text = line,
                    style =
                      MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Medium,
                      ),
                    color = SLauncherColors.TextPrimary.copy(alpha = 0.92f),
                  )
                }
              }
            }
          }
        }

        // Bottom Scrubbing + Transport Bar
        if (track.durationMs > 0L) {
          val progress = (livePositionMs.toFloat() / track.durationMs.toFloat()).coerceIn(0f, 1f)
          Slider(
            value = progress,
            onValueChange = { frac ->
              val target = (frac * track.durationMs).toLong()
              livePositionMs = target
              onSeekTo(target)
            },
            colors =
              SliderDefaults.colors(
                thumbColor = SLauncherColors.TextPrimary,
                activeTrackColor = SLauncherColors.AccentPrimary,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
              ),
            modifier = Modifier.fillMaxWidth().height(24.dp),
          )
          Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
          ) {
            Text(
              text = formatTrackMillis(livePositionMs),
              style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
              color = SLauncherColors.TextMuted,
            )
            Text(
              text = formatTrackMillis(track.durationMs),
              style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
              color = SLauncherColors.TextMuted,
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
        }

        MusicTransportControlsRow(
          isPlaying = track.isPlaying,
          onSkipPrevious = onSkipPrevious,
          onPlayPause = onPlayPause,
          onSkipNext = onSkipNext,
          modifier = Modifier.padding(bottom = 8.dp),
        )
      }
    }
  }
}
