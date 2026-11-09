package com.mediacenter.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private data class PlaybackEpisode(val number: Int, val title: String, val url: String)
private data class PlaybackVoice(val id: String, val name: String, val episodes: List<PlaybackEpisode>)

private suspend fun loadPlaybackVoices(context: android.content.Context, movie: Movie): List<PlaybackVoice> = withContext(Dispatchers.IO) {
    val base = context.getSharedPreferences("arnix_sources", 0)
        .getString("api_base", "")?.trim()?.trimEnd('/').orEmpty()
    require(base.startsWith("https://")) {
        "Сначала откройте «Источники» и укажите HTTPS-адрес backend ARNIX."
    }
    val query = URLEncoder.encode(movie.title, "UTF-8")
    val connection = (URL("$base/api/lampa/search?q=$query").openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000
        readTimeout = 20000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
    }
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        check(code in 200..299) { "Backend вернул HTTP $code" }
        val root = JSONObject(body)
        val groups = root.optJSONArray("results") ?: JSONArray()
        val voices = mutableListOf<PlaybackVoice>()
        for (i in 0 until groups.length()) {
            val group = groups.optJSONObject(i) ?: continue
            val sourceName = group.optString("source_name").ifBlank { group.optString("source_id") }
            val rows = group.optJSONArray("results") ?: continue
            val episodes = mutableListOf<PlaybackEpisode>()
            for (j in 0 until rows.length()) {
                val row = rows.optJSONObject(j) ?: continue
                val streamUrl = row.optString("stream_url")
                if (!streamUrl.startsWith("https://")) continue
                val title = row.optString("title").ifBlank { movie.title }
                episodes += PlaybackEpisode(j + 1, title, streamUrl)
            }
            if (episodes.isNotEmpty()) voices += PlaybackVoice(
                group.optString("source_id", "source-$i"), sourceName, episodes
            )
        }
        voices
    } finally { connection.disconnect() }
}

@Composable
internal fun PlaybackHubScreen(movie: Movie, onBack: () -> Unit) {
    val context = LocalContext.current
    var voices by remember(movie.key) { mutableStateOf<List<PlaybackVoice>>(emptyList()) }
    var loading by remember(movie.key) { mutableStateOf(true) }
    var error by remember(movie.key) { mutableStateOf("") }
    var selectedVoice by remember { mutableStateOf<PlaybackVoice?>(null) }
    var selectedEpisode by remember { mutableStateOf<PlaybackEpisode?>(null) }

    BackHandler {
        when {
            selectedEpisode != null -> selectedEpisode = null
            selectedVoice != null -> selectedVoice = null
            else -> onBack()
        }
    }

    LaunchedEffect(movie.key) {
        loading = true
        error = ""
        try {
            voices = loadPlaybackVoices(context, movie)
            if (voices.isEmpty()) error = "Источники не вернули прямые HTTPS-видеопотоки для этого фильма. Проверьте настройки backend и адаптеров."
        } catch (e: Exception) { error = e.message ?: "Не удалось загрузить плагины" }
        loading = false
    }

    val episode = selectedEpisode
    if (episode != null) {
        ArnixPlayerScreen(movie.title, selectedVoice?.name.orEmpty(), episode, onBack = { selectedEpisode = null })
        return
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF070707)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (selectedVoice != null) selectedVoice = null else onBack()
            }) { Icon(Icons.Rounded.ArrowBack, "Назад", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(if (selectedVoice == null) "ВЫБОР ОЗВУЧКИ" else selectedVoice!!.name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(movie.title, color = Color(0xFFAAAAAA), fontSize = 12.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(18.dp))
        when {
            loading -> Text("Проверяем подключённые источники…", color = Color.LightGray)
            error.isNotBlank() -> {
                Text("Нет подключённого источника", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(error, color = Color(0xFFAAAAAA), fontSize = 13.sp)
            }
            selectedVoice == null -> {
                Text("Выберите озвучку", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(voices, key = { it.id }) { voice ->
                        Row(Modifier.fillMaxWidth().background(Color(0xFF171717), RoundedCornerShape(14.dp))
                            .clickable { selectedVoice = voice }.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(voice.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("${voice.episodes.size} серий / видео", color = Color(0xFF999999), fontSize = 12.sp)
                            }
                            Text("›", color = Color.White, fontSize = 26.sp)
                        }
                    }
                }
            }
            else -> {
                Text("Выберите серию", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(selectedVoice!!.episodes, key = { it.number }) { ep ->
                        Row(Modifier.fillMaxWidth().background(Color(0xFF171717), RoundedCornerShape(12.dp))
                            .clickable { selectedEpisode = ep }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
                            Spacer(Modifier.width(12.dp))
                            Text(ep.title, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArnixPlayerScreen(title: String, voiceName: String, episode: PlaybackEpisode, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember(episode.url) {
        ExoPlayer.Builder(context).build().apply {
            val lowerUrl = episode.url.lowercase()
            val itemBuilder = MediaItem.Builder().setUri(episode.url)
            when {
                ".m3u8" in lowerUrl -> itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                ".mpd" in lowerUrl -> itemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                ".mp4" in lowerUrl -> itemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
            }
            setMediaItem(itemBuilder.build())
            prepare()
            playWhenReady = true
        }
    }
    var playing by remember { mutableStateOf(true) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var buffering by remember { mutableStateOf(true) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                duration = player.duration.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    val exoPlayer = player
    LaunchedEffect(exoPlayer) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            kotlinx.coroutines.delay(500)
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Назад", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("ARNIX PLAYER", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text(title, color = Color(0xFFAAAAAA), fontSize = 11.sp, maxLines = 1)
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            if (buffering) Text("Загрузка видео…", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp))
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFF101010)).padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(voiceName + "  •  " + episode.title, color = Color(0xFFBBBBBB), fontSize = 12.sp)
            Slider(value = if (duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                onValueChange = { position = it.toLong() },
                onValueChangeFinished = { player.seekTo(position) },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatPlayerTime(position) + " / " + formatPlayerTime(duration), color = Color(0xFFAAAAAA), fontSize = 11.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { player.seekBack() }) { Icon(Icons.Rounded.Replay10, "Назад на 10 секунд", tint = Color.White) }
                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Воспроизвести", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = { player.seekForward() }) { Icon(Icons.Rounded.Forward10, "Вперёд на 10 секунд", tint = Color.White) }
            }
            Text("Фирменный видеоплеер ARNIX", color = Color(0xFF666666), fontSize = 10.sp)
        }
    }
}

private fun formatPlayerTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}
