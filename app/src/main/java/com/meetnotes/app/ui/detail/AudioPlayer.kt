package com.meetnotes.app.ui.detail

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.meetnotes.app.util.Formatters
import kotlinx.coroutines.delay
import java.io.File

private val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f)

@Composable
fun AudioPlayerCard(audioPath: String, durationMs: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val file = remember(audioPath) { File(audioPath) }

    if (!file.exists()) {
        Card(modifier.fillMaxWidth()) {
            Text("Audio file not found", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        }
        return
    }

    val player = remember(audioPath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(durationMs.coerceAtLeast(1L)) }
    var dragging by remember { mutableStateOf(false) }
    var speedIndex by remember { mutableStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player.duration > 0) duration = player.duration
                if (playbackState == Player.STATE_ENDED) {
                    player.pause()
                    player.seekTo(0)
                    position = 0
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (!dragging) position = player.currentPosition
            delay(250)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause playback" else "Play recording",
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Slider(
                    value = position.toFloat().coerceIn(0f, duration.toFloat()),
                    onValueChange = { dragging = true; position = it.toLong() },
                    onValueChangeFinished = { player.seekTo(position); dragging = false },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.semantics { contentDescription = "Playback position" },
                )
                Row {
                    Text(Formatters.duration(position), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.weight(1f))
                    Text(Formatters.duration(duration), style = MaterialTheme.typography.labelSmall)
                }
            }
            TextButton(onClick = {
                speedIndex = (speedIndex + 1) % SPEEDS.size
                player.setPlaybackSpeed(SPEEDS[speedIndex])
            }) {
                val s = SPEEDS[speedIndex]
                Text(if (s % 1f == 0f) "${s.toInt()}×" else "$s×")
            }
        }
    }
}
