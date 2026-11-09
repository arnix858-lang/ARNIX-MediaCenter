package com.mediacenter.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val SourceBg = Color(0xFF080808)
private val SourceCard = Color(0xFF191919)
private const val DEFAULT_SOURCE_API = "https://arnix-hdrezka-api.onrender.com"

private data class NativeSource(val id: String, val name: String)
private data class NativeSourceItem(
    val id: String, val title: String, val year: String, val poster: String, val description: String, val source: String
)

private fun sourceApi(context: android.content.Context): String =
    context.getSharedPreferences("arnix_sources", 0)
        .getString("api_base", DEFAULT_SOURCE_API)?.trim()?.trimEnd('/')
        ?: DEFAULT_SOURCE_API

private suspend fun sourceGet(url: String): JSONObject = withContext(Dispatchers.IO) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000
        readTimeout = 15000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
    }
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw IllegalStateException("HTTP $code: ${body.take(180)}")
        JSONObject(body)
    } finally { connection.disconnect() }
}

private suspend fun loadNativeSources(base: String): List<NativeSource> {
    val array = sourceGet("$base/api/lampa/sources").optJSONArray("sources") ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        NativeSource(item.optString("id"), item.optString("name"))
    }
}

private suspend fun searchNativeSources(base: String, query: String): List<NativeSourceItem> {
    val encoded = java.net.URLEncoder.encode(query, "UTF-8")
    val root = sourceGet("$base/api/lampa/search?q=$encoded")
    val groups = root.optJSONArray("results") ?: return emptyList()
    val found = mutableListOf<NativeSourceItem>()
    for (i in 0 until groups.length()) {
        val group = groups.optJSONObject(i) ?: continue
        val sourceName = group.optString("source_name", group.optString("source_id"))
        val rows = group.optJSONArray("results") ?: continue
        for (j in 0 until rows.length()) {
            val row = rows.optJSONObject(j) ?: continue
            found += NativeSourceItem(
                row.optString("id"), row.optString("title", "Без названия"),
                row.optString("year"), row.optString("poster"),
                row.optString("description"), sourceName
            )
        }
    }
    return found
}

@Composable
internal fun PluginManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var apiBase by remember { mutableStateOf(sourceApi(context)) }
    var query by remember { mutableStateOf("") }
    var sources by remember { mutableStateOf<List<NativeSource>>(emptyList()) }
    var results by remember { mutableStateOf<List<NativeSourceItem>>(emptyList()) }
    var status by remember { mutableStateOf("Укажите адрес ARNIX API и проверьте соединение") }
    var busy by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    fun saveBase() {
        apiBase = apiBase.trim().trimEnd('/')
        context.getSharedPreferences("arnix_sources", 0).edit().putString("api_base", apiBase).apply()
    }

    Column(Modifier.fillMaxSize().background(SourceBg).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Назад", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("ИСТОЧНИКИ", color = Color.White, fontSize = 21.sp)
                Text("Нативный каталог ARNIX", color = Color.LightGray, fontSize = 12.sp)
            }
            IconButton(enabled = !busy, onClick = {
                saveBase()
                busy = true
            }) { Icon(Icons.Rounded.Refresh, "Проверить", tint = Color.White) }
        }
        Spacer(Modifier.height(14.dp))
        Text("Адрес backend API", color = Color.White, fontSize = 13.sp)
        BasicTextField(value = apiBase, onValueChange = { apiBase = it },
            modifier = Modifier.fillMaxWidth().background(SourceCard, RoundedCornerShape(12.dp)).padding(13.dp),
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp), singleLine = true)
        Spacer(Modifier.height(8.dp))
        Text("Сохранить и проверить источники", color = Color.White,
            modifier = Modifier.background(Color(0xFF303030), RoundedCornerShape(10.dp)).clickable {
                saveBase()
                busy = true
            }.padding(12.dp))
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = Color.LightGray)
            Spacer(Modifier.width(8.dp))
            BasicTextField(value = query, onValueChange = { query = it },
                modifier = Modifier.weight(1f).background(SourceCard, RoundedCornerShape(10.dp)).padding(12.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp), singleLine = true,
                decorationBox = { inner -> if (query.isEmpty()) Text("Поиск по источникам…", color = Color.Gray); inner() })
            Text("Найти", color = Color.White, modifier = Modifier.clickable {
                if (query.isNotBlank()) { saveBase(); busy = true }
            }.padding(10.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("Подключено: ${sources.size}", color = Color.White, fontSize = 14.sp)
        Text(status, color = Color.LightGray, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (results.isNotEmpty()) {
                item { Text("РЕЗУЛЬТАТЫ ПОИСКА", color = Color.White, fontSize = 12.sp) }
                items(results) { item ->
                    Column(Modifier.fillMaxWidth().background(SourceCard, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Text(item.title + if (item.year.isNotBlank()) " (${item.year})" else "", color = Color.White, fontSize = 14.sp)
                        Text(item.source, color = Color(0xFFAAAAAA), fontSize = 11.sp)
                        if (item.description.isNotBlank()) Text(item.description, color = Color(0xFFBBBBBB), fontSize = 12.sp, maxLines = 3)
                    }
                }
            } else {
                items(sources) { source ->
                    Row(Modifier.fillMaxWidth().background(SourceCard, RoundedCornerShape(12.dp)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Cloud, null, tint = Color.White)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(source.name, color = Color.White, fontSize = 14.sp)
                            Text(source.id, color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Text("Совместимость: нативные JSON-адаптеры. Это не запуск произвольных JavaScript-плагинов Lampa. Потоки с DRM не обходятся.",
            color = Color.Gray, fontSize = 10.sp, lineHeight = 14.sp)
    }

    LaunchedEffect(busy) {
        if (!busy) return@LaunchedEffect
        try {
            val base = apiBase.trim().trimEnd('/')
            if (!base.startsWith("https://")) throw IllegalArgumentException("Используйте HTTPS-адрес backend.")
            sources = loadNativeSources(base)
            results = if (query.isNotBlank()) searchNativeSources(base, query.trim()) else emptyList()
            status = if (sources.isEmpty()) "Соединение есть, но источники не настроены на сервере."
                else "Подключение успешно. Источников: ${sources.size}"
        } catch (e: Exception) {
            status = "Ошибка подключения: ${e.message ?: "неизвестная ошибка"}"
            sources = emptyList()
            results = emptyList()
        } finally { busy = false }
    }
}
