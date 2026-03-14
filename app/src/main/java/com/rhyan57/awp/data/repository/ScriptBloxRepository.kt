package com.rhyan57.awp.data.repository

import com.google.gson.JsonParser
import com.rhyan57.awp.data.model.ScriptBloxScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ScriptBloxRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                    .build()
            )
        }
        .build()

    suspend fun fetchScripts(query: String = "", page: Int = 1): List<ScriptBloxScript> = withContext(Dispatchers.IO) {
        val url = if (query.isBlank())
            "https://scriptblox.com/api/script/fetch?page=$page&max=20&mode=free"
        else
            "https://scriptblox.com/api/script/search?q=${query.trim().replace(" ", "+")}&page=$page&max=20&mode=free"

        val response = client.newCall(Request.Builder().url(url).build()).execute()
        if (!response.isSuccessful) return@withContext emptyList()

        val body = response.body?.string() ?: return@withContext emptyList()
        parseScripts(body)
    }

    private fun parseScripts(body: String): List<ScriptBloxScript> {
        return try {
            val root    = JsonParser.parseString(body).asJsonObject
            val result  = root.getAsJsonObject("result") ?: return emptyList()
            val scripts = result.getAsJsonArray("scripts") ?: return emptyList()

            scripts.mapNotNull { el ->
                try {
                    val s       = el.asJsonObject
                    val game    = s.getAsJsonObject("game")
                    val imageEl = game?.get("imageUrl")
                    ScriptBloxScript(
                        id       = s.get("_id")?.asString ?: return@mapNotNull null,
                        title    = s.get("title")?.asString ?: "Untitled",
                        game     = game?.get("name")?.asString ?: "Unknown",
                        script   = s.get("script")?.asString ?: "",
                        imageUrl = if (imageEl != null && !imageEl.isJsonNull) {
                            val raw = imageEl.asString
                            if (raw.startsWith("http")) raw else "https://scriptblox.com$raw"
                        } else null,
                        verified = s.get("verified")?.asBoolean ?: false,
                        views    = s.get("views")?.asInt ?: 0
                    )
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }
}
