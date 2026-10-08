@file:OptIn(ExperimentalMaterial3Api::class)

package com.meetnotes.app.ui.record

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetnotes.app.audio.RecStatus
import com.meetnotes.app.audio.RecordingService
import com.meetnotes.app.audio.RecordingStateHolder
import com.meetnotes.app.ui.components.EmptyState
import com.meetnotes.app.ui.components.RecordRed
import com.meetnotes.app.ui.components.Waveform
import com.meetnotes.app.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class RecordViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val holder: RecordingStateHolder,
) : ViewModel() {
    val state = holder.state
    val finished = holder.finished

    fun start() { holder.clearError(); RecordingService.send(context, RecordingService.ACTION_START) }
    fun pause() = RecordingService.send(context, RecordingService.ACTION_PAUSE)
    fun resume() = RecordingService.send(context, RecordingService.ACTION_RESUME)
    fun stop() = RecordingService.send(context, RecordingService.ACTION_STOP)
    fun discard() = RecordingService.send(context, RecordingService.ACTION_CANCEL)
    fun clearError() = holder.clearError()
}

@Composable
fun RecordScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    vm: RecordViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var micGranted by remember { mutableStateOf(context.hasMicPermission()) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val permissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        micGranted = result[Manifest.permission.RECORD_AUDIO] == true || context.hasMicPermission()
        if (micGranted) vm.start() else permissionDenied = true
    }
    val startRecording = {
        if (context.hasMicPermission()) vm.start() else launcher.launch(permissions)
    }

    LaunchedEffect(Unit) { vm.finished.collect { onSaved(it) } }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.isActive) "Recording" else "New recording") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back (recording continues in background)")
                    }
                },
            )
        },
    ) { padding ->
        if (permissionDenied && !micGranted) {
            EmptyState(
                icon = Icons.Default.MicOff,
                title = "Microphone access needed",
                message = "Mokwa Meeting Note needs the microphone to record meetings. Audio stays on your device unless you choose a cloud transcription engine.",
                actionLabel = "Try again",
                onAction = { permissionDenied = false; launcher.launch(permissions) },
                secondaryLabel = "Open app settings",
                onSecondary = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                },
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            StatusLine(state.status)
            Spacer(Modifier.height(12.dp))
            Text(
                Formatters.duration(state.elapsedMs),
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            state.fileName?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(32.dp))
            Waveform(
                levels = state.levels,
                active = state.status == RecStatus.RECORDING,
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp).height(96.dp),
            )
            Spacer(Modifier.height(48.dp))

            AnimatedContent(targetState = state.status, label = "controls") { status ->
                when (status) {
                    RecStatus.IDLE -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(
                            onClick = startRecording,
                            modifier = Modifier.size(96.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.RecordRed),
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Start recording", tint = Color.White, modifier = Modifier.size(44.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Place the phone in the middle of the table for best results.\nRecording continues if you leave this screen or lock the phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        OutlinedIconButton(onClick = { confirmDiscard = true }, modifier = Modifier.size(60.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Discard recording")
                        }
                        FilledIconButton(
                            onClick = vm::stop,
                            modifier = Modifier.size(88.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.RecordRed),
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop and save", tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                        FilledTonalIconButton(
                            onClick = { if (status == RecStatus.RECORDING) vm.pause() else vm.resume() },
                            modifier = Modifier.size(60.dp),
                        ) {
                            if (status == RecStatus.RECORDING) Icon(Icons.Default.Pause, contentDescription = "Pause")
                            else Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                        }
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard recording?") },
            text = { Text("The audio recorded so far will be deleted.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; vm.discard() }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep recording") } },
        )
    }
}

@Composable
private fun StatusLine(status: RecStatus) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (status != RecStatus.IDLE) {
            Icon(
                Icons.Default.FiberManualRecord, contentDescription = null,
                tint = if (status == RecStatus.RECORDING) Color.RecordRed else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(14.dp).alpha(if (status == RecStatus.RECORDING) alpha else 1f),
            )
            Spacer(Modifier.size(6.dp))
        }
        Text(
            when (status) {
                RecStatus.IDLE -> "Ready to record"
                RecStatus.RECORDING -> "Recording"
                RecStatus.PAUSED -> "Paused"
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private fun Context.hasMicPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
