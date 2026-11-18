package com.newpos.wordcard.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.MainThread
import java.util.Locale

/** 主线程调用。默认巴西葡语，语言配置在释放引擎后仍保留。 */
@MainThread
object TtsUtils {
    private const val TAG = "TtsUtils"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var initialized = false
    private var languageAvailable = false
    private var locale = Locale.forLanguageTag("ru")
    private var pendingText: String? = null
    private var pendingSpeechRate = 1f
    private var utteranceId: String? = null
    private var onDone: (() -> Unit)? = null

    /** 可提前初始化；也可直接 speak 自动初始化。 */
    fun init(context: Context, locale: Locale = this.locale) {
        setLocale(locale)
        if (tts != null) return
        val appContext = context.applicationContext
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(appContext) { status ->
            mainHandler.post {
                // 忽略释放后的旧回调，并确保 engine 已完成赋值。
                if (tts !== engine) return@post
                if (status != TextToSpeech.SUCCESS) {
                    Log.e(TAG, "TTS 初始化失败: $status")
                    shutdown()
                    return@post
                }
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = finish(utteranceId, true)
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = finish(utteranceId, false)
                    override fun onStop(utteranceId: String?, interrupted: Boolean) =
                        finish(utteranceId, false)
                })
                initialized = true
                applyLocale()
            }
        }
        tts = engine
    }

    /** 修改后续朗读语言，相同语言不重复设置；初始化前只保存配置。 */
    fun setLocale(locale: Locale) {
        if (this.locale == locale) return
        this.locale = locale
        if (initialized) {
            stop()
            applyLocale()
        }
    }

    private fun applyLocale() {
        languageAvailable = (tts?.setLanguage(locale) ?: TextToSpeech.ERROR) >= 0
        if (!languageAvailable) Log.e(TAG, "不支持语言或缺少语音数据: $locale")
    }

    /**
     * 朗读最新文本，沿用当前语言。正常读完后在主线程回调；停止、替换、出错不回调。
     * Fragment 的 onDestroyView 中调用 stop()，取消朗读并清理回调。
     */
    fun speak(
        text: String,
        speechRate: Float = 1f,
        onDone: (() -> Unit)? = null
    ) {
        if (text.isBlank()) return
        stop()
        if (!languageAvailable) return
        if (tts?.setSpeechRate(speechRate) == TextToSpeech.ERROR) {
            Log.w(TAG, "无法设置 TTS 语速: $speechRate")
        }
        utteranceId = "utterance_${System.nanoTime()}"
        this.onDone = onDone
        if (tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.ERROR) {
            Log.e(TAG, "TTS 朗读请求失败")
            stop()
        }
    }

    private fun finish(id: String?, completed: Boolean) {
        mainHandler.post {
            if (id == null || id != utteranceId) return@post
            val callback = onDone
            utteranceId = null
            onDone = null
            if (completed) callback?.invoke() else Log.d(TAG, "TTS 朗读停止或出错")
        }
    }

    /** 停止朗读，取消待播文本和完成回调。 */
    fun stop() {
        pendingText = null
        utteranceId = null
        onDone = null
        tts?.stop()
    }

    /** 释放引擎；下次调用时按当前语言配置重新初始化。 */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        initialized = false
        languageAvailable = false
    }
}
