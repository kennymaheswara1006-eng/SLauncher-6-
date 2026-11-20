package com.example.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import com.example.R
import com.example.data.LyricLine
import com.example.data.LyricsState
import com.example.data.MusicPlaybackInfo
import com.example.data.MusicUiState
import com.example.ui.theme.SLauncherColors
import kotlinx.coroutines.delay

/**
 * Spotify-inspired Glassmorphism Music Player card replacing the Pinned Apps section.
 * Works universally with Spotify, YouTube Music, Apple Music, SoundCloud, local players, etc.
 * Shows "Tidak Ada Musik" / "No Music Playing" with Previous, centered Play, and Next buttons
 * when idle, and supports live synced / plain lyrics when a track is playing.
 */
@Composable
fun MusicPlayerCard(
  musicState: MusicUiState,
  onPlayPause: () -> Unit,
  onSkipPrevious: () -> Unit,
  onSkipNext: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onOpenPlayerApp: (String) -> Unit,
  onToggleInlineLyrics: () -> Unit,
  onOpenFullLyrics: () -> Unit,
  onRequestNotificationAccess: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val track = musicState.activeTrack
  val cardShape = RoundedCornerShape(28.dp)

  var livePositionMs by remember(track?.trackCacheKey) {
    mutableLongStateOf(track?.positionMs ?: 0L)
  }

  // Lightweight position interpolation ticker only when a track is actively playing
  LaunchedEffect(
    track?.trackCacheKey,
    track?.isPlaying,
    track?.positionMs,
    track?.positionUpdatedAtElapsedMs,
  ) {
    if (track == null) {
      livePositionMs = 0L
      return@LaunchedEffect
    }
    fun computeCurrentPosition(): Long {
      if (!track.isPlaying) return track.positionMs
      val elapsed = (SystemClock.elapsedRealtime() - track.positionUpdatedAtElapsedMs).coerceAtLeast(0L)
      val estimated = track.positionMs + elapsed
      return if (track.durationMs > 0L) estimated.coerceAtMost(track.durationMs) else estimated
    }
    livePositionMs = computeCurrentPosition()
    while (track.isPlaying) {
      delay(450L)
      livePositionMs = computeCurrentPosition()
    }
  }

  val cardBackground =
    if (track != null) {
      Brush.verticalGradient(
        colors = listOf(Color(0xD8202B42), Color(0xCC111725))
      )
    } else {
      SLauncherColors.GlassFloatingCardGradient
    }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(cardShape)
        .background(cardBackground)
        .border(1.dp, SLauncherColors.GlassBorderGradient, cardShape)
        .padding(horizontal = 16.dp, vertical = 14.dp)
        .testTag("music_player_card"),
  ) {
    // Header Row: Player source pill + Lyrics action toggle
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
          Modifier.clip(RoundedCornerShape(50))
            .background(SLauncherColors.DarkGlassPill)
            .border(0.8.dp, SLauncherColors.GlassBorder, RoundedCornerShape(50))
            .clickable(enabled = track != null) {
              if (track != null) onOpenPlayerApp(track.packageName)
            }
            .padding(horizontal = 10.dp, vertical = 5.dp),
      ) {
        Icon(
          imageVector = if (track?.isPlaying == true) Icons.Rounded.GraphicEq else Icons.Rounded.MusicNote,
          contentDescription = null,
          tint = SLauncherColors.AccentPrimary,
          modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = (track?.appName ?: stringResource(R.string.music_card_label)).uppercase(),
          style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
          color = SLauncherColors.AccentPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (track != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          val lyricsActive = musicState.isLyricsExpandedInline
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.clip(RoundedCornerShape(50))
                .background(
                  if (lyricsActive) SLauncherColors.AccentContainer else SLauncherColors.DarkGlassPill
                )
                .border(
                  0.8.dp,
                  if (lyricsActive) SLauncherColors.AccentPrimary.copy(alpha = 0.6f)
                  else SLauncherColors.GlassBorder,
                  RoundedCornerShape(50),
                )
                .clickable { onToggleInlineLyrics() }
                .padding(horizontal = 11.dp, vertical = 5.dp)
                .testTag("music_lyrics_toggle_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Lyrics,
              contentDescription = null,
              tint = if (lyricsActive) SLauncherColors.AccentPrimary else SLauncherColors.TextSecondary,
              modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = stringResource(R.string.music_lyrics_button),
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = if (lyricsActive) SLauncherColors.AccentPrimary else SLauncherColors.TextPrimary,
            )
          }

          Spacer(modifier = Modifier.width(4.dp))

          IconButton(
            onClick = onOpenFullLyrics,
            modifier = Modifier.size(32.dp).testTag("music_expand_lyrics_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.OpenInFull,
              contentDescription = stringResource(R.string.music_lyrics_expand),
              tint = SLauncherColors.TextSecondary,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    if (track == null) {
      // Idle / Empty State: Clear "Tidak Ada Musik" + Transport controls below
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.size(56.dp)
              .clip(CircleShape)
              .background(SLauncherColors.AccentContainer)
              .border(1.dp, SLauncherColors.AccentPrimary.copy(alpha = 0.35f), CircleShape),
        ) {
          Icon(
            imageVector = Icons.Rounded.Album,
            contentDescription = null,
            tint = SLauncherColors.AccentPrimary,
            modifier = Modifier.size(30.dp),
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = stringResource(R.string.music_no_music),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = SLauncherColors.TextPrimary,
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag("music_no_music_text"),
        )

        Spacer(modifier = Modifier.height(3.dp))

        if (musicState.requiresNotificationAccess) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.padding(top = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(SLauncherColors.DarkGlassPill)
                .border(0.8.dp, SLauncherColors.GlassBorder, RoundedCornerShape(50))
                .clickable { onRequestNotificationAccess() }
                .padding(horizontal = 12.dp, vertical = 5.dp)
                .testTag("music_grant_access_chip"),
          ) {
            Icon(
              imageVector = Icons.Rounded.NotificationsActive,
              contentDescription = null,
              tint = SLauncherColors.AccentPrimary,
              modifier = Modifier.size(13.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.music_grant_access_hint),
              style = MaterialTheme.typography.labelSmall,
              color = SLauncherColors.TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        } else {
          Text(
            text = stringResource(R.string.music_no_music_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = SLauncherColors.TextMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    } else {
      // Active Playing / Paused Track UI
      ActiveTrackHeader(
        track = track,
        onOpenPlayerApp = { onOpenPlayerApp(track.packageName) },
      )

      if (track.durationMs > 0L) {
        Spacer(modifier = Modifier.height(10.dp))
        val progress = (livePositionMs.toFloat() / track.durationMs.toFloat()).coerceIn(0f, 1f)
        Slider(
          value = progress,
          onValueChange = { frac ->
            val targetMs = (frac * track.durationMs).toLong()
            livePositionMs = targetMs
            onSeekTo(targetMs)
          },
          colors =
            SliderDefaults.colors(
              thumbColor = SLauncherColors.TextPrimary,
              activeTrackColor = SLauncherColors.AccentPrimary,
              inactiveTrackColor = Color.White.copy(alpha = 0.16f),
            ),
          modifier = Modifier.fillMaxWidth().height(20.dp).testTag("music_seek_slider"),
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
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Spotify-style Transport Controls Row: Previous | Centered Play/Pause | Next
    MusicTransportControlsRow(
      isPlaying = track?.isPlaying == true,
      onSkipPrevious = onSkipPrevious,
      onPlayPause = onPlayPause,
      onSkipNext = onSkipNext,
    )

    // Collapsible Inline Lyrics Drawer inside the Card
    AnimatedVisibility(
      visible = track != null && musicState.isLyricsExpandedInline,
      enter = expandVertically() + fadeIn(),
      exit = shrinkVertically() + fadeOut(),
    ) {
      Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(1.dp)
              .background(SLauncherColors.GlassDivider),
        )
        Spacer(modifier = Modifier.height(10.dp))
        InlineLyricsBox(
          lyricsState = musicState.lyricsState,
          currentPositionMs = livePositionMs,
          onSeekTo = onSeekTo,
          onOpenFullLyrics = onOpenFullLyrics,
        )
      }
    }
  }
}

@Composable
private fun ActiveTrackHeader(
  track: MusicPlaybackInfo,
  onOpenPlayerApp: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .clickable(onClick = onOpenPlayerApp)
        .padding(vertical = 2.dp),
  ) {
    val artShape = RoundedCornerShape(16.dp)
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.size(62.dp)
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
          modifier = Modifier.size(30.dp),
        )
      }
    }

    Spacer(modifier = Modifier.width(14.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = track.title,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
        color = SLauncherColors.TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("music_track_title"),
      )
      if (track.artist.isNotBlank()) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = track.artist,
          style = MaterialTheme.typography.bodyMedium,
          color = SLauncherColors.TextSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag("music_track_artist"),
        )
      }
      if (track.album.isNotBlank()) {
        Text(
          text = track.album,
          style = MaterialTheme.typography.labelSmall,
          color = SLauncherColors.TextMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
fun MusicTransportControlsRow(
  isPlaying: Boolean,
  onSkipPrevious: () -> Unit,
  onPlayPause: () -> Unit,
  onSkipNext: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
    modifier = modifier.fillMaxWidth(),
  ) {
    // Previous button
    IconButton(
      onClick = onSkipPrevious,
      modifier =
        Modifier.minimumInteractiveComponentSize()
          .size(48.dp)
          .clip(CircleShape)
          .background(SLauncherColors.DarkGlassPill)
          .border(1.dp, SLauncherColors.GlassBorder, CircleShape)
          .testTag("music_prev_button"),
    ) {
      Icon(
        imageVector = Icons.Rounded.SkipPrevious,
        contentDescription = stringResource(R.string.music_cd_previous),
        tint = SLauncherColors.TextPrimary,
        modifier = Modifier.size(26.dp),
      )
    }

    // Centered signature Play / Pause button
    IconButton(
      onClick = onPlayPause,
      modifier =
        Modifier.minimumInteractiveComponentSize()
          .size(56.dp)
          .clip(CircleShape)
          .background(SLauncherColors.TextPrimary)
          .testTag("music_play_pause_button"),
    ) {
      Icon(
        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
        contentDescription =
          if (isPlaying) stringResource(R.string.music_cd_pause)
          else stringResource(R.string.music_cd_play),
        tint = Color(0xFF0E131E),
        modifier = Modifier.size(30.dp),
      )
    }

    // Next button
    IconButton(
      onClick = onSkipNext,
      modifier =
        Modifier.minimumInteractiveComponentSize()
          .size(48.dp)
          .clip(CircleShape)
          .background(SLauncherColors.DarkGlassPill)
          .border(1.dp, SLauncherColors.GlassBorder, CircleShape)
          .testTag("music_next_button"),
    ) {
      Icon(
        imageVector = Icons.Rounded.SkipNext,
        contentDescription = stringResource(R.string.music_cd_next),
        tint = SLauncherColors.TextPrimary,
        modifier = Modifier.size(26.dp),
      )
    }
  }
}

@Composable
private fun InlineLyricsBox(
  lyricsState: LyricsState,
  currentPositionMs: Long,
  onSeekTo: (Long) -> Unit,
  onOpenFullLyrics: () -> Unit,
) {
  val containerShape = RoundedCornerShape(18.dp)
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = 96.dp, max = 185.dp)
        .clip(containerShape)
        .background(Color.Black.copy(alpha = 0.24f))
        .border(0.8.dp, SLauncherColors.GlassBorder, containerShape)
        .padding(horizontal = 14.dp, vertical = 12.dp)
        .testTag("music_inline_lyrics_box"),
  ) {
    when (lyricsState) {
      LyricsState.Idle,
      LyricsState.Loading -> {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
          modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        ) {
          CircularProgressIndicator(
            color = SLauncherColors.AccentPrimary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = stringResource(R.string.music_lyrics_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = SLauncherColors.TextSecondary,
          )
        }
      }

      LyricsState.NotFound -> {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        ) {
          Text(
            text = stringResource(R.string.music_lyrics_not_found),
            style = MaterialTheme.typography.bodyMedium,
            color = SLauncherColors.TextMuted,
            textAlign = TextAlign.Center,
          )
        }
      }

      is LyricsState.Synced -> {
        val listState = rememberLazyListState()
        val activeIndex = findActiveLyricIndex(lyricsState.lines, currentPositionMs)

        LaunchedEffect(activeIndex) {
          if (activeIndex >= 0) {
            listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
          }
        }

        LazyColumn(
          state = listState,
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          itemsIndexed(lyricsState.lines) { index, line ->
            val isActive = index == activeIndex
            val textColor by
              animateColorAsState(
                targetValue =
                  when {
                    isActive -> SLauncherColors.TextPrimary
                    index < activeIndex -> SLauncherColors.TextMuted.copy(alpha = 0.7f)
                    else -> SLauncherColors.TextSecondary.copy(alpha = 0.8f)
                  },
                label = "inline_lyric_color",
              )
            Text(
              text = line.text,
              style =
                MaterialTheme.typography.bodyLarge.copy(
                  fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                  fontSize = if (isActive) 15.5.sp else 14.sp,
                ),
              color = textColor,
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .clickable { onSeekTo(line.timeMs) }
                  .padding(vertical = 2.dp),
            )
          }
        }
      }

      is LyricsState.Plain -> {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth().clickable { onOpenFullLyrics() },
        ) {
          itemsIndexed(lyricsState.lines) { _, line ->
            Text(
              text = line,
              style = MaterialTheme.typography.bodyMedium,
              color = SLauncherColors.TextPrimary.copy(alpha = 0.9f),
            )
          }
        }
      }
    }
  }
}

fun findActiveLyricIndex(lines: List<LyricLine>, positionMs: Long): Int {
  if (lines.isEmpty()) return -1
  var match = -1
  for (i in lines.indices) {
    if (lines[i].timeMs <= positionMs + 200L) {
      match = i
    } else {
      break
    }
  }
  return match
}

fun formatTrackMillis(millis: Long): String {
  val totalSec = (millis / 1000L).coerceAtLeast(0L)
  val min = totalSec / 60L
  val sec = totalSec % 60L
  return "%d:%02d".format(min, sec)
}
