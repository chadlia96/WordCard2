package com.newpos.wordcard.llm

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object LLMChat {
    private const val PREFERENCES = "llm"
    private const val API_KEY = "api_key"
    private const val CHAT_URL = "https://api.deepseek.com/chat/completions"
    private const val MODEL = "deepseek-flash"

    fun getApiKey(context: Context): String =
        normalizeApiKey(
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(API_KEY, "").orEmpty()
        )

    fun saveApiKey(context: Context, apiKey: String) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            val key = normalizeApiKey(apiKey)
            if (key.isEmpty()) remove(API_KEY) else putString(API_KEY, key)
        }
    }

    private fun normalizeApiKey(apiKey: String): String {
        val key = apiKey.trim()
        return if (key.startsWith("Bearer ", ignoreCase = true)) {
            key.substringAfter(' ').trim()
        } else {
            key
        }
    }

    /** 使用 JSON 模式时，prompt 也应明确要求模型输出 JSON。 */
    suspend fun chat(context: Context, prompt: String, jsonOutput: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val apiKey = getApiKey(context)
            check(apiKey.isNotEmpty()) { "请先在侧栏设置 DeepSeek API Key" }

            val message = JSONObject().put("role", "user").put("content", prompt)
            val request = JSONObject()
                .put("model", MODEL)
                .put("messages", JSONArray().put(message))
                .put("stream", false)
                .put("thinking", JSONObject().put("type", "disabled"))
            if (jsonOutput) {
                request.put("response_format", JSONObject().put("type", "json_object"))
            }

            val connection = URL(CHAT_URL).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 60_000
                connection.instanceFollowRedirects = false
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    throw IOException("DeepSeek 认证失败（HTTP 401）。请在侧栏检查或更换 DeepSeek 开放平台的 API Key。")
                }
                if (status !in 200..299) {
                    throw IOException("DeepSeek 请求失败（HTTP $status）")
                }

                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val reply = JSONObject(body).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.opt("content") as? String
                reply?.takeIf { it.isNotBlank() } ?: throw IOException("DeepSeek 返回空响应")
            } finally {
                connection.disconnect()
            }
        }
}
