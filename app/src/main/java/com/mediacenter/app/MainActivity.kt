package com.mediacenter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private val Background = Color(0xFF050505)
private val CardColor = Color(0xFF151515)
private val CardFocused = Color(0xFF292929)
private val White = Color.White
private val Muted = Color(0xFFB8B8B8)
private const val TMDB_KEY = "de83af9bf3f4cf2d61cb8a9467045768"
private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Background) {
                    var page by remember { mutableStateOf("home") }
                    when (page) {
                        "cinema" -> CinemaScreen(onBack = { page = "home" })
                        else -> HomeScreen(onCinema = { page = "cinema" })
                    }
                }
            }
        }
    }
}

private data class MenuItem(val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun HomeScreen(onCinema: () -> Unit) {
    val items = listOf(
        MenuItem("ТВ", "Телеканалы", Icons.Rounded.Tv),
        MenuItem("Кинотеатр", "Фильмы и сериалы", Icons.Rounded.Movie),
        MenuItem("Настройки", "Параметры приложения", Icons.Rounded.Settings)
    )
    var showHeader by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showHeader = true }
    BoxWithConstraints(Modifier.fillMaxSize().background(Background).padding(horizontal = 24.dp, vertical = 20.dp)) {
        val wide = maxWidth >= 600.dp
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(showHeader, enter = fadeIn() + slideInVertically { -it / 3 }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("◈  МЕДИАЦЕНТР", color = White, fontSize = if (wide) 24.sp else 19.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text("ANDROID", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(showHeader, enter = fadeIn() + slideInVertically { it / 2 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Добро пожаловать!", color = White, fontSize = if (wide) 34.sp else 27.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Выберите раздел", color = Muted, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(if (wide) 38.dp else 26.dp))
            if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items.forEach { item -> MenuCard(item, Modifier.weight(1f), true) { if (item.title == "Кинотеатр") onCinema() } }
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items.forEach { item -> MenuCard(item, Modifier.fillMaxWidth(), false) { if (item.title == "Кинотеатр") onCinema() } }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Медиацентр  •  Версия 1.1", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MenuCard(item: MenuItem, modifier: Modifier = Modifier, wide: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else if (focused) 1.025f else 1f, spring(dampingRatio = .55f, stiffness = 500f), label = "menuScale")
    Row(
        modifier.height(if (wide) 190.dp else 88.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .background(if (focused) CardFocused else CardColor, RoundedCornerShape(20.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) White else Color(0xFF3A3A3A), RoundedCornerShape(20.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = source, indication = null, onClick = onClick).focusable(),
        horizontalArrangement = if (wide) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (wide) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(item.icon, item.title, tint = White, modifier = Modifier.size(46.dp))
                Spacer(Modifier.height(14.dp))
                Text(item.title, color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(item.subtitle, color = Muted, fontSize = 12.sp)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(item.icon, item.title, tint = White, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(18.dp))
                Column {
                    Text(item.title, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(item.subtitle, color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

private data class Movie(val id: Int, val title: String, val overview: String, val poster: String, val date: String, val rating: Double)

private suspend fun tmdb(path: String): JSONObject = withContext(Dispatchers.IO) {
    val url = URL("https://api.themoviedb.org/3/$path?api_key=$TMDB_KEY&language=ru-RU")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 15000
        requestMethod = "GET"
    }
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw Exception("TMDB вернул ошибку $code: $body")
        JSONObject(body)
    } finally { connection.disconnect() }
}

private fun JSONObject.toMovies(): List<Movie> {
    val array = optJSONArray("results") ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        val title = o.optString("title").ifBlank { o.optString("name") }
        Movie(o.optInt("id"), title, o.optString("overview"), o.optString("poster_path").takeIf { it != "null" }?.let { IMAGE_BASE + it } ?: "", o.optString("release_date").ifBlank { o.optString("first_air_date") }, o.optDouble("vote_average"))
    }
}

@Composable
private fun CinemaScreen(onBack: () -> Unit) {
    val categories = listOf("Популярное", "Сейчас в кино", "Лучшее", "Скоро", "Сериалы")
    val paths = listOf("movie/popular", "movie/now_playing", "movie/top_rated", "movie/upcoming", "tv/popular")
    var category by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var selected by remember { mutableStateOf<Movie?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(category, search) {
        loading = true
        error = ""
        try {
            val path = if (search.isNotBlank()) "search/multi?query=" + URLEncoder.encode(search.trim(), "UTF-8") else paths[category]
            movies = tmdb(path).toMovies().filter { it.poster.isNotBlank() }
        } catch (e: Exception) {
            error = e.message ?: "Не удалось загрузить каталог"
        } finally { loading = false }
    }

    selected?.let { movie ->
        Column(Modifier.fillMaxSize().background(Background).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selected = null }.focusable()) {
                Icon(Icons.Rounded.ArrowBack, "Назад", tint = White)
                Spacer(Modifier.width(10.dp))
                Text("Назад к каталогу", color = White)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                AsyncImage(model = movie.poster, contentDescription = movie.title, modifier = Modifier.width(130.dp).height(195.dp))
                Column(Modifier.weight(1f)) {
                    Text(movie.title, color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("★ " + String.format("%.1f", movie.rating) + "   •   " + movie.date.take(4), color = Muted, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(movie.overview.ifBlank { "Описание пока отсутствует." }, color = White, fontSize = 15.sp)
            Text("Данные и постер предоставлены TMDB.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 18.dp))
        }
        return
    }

    Column(Modifier.fillMaxSize().background(Background).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(CardColor, RoundedCornerShape(12.dp)).clickable(onClick = onBack).focusable(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ArrowBack, "Назад", tint = White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("КИНОТЕАТР", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Фильмы и сериалы • TMDB", color = Muted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().background(CardColor, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, "Поиск", tint = Muted)
            Spacer(Modifier.width(10.dp))
            BasicTextField(value = search, onValueChange = { search = it }, modifier = Modifier.weight(1f), singleLine = true, textStyle = TextStyle(color = White, fontSize = 15.sp), cursorBrush = SolidColor(White), decorationBox = { inner -> if (search.isEmpty()) Text("Найти фильм или сериал…", color = Muted); inner() })
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories.size) { index ->
                val active = category == index && search.isBlank()
                Text(categories[index], color = if (active) Color.Black else White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.background(if (active) White else CardColor, RoundedCornerShape(20.dp)).clickable { category = index; search = "" }.focusable().padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        when {
            loading -> Text("Загрузка каталога…", color = Muted, modifier = Modifier.padding(12.dp))
            error.isNotBlank() -> Text(error, color = White, modifier = Modifier.padding(12.dp))
            movies.isEmpty() -> Text("Ничего не найдено.", color = Muted, modifier = Modifier.padding(12.dp))
            else -> LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 112.dp), modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(movies, key = { it.id }) { movie ->
                    Column(Modifier.clickable { selected = movie }.focusable()) {
                        AsyncImage(model = movie.poster, contentDescription = movie.title, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(CardColor, RoundedCornerShape(12.dp)))
                        Text(movie.title, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
                        Text("★ " + String.format("%.1f", movie.rating) + "  •  " + movie.date.take(4), color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
