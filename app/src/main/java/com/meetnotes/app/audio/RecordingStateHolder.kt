package com.meetnotes.app.audio

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

enum class RecStatus { IDLE, RECORDING, PAUSED }

data class RecordingState(
    val status: RecStatus = RecStatus.IDLE,
    val elapsedMs: Long = 0,
    /** Recent normalised input levels (0..1) for the live waveform. */
    val levels: List<Float> = emptyList(),
    val fileName: String? = null,
    val error: String? = null,
) {
    val isActive: Boolean get() = status != RecStatus.IDLE
}

/** Shared between [RecordingService] (writer) and the UI (reader). */
@Singleton
class RecordingStateHolder @Inject constructor() {
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _finished = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    /** Emits the id of each meeting saved when a recording stops. */
    val finished: SharedFlow<Long> = _finished.asSharedFlow()

    fun update(transform: (RecordingState) -> RecordingState) = _state.update(transform)
    fun emitFinished(meetingId: Long) { _finished.tryEmit(meetingId) }
    fun clearError() = _state.update { it.copy(error = null) }
}
