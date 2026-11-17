package com.example.data.search

import com.example.data.model.JarvisLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WebSearchResult(
    val title: String,
    val snippet: String,
    val url: String,
    val source: String
)

data class WebSearchResponse(
    val query: String,
    val instantAnswer: String?,
    val results: List<WebSearchResult>
)

class WebSearchRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, language: JarvisLanguage): Result<WebSearchResponse> =
        withContext(Dispatchers.IO) {
            try {
                val trimmed = query.trim()
                if (trimmed.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Enter a search query."))
                }
                val encoded = URLEncoder.encode(trimmed, "UTF-8")

                val results = mutableListOf<WebSearchResult>()
                var instantAnswer: String? = null

                // 1. DuckDuckGo Instant Answer API
                try {
                    val ddgUrl = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
                    val ddgReq = Request.Builder()
                        .url(ddgUrl)
                        .header("User-Agent", "JarvisAndroidAssistant/1.0")
                        .build()
                    client.newCall(ddgReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            if (body.isNotBlank()) {
                                val json = JSONObject(body)
                                val abstractText = json.optString("AbstractText").trim()
                                val abstractUrl = json.optString("AbstractURL").trim()
                                val heading = json.optString("Heading").trim()
                                if (abstractText.isNotEmpty()) {
                                    instantAnswer = abstractText
                                    if (abstractUrl.isNotEmpty()) {
                                        results.add(
                                            WebSearchResult(
                                                title = heading.ifEmpty { trimmed },
                                                snippet = abstractText,
                                                url = abstractUrl,
                                                source = json.optString("AbstractSource").ifEmpty { "DuckDuckGo" }
                                            )
                                        )
                                    }
                                }
                                val related = json.optJSONArray("RelatedTopics")
                                if (related != null) {
                                    for (i in 0 until minOf(related.length(), 4)) {
                                        val item = related.optJSONObject(i) ?: continue
                                        val text = item.optString("Text").trim()
                                        val firstUrl = item.optString("FirstURL").trim()
                                        if (text.isNotEmpty() && firstUrl.isNotEmpty()) {
                                            results.add(
                                                WebSearchResult(
                                                    title = text.substringBefore(" - ").take(80),
                                                    snippet = text,
                                                    url = firstUrl,
                                                    source = "DuckDuckGo"
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Continue to Wikipedia search even if DuckDuckGo fails
                }

                // 2. Wikipedia Search API (localized subdomain with English fallback)
                val wikiSubdomain = when (language) {
                    JarvisLanguage.HINDI -> "hi"
                    JarvisLanguage.TELUGU -> "te"
                    JarvisLanguage.ENGLISH -> "en"
                }
                val wikiHosts = if (wikiSubdomain == "en") listOf("en") else listOf(wikiSubdomain, "en")

                for (host in wikiHosts) {
                    if (results.size >= 6) break
                    val wikiUrl =
                        "https://$host.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&utf8=1&srlimit=5"
                    val wikiReq = Request.Builder()
                        .url(wikiUrl)
                        .header("User-Agent", "JarvisAndroidAssistant/1.0 (contact@jarvis-app.local)")
                        .build()
                    client.newCall(wikiReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            if (body.isNotBlank()) {
                                val json = JSONObject(body)
                                val searchArr = json.optJSONObject("query")?.optJSONArray("search")
                                if (searchArr != null) {
                                    for (i in 0 until searchArr.length()) {
                                        val obj = searchArr.optJSONObject(i) ?: continue
                                        val title = obj.optString("title").trim()
                                        val rawSnippet = obj.optString("snippet")
                                            .replace(Regex("<[^>]*>"), "")
                                            .replace("&quot;", "\"")
                                            .replace("&amp;", "&")
                                            .replace("&#039;", "'")
                                            .trim()
                                        val pageUrl = "https://$host.wikipedia.org/wiki/${
                                            URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
                                        }"
                                        if (title.isNotEmpty() && results.none { it.url == pageUrl }) {
                                            results.add(
                                                WebSearchResult(
                                                    title = title,
                                                    snippet = rawSnippet.ifEmpty { "Wikipedia article: $title" },
                                                    url = pageUrl,
                                                    source = "Wikipedia ($host)"
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (results.isEmpty() && instantAnswer == null) {
                    Result.failure(
                        IllegalStateException(
                            "No live results returned for \"$trimmed\". You can open the full search in your browser."
                        )
                    )
                } else {
                    Result.success(
                        WebSearchResponse(
                            query = trimmed,
                            instantAnswer = instantAnswer,
                            results = results
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(
                    IllegalStateException(
                        e.localizedMessage ?: "Web search unavailable. Check network connectivity.",
                        e
                    )
                )
            }
        }
}
