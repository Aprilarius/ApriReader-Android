package com.aprireader.app.data.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.aprireader.app.data.library.LibraryRepository
import com.aprireader.app.data.saf.DocumentCache
import com.aprireader.app.domain.Book
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AudioPlayerState(
    val currentBook: Book? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val sleepTimerMinutes: Int? = null,
    val sleepTimerRemainingSec: Int? = null,
    val errorMessage: String? = null,
) {
    val progress: Float
        get() = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Контроллер воспроизведения аудиокниг на базе Android Media3 ExoPlayer.
 *
 * Предоставляет надёжное воспроизведение всех поддерживаемых форматов (M4B, MP3, M4A, AAC, FLAC, OGG, OPUS),
 * автоматическое управление аудиофокусом, бесшовное изменение скорости, точное запоминание таймкода
 * и синхронизацию с фоновым сервисом.
 */
class AudiobookPlayerController(
    private val context: Context,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AudioPlayerState())
    val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var progressTrackingJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressTracking()
            } else {
                stopProgressTracking()
                saveCurrentProgress()
            }
            AudioPlayerService.startOrUpdate(context, _state.value)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _state.update { it.copy(isBuffering = true) }
                }
                Player.STATE_READY -> {
                    val dur = exoPlayer?.duration?.takeIf { it > 0 } ?: _state.value.durationMs
                    _state.update {
                        it.copy(
                            isBuffering = false,
                            durationMs = dur,
                            errorMessage = null,
                        )
                    }
                }
                Player.STATE_ENDED -> {
                    val dur = _state.value.durationMs
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            isBuffering = false,
                            currentPositionMs = dur,
                        )
                    }
                    stopProgressTracking()
                    saveCurrentProgress()
                    AudioPlayerService.stop(context)
                }
                Player.STATE_IDLE -> {
                    _state.update { it.copy(isBuffering = false) }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = error.message ?: "Ошибка воспроизведения",
                )
            }
            stopProgressTracking()
            AudioPlayerService.stop(context)
        }
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: run {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .setUsage(C.USAGE_MEDIA)
                .build()

            val player = ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
                .setHandleAudioBecomingNoisy(true)
                .build()

            player.addListener(playerListener)
            exoPlayer = player
            player
        }
    }

    /**
     * Загружает аудиокнигу и начинает воспроизведение.
     */
    fun loadAndPlay(book: Book, startPositionMs: Long? = null) {
        val current = _state.value.currentBook
        val resumePos = startPositionMs ?: book.locatorOffset.toLong().coerceAtLeast(0L)
        val initialDuration = (book.totalChars * 1000L).coerceAtLeast(0L)

        scope.launch(Dispatchers.Main) {
            val player = getOrCreatePlayer()

            // Если эта же книга уже загружена в плеер
            if (current?.id == book.id && player.currentMediaItem != null) {
                if (!player.isPlaying) {
                    player.play()
                }
                return@launch
            }

            _state.update {
                it.copy(
                    currentBook = book,
                    isBuffering = true,
                    errorMessage = null,
                    currentPositionMs = resumePos,
                    durationMs = initialDuration,
                )
            }

            // Разрешение URI источника (с поддержкой локального кэша DocumentCache при потере SAF прав)
            val uri = book.documentUri
            val mediaUri = resolveMediaUri(book, uri)

            val mediaItem = MediaItem.fromUri(mediaUri)
            player.setMediaItem(mediaItem)
            player.playbackParameters = PlaybackParameters(_state.value.speed)
            player.prepare()

            if (resumePos > 0) {
                player.seekTo(resumePos)
            }

            player.play()
        }
    }

    private fun resolveMediaUri(book: Book, uri: Uri): Uri {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            if (file.exists()) return uri
        }

        // Проверяем доступность через SAF или материализованный файл
        val cached = DocumentCache.materialize(context, uri, book.fileName)
        if (cached != null && cached.exists()) {
            return Uri.fromFile(cached)
        }

        return uri
    }

    /**
     * Возобновляет воспроизведение.
     */
    fun play() {
        scope.launch(Dispatchers.Main) {
            val player = exoPlayer
            val current = _state.value.currentBook
            if (player != null && player.currentMediaItem != null) {
                player.play()
            } else if (current != null) {
                loadAndPlay(current)
            }
        }
    }

    /**
     * Ставит на паузу.
     */
    fun pause() {
        scope.launch(Dispatchers.Main) {
            exoPlayer?.pause()
        }
    }

    /**
     * Переключает play/pause.
     */
    fun togglePlayPause() {
        scope.launch(Dispatchers.Main) {
            val player = exoPlayer
            if (player != null && player.isPlaying) {
                player.pause()
            } else {
                play()
            }
        }
    }

    /**
     * Перематывает на заданную позицию (в миллисекундах).
     */
    fun seekTo(positionMs: Long) {
        scope.launch(Dispatchers.Main) {
            val player = exoPlayer ?: return@launch
            val duration = if (player.duration > 0) player.duration else _state.value.durationMs
            val target = positionMs.coerceIn(0L, if (duration > 0L) duration else Long.MAX_VALUE)
            player.seekTo(target)
            _state.update { it.copy(currentPositionMs = target) }
            saveCurrentProgress()
            AudioPlayerService.startOrUpdate(context, _state.value)
        }
    }

    /**
     * Перематывает относительно текущей позиции на deltaMs (в миллисекундах).
     */
    fun seekBy(deltaMs: Long) {
        scope.launch(Dispatchers.Main) {
            val player = exoPlayer
            val current = player?.currentPosition ?: _state.value.currentPositionMs
            seekTo(current + deltaMs)
        }
    }

    /**
     * Устанавливает скорость воспроизведения (0.5x - 3.0x).
     */
    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 3.0f)
        _state.update { it.copy(speed = clamped) }
        scope.launch(Dispatchers.Main) {
            exoPlayer?.playbackParameters = PlaybackParameters(clamped)
        }
    }

    /**
     * Устанавливает таймер сна (в минутах). null для отключения.
     */
    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _state.update { it.copy(sleepTimerMinutes = null, sleepTimerRemainingSec = null) }
            return
        }

        val totalSec = minutes * 60
        _state.update { it.copy(sleepTimerMinutes = minutes, sleepTimerRemainingSec = totalSec) }

        sleepTimerJob = scope.launch {
            var remaining = totalSec
            while (isActive && remaining > 0) {
                delay(1000L)
                remaining--
                _state.update { it.copy(sleepTimerRemainingSec = remaining) }
            }
            if (isActive) {
                pause()
                _state.update { it.copy(sleepTimerMinutes = null, sleepTimerRemainingSec = null) }
            }
        }
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressTrackingJob = scope.launch(Dispatchers.Main) {
            var saveCounter = 0
            while (isActive) {
                val player = exoPlayer
                if (player != null && player.isPlaying) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.takeIf { it > 0 } ?: _state.value.durationMs
                    _state.update { it.copy(currentPositionMs = pos, durationMs = dur) }
                    saveCounter++
                    if (saveCounter >= 4) { // Каждые 2 секунды
                        saveCounter = 0
                        saveCurrentProgress()
                    }
                }
                delay(500L)
            }
        }
    }

    private fun stopProgressTracking() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }

    private fun saveCurrentProgress() {
        val book = _state.value.currentBook ?: return
        val pos = _state.value.currentPositionMs
        val dur = _state.value.durationMs
        val fraction = if (dur > 0L) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
        scope.launch(Dispatchers.IO) {
            library.updateProgress(
                bookId = book.id,
                unit = 0,
                offset = pos.toInt(),
                progress = fraction,
                totalUnits = 1,
            )
        }
    }

    fun releasePlayer() {
        stopProgressTracking()
        scope.launch(Dispatchers.Main) {
            val player = exoPlayer
            exoPlayer = null
            player?.removeListener(playerListener)
            player?.stop()
            player?.release()
        }
    }

    /**
     * Сохраняет позицию и полностью освобождает плеер одним действием —
     * для сценария «задачу смахнули из недавних» ([AudioPlayerService.onTaskRemoved]).
     *
     * Раньше это делалось как `pause()`, затем `releasePlayer()`. `pause()`
     * идёт через обычный `player.pause()`, который сихронно бьёт по
     * слушателю `onIsPlayingChanged`, а тот сам вызывает
     * `AudioPlayerService.startOrUpdate` — то есть `startService()` на тот же
     * сервис, который прямо сейчас в `onTaskRemoved` вызывает `stopSelf()`.
     * `startService()` после `stopSelf()`, но до его фактической обработки,
     * по документированному поведению Android отменяет остановку — сервис
     * рисковал не остановиться после закрытия задачи. Здесь слушатель
     * снимается ДО `pause()`, поэтому событие вообще не долетает и сервис не
     * перезапускает сам себя.
     */
    fun pauseAndRelease() {
        stopProgressTracking()
        scope.launch(Dispatchers.Main) {
            val player = exoPlayer
            player?.removeListener(playerListener)
            player?.pause()
            saveCurrentProgress()
            exoPlayer = null
            player?.stop()
            player?.release()
        }
    }

    fun destroy() {
        releasePlayer()
        sleepTimerJob?.cancel()
    }
}
