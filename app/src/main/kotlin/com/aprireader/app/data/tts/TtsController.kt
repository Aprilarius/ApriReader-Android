package com.aprireader.app.data.tts

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

private const val TAG = "TtsManager"

data class TtsEngineInfo(
    val packageName: String,
    val label: String,
)

data class TtsState(
    val available: Boolean = false,
    val speaking: Boolean = false,
    val paused: Boolean = false,
    val blockIndex: Int = 0,
    val rate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val availableEngines: List<TtsEngineInfo> = emptyList(),
    val selectedEngine: String? = null,
    val languageMissing: Boolean = false,
    val targetLanguage: String? = null,
    val initializing: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * TtsController (TtsManager) — автономный сервис для работы с системным Android TextToSpeech.
 *
 * Предоставляет безопасный API для синтеза речи (play/pause/resume/stop/setRate/setLanguage),
 * управляет жизненным циклом одного экземпляра TextToSpeech, отслеживает готовность onInit(),
 * проверяет доступность языковых пакетов (isLanguageAvailable) и логирует все ошибки.
 */
class TtsController(private val context: Context) {

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    @Volatile
    private var engine: TextToSpeech? = null
    private var isInitializing = false
    private var pendingReady: ((TextToSpeech) -> Unit)? = null

    private var queue: List<String> = emptyList()
    private var onBlockChanged: ((Int) -> Unit)? = null
    private var onFinished: (() -> Unit)? = null
    private var pendingStartIndex: Int? = null

    init {
        queryEngines()
    }

    fun setPreferredEngine(packageName: String?) {
        val targetPackage = packageName?.takeIf { it.isNotBlank() }
        if (targetPackage != _state.value.selectedEngine) {
            val wasSpeaking = _state.value.speaking
            val currentBlock = _state.value.blockIndex
            shutdown()
            _state.update { it.copy(selectedEngine = targetPackage) }
            if (wasSpeaking && queue.isNotEmpty()) {
                ensureEngine { tts ->
                    enqueueFrom(tts, currentBlock)
                }
            }
        }
    }

    fun getInstalledEngines(): List<TtsEngineInfo> {
        queryEngines()
        return _state.value.availableEngines
    }

    private fun queryEngines() {
        runCatching {
            var tempTts: TextToSpeech? = null
            tempTts = TextToSpeech(context.applicationContext) { status ->
                runCatching {
                    if (status == TextToSpeech.SUCCESS) {
                        val list = tempTts?.engines?.map {
                            TtsEngineInfo(packageName = it.name, label = it.label)
                        } ?: emptyList()
                        _state.update { it.copy(availableEngines = list) }
                    } else {
                        Log.w(TAG, "queryEngines: init returned status $status")
                    }
                    tempTts?.shutdown()
                }
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to query TTS engines", e)
        }
    }

    fun openSystemTtsSettings(ctx: Context) {
        val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val settingsIntent = Intent("com.android.settings.TTS_SETTINGS").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val fallback = Intent(Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val launched = runCatching {
            ctx.startActivity(installIntent)
            true
        }.getOrElse {
            runCatching {
                ctx.startActivity(settingsIntent)
                true
            }.getOrElse {
                runCatching {
                    ctx.startActivity(fallback)
                    true
                }.getOrDefault(false)
            }
        }

        if (!launched) {
            Log.e(TAG, "Could not open any TTS settings intent")
        }
    }

    @Synchronized
    private fun ensureEngine(onReady: (TextToSpeech) -> Unit) {
        val existing = engine
        if (existing != null) {
            onReady(existing)
            return
        }
        if (isInitializing) {
            val prev = pendingReady
            pendingReady = { tts ->
                prev?.invoke(tts)
                onReady(tts)
            }
            return
        }
        isInitializing = true
        pendingReady = onReady
        _state.update { it.copy(initializing = true, errorMessage = null) }

        val pkg = _state.value.selectedEngine
        var createdTts: TextToSpeech? = null
        val listenerInit = TextToSpeech.OnInitListener { status ->
            isInitializing = false
            if (status == TextToSpeech.SUCCESS) {
                Log.i(TAG, "TextToSpeech initialized successfully (engine: $pkg)")
                val currentEngine = createdTts ?: engine
                if (currentEngine != null) {
                    setupEngineInstance(currentEngine, pkg)
                    val readyCallback = pendingReady
                    pendingReady = null
                    readyCallback?.invoke(currentEngine)
                } else {
                    Log.e(TAG, "TextToSpeech onInit succeeded but engine reference is null")
                    _state.update {
                        it.copy(
                            available = false,
                            initializing = false,
                            errorMessage = "Ошибка привязки движка TTS",
                        )
                    }
                    pendingReady = null
                }
            } else {
                val msg = "Ошибка инициализации TTS (код $status). Проверьте настройки синтеза речи."
                Log.e(TAG, msg)
                _state.update {
                    it.copy(
                        available = false,
                        initializing = false,
                        errorMessage = msg,
                    )
                }
                pendingReady = null
            }
        }

        createdTts = if (pkg != null) {
            runCatching {
                TextToSpeech(context.applicationContext, listenerInit, pkg)
            }.getOrElse { e ->
                Log.w(TAG, "Failed to create TTS with engine $pkg, falling back to default", e)
                TextToSpeech(context.applicationContext, listenerInit)
            }
        } else {
            TextToSpeech(context.applicationContext, listenerInit)
        }

        // Страховка на случай синхронного вызова onInit внутри конструктора
        if (engine != null && pendingReady != null) {
            val currentEngine = engine!!
            val readyCallback = pendingReady
            pendingReady = null
            readyCallback?.invoke(currentEngine)
        }
    }

    private fun setupEngineInstance(instance: TextToSpeech, pkg: String?) {
        runCatching {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            instance.setAudioAttributes(audioAttributes)
        }.onFailure { e ->
            Log.w(TAG, "Failed to setAudioAttributes on TTS", e)
        }

        instance.setOnUtteranceProgressListener(listener)
        engine = instance
        _state.update {
            it.copy(
                available = true,
                selectedEngine = pkg ?: runCatching { instance.defaultEngine }.getOrNull(),
                errorMessage = null,
            )
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val index = utteranceId?.toIntOrNull() ?: return
            _state.update { it.copy(blockIndex = index, speaking = true, initializing = false) }
            onBlockChanged?.invoke(index)
        }

        override fun onDone(utteranceId: String?) {
            val index = utteranceId?.toIntOrNull() ?: return
            if (index >= queue.lastIndex) {
                _state.update { it.copy(speaking = false) }
                onFinished?.invoke()
            }
        }

        @Deprecated("Required by base class", ReplaceWith(""))
        override fun onError(utteranceId: String?) {
            Log.e(TAG, "TTS Utterance error on utteranceId=$utteranceId")
            _state.update {
                it.copy(
                    speaking = false,
                    initializing = false,
                    errorMessage = "Ошибка воспроизведения фрагмента",
                )
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            val description = when (errorCode) {
                TextToSpeech.ERROR_SYNTHESIS -> "Ошибка синтеза речи (ERROR_SYNTHESIS)"
                TextToSpeech.ERROR_SERVICE -> "Служба TTS недоступна (ERROR_SERVICE)"
                TextToSpeech.ERROR_OUTPUT -> "Ошибка аудиовыхода (ERROR_OUTPUT)"
                TextToSpeech.ERROR_NETWORK -> "Ошибка сети при загрузке голоса (ERROR_NETWORK)"
                TextToSpeech.ERROR_NETWORK_TIMEOUT -> "Таймаут сети (ERROR_NETWORK_TIMEOUT)"
                TextToSpeech.ERROR_INVALID_REQUEST -> "Некорректный запрос озвучки (ERROR_INVALID_REQUEST)"
                TextToSpeech.ERROR_NOT_INSTALLED_YET -> "Голосовой пакет ещё не установлен (ERROR_NOT_INSTALLED_YET)"
                else -> "Ошибка озвучки (код $errorCode)"
            }
            Log.e(TAG, "TTS Utterance error on utteranceId=$utteranceId: $description ($errorCode)")
            _state.update {
                it.copy(
                    speaking = false,
                    initializing = false,
                    errorMessage = description,
                )
            }
        }
    }

    /**
     * Запуск воспроизведения текста.
     * Проверяет готовность движка, валидирует доступность языка через isLanguageAvailable(),
     * применяет настройки скорости и интонации, и начинает чтение с указанного абзаца.
     */
    fun speak(
        blocks: List<String>,
        fromIndex: Int,
        language: Locale?,
        onBlock: (Int) -> Unit,
        onComplete: () -> Unit,
    ) {
        queue = blocks
        onBlockChanged = onBlock
        onFinished = onComplete
        pendingStartIndex = fromIndex

        _state.update { it.copy(speaking = true, blockIndex = fromIndex, errorMessage = null) }

        ensureEngine { tts ->
            val languageReady = applyLanguage(tts, language)
            if (languageReady) {
                tts.setSpeechRate(_state.value.rate)
                tts.setPitch(_state.value.pitch)
                enqueueFrom(tts, pendingStartIndex ?: 0)
            } else {
                Log.e(TAG, "Speech cancelled because language is not available")
            }
        }
    }

    /**
     * Валидация и установка языка.
     * Проверяет isLanguageAvailable() и возвращает true только при LANG_AVAILABLE / LANG_COUNTRY_AVAILABLE.
     * При отсутствии языковых пакетов (LANG_MISSING_DATA / LANG_NOT_SUPPORTED) выставляет флаг
     * languageMissing = true и формирует понятное сообщение для пользователя.
     */
    private fun applyLanguage(tts: TextToSpeech, targetLocale: Locale?): Boolean {
        val localesToTry = listOfNotNull(
            targetLocale,
            targetLocale?.let { Locale.forLanguageTag(it.language) },
            Locale.getDefault(),
            Locale.Builder().setLanguage("ru").setRegion("RU").build(),
            Locale.ENGLISH,
        )

        for (loc in localesToTry) {
            val availability = runCatching {
                tts.isLanguageAvailable(loc)
            }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)

            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                val setRes = runCatching { tts.setLanguage(loc) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                if (setRes >= TextToSpeech.LANG_AVAILABLE) {
                    _state.update {
                        it.copy(
                            languageMissing = false,
                            targetLanguage = loc.displayLanguage,
                            errorMessage = null,
                        )
                    }
                    return true
                }
            } else if (availability == TextToSpeech.LANG_MISSING_DATA) {
                Log.w(TAG, "TTS voice data missing for locale: $loc")
            } else {
                Log.w(TAG, "TTS locale not supported: $loc (availability code: $availability)")
            }
        }

        val langName = targetLocale?.displayLanguage?.takeIf { it.isNotBlank() } ?: "этого языка"
        val errorText = "Голос для $langName не установлен"
        Log.e(TAG, "No usable TTS language found. $errorText")

        _state.update {
            it.copy(
                speaking = false,
                languageMissing = true,
                targetLanguage = langName,
                errorMessage = errorText,
            )
        }
        return false
    }

    private fun enqueueFrom(tts: TextToSpeech, fromIndex: Int) {
        tts.stop()
        _state.update { it.copy(speaking = true, blockIndex = fromIndex) }

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        var enqueuedCount = 0
        for (i in fromIndex until queue.size) {
            val text = queue[i].trim()
            if (text.isNotEmpty()) {
                tts.speak(text, TextToSpeech.QUEUE_ADD, params, i.toString())
                enqueuedCount++
            }
        }
        Log.d(TAG, "Enqueued $enqueuedCount blocks for speech starting from block $fromIndex")
    }

    fun play(
        blocks: List<String>,
        fromIndex: Int = 0,
        language: Locale? = null,
        onBlock: (Int) -> Unit = {},
        onComplete: () -> Unit = {},
    ) {
        speak(blocks, fromIndex, language, onBlock, onComplete)
    }

    fun pause() {
        engine?.stop()
        _state.update { it.copy(speaking = false, paused = true) }
    }

    fun resume() {
        val currentBlock = _state.value.blockIndex
        _state.update { it.copy(speaking = true, paused = false) }
        ensureEngine { tts ->
            enqueueFrom(tts, currentBlock)
        }
    }

    fun stop() {
        engine?.stop()
        _state.update { it.copy(speaking = false, paused = false, blockIndex = 0) }
    }

    fun setRate(rate: Float) {
        _state.update { it.copy(rate = rate) }
        engine?.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        _state.update { it.copy(pitch = pitch) }
        engine?.setPitch(pitch)
    }

    fun skipTo(blockIndex: Int) {
        val bounded = blockIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        _state.update { it.copy(blockIndex = bounded) }
        if (_state.value.speaking) {
            ensureEngine { tts ->
                enqueueFrom(tts, bounded)
            }
        }
    }

    fun skip(delta: Int) {
        val target = _state.value.blockIndex + delta
        skipTo(target)
    }

    fun release() {
        shutdown()
    }

    fun shutdown() {
        Log.i(TAG, "Shutting down TextToSpeech engine")
        runCatching {
            engine?.stop()
            engine?.shutdown()
        }.onFailure { e ->
            Log.e(TAG, "Error during TTS shutdown", e)
        }
        engine = null
        isInitializing = false
        pendingReady = null
        _state.update {
            TtsState(
                availableEngines = it.availableEngines,
                selectedEngine = it.selectedEngine,
            )
        }
    }

    companion object {
        fun resolveLocale(languageCode: String?, sampleText: String? = null): Locale {
            val normalized = languageCode?.trim()?.lowercase(Locale.ROOT)
            return when {
                normalized == "ru" || normalized == "rus" || normalized == "russian" || normalized?.startsWith("ru") == true ->
                    Locale.Builder().setLanguage("ru").setRegion("RU").build()
                normalized == "en" || normalized == "eng" || normalized == "english" || normalized?.startsWith("en") == true ->
                    Locale.ENGLISH
                normalized == "de" || normalized == "deu" || normalized == "ger" || normalized?.startsWith("de") == true ->
                    Locale.GERMAN
                normalized == "it" || normalized == "ita" || normalized?.startsWith("it") == true ->
                    Locale.ITALIAN
                normalized == "az" || normalized == "aze" || normalized?.startsWith("az") == true ->
                    Locale.Builder().setLanguage("az").setRegion("AZ").build()
                normalized == "fr" || normalized == "fra" || normalized == "fre" || normalized?.startsWith("fr") == true ->
                    Locale.FRENCH
                normalized == "es" || normalized == "spa" || normalized?.startsWith("es") == true ->
                    Locale.Builder().setLanguage("es").setRegion("ES").build()
                !normalized.isNullOrBlank() -> {
                    val parsed = runCatching { Locale.forLanguageTag(normalized) }.getOrNull()
                    if (parsed != null && parsed.language.isNotEmpty()) parsed else Locale.forLanguageTag(normalized)
                }
                else -> {
                    if (sampleText != null && sampleText.any { it in 'Ѐ'..'ӿ' }) {
                        Locale.Builder().setLanguage("ru").setRegion("RU").build()
                    } else {
                        Locale.getDefault()
                    }
                }
            }
        }
    }
}

/** Алиас для унификации архитектуры TtsManager / TtsController */
typealias TtsManager = TtsController
