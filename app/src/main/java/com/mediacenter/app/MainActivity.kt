package com.mediacenter.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
private const val BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Background) {
                    var page by remember { mutableStateOf("home") }
                    BackHandler(enabled = page != "home") { page = "home" }
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
            Text("Медиацентр  •  Версия 1.2", color = Muted, fontSize = 11.sp)
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

private data class Movie(
    val id: Int,
    val mediaType: String,
    val title: String,
    val overview: String,
    val poster: String,
    val backdrop: String,
    val date: String,
    val rating: Double
) {
    val key: String get() = "$mediaType-$id"
}

private data class MovieDetails(
    val tagline: String,
    val genres: List<String>,
    val runtime: String,
    val status: String,
    val originalTitle: String,
    val country: String,
    val director: String,
    val writers: String,
    val cast: List<String>,
    val companies: String,
    val seasons: String,
    val producers: String,
    val creators: String
)

private suspend fun tmdb(path: String): JSONObject = withContext(Dispatchers.IO) {
    val separator = if (path.contains("?")) "&" else "?"
    val url = URL("https://api.themoviedb.org/3/" + path + separator + "api_key=" + TMDB_KEY + "&language=ru-RU")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 12000
        readTimeout = 12000
        requestMethod = "GET"
    }
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw Exception("Ошибка TMDB: HTTP $code")
        JSONObject(body)
    } finally { connection.disconnect() }
}

private fun JSONObject.toMovies(defaultType: String = "movie"): List<Movie> {
    val array = optJSONArray("results") ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        val type = o.optString("media_type").takeIf { it == "tv" || it == "movie" } ?: defaultType
        val title = o.optString("title").ifBlank { o.optString("name") }
        Movie(
            o.optInt("id"), type, title, o.optString("overview"),
            o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }?.let { IMAGE_BASE + it } ?: "",
            o.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }?.let { BACKDROP_BASE + it } ?: "",
            o.optString("release_date").ifBlank { o.optString("first_air_date") },
            o.optDouble("vote_average")
        )
    }
}

private suspend fun loadMovieDetails(movie: Movie): MovieDetails {
    val data = tmdb("${movie.mediaType}/${movie.id}?append_to_response=credits")
    val credits = data.optJSONObject("credits") ?: JSONObject()
    val crew = credits.optJSONArray("crew") ?: JSONArray()
    fun crewNames(job: String): String = (0 until crew.length()).mapNotNull { crew.optJSONObject(it) }
        .filter { it.optString("job") == job }.map { it.optString("name") }.distinct().take(4).joinToString(", ")
    val castArray = credits.optJSONArray("cast") ?: JSONArray()
    val cast = (0 until minOf(castArray.length(), 12)).mapNotNull { castArray.optJSONObject(it)?.optString("name") }
    val genresArray = data.optJSONArray("genres") ?: JSONArray()
    val genres = (0 until genresArray.length()).mapNotNull { genresArray.optJSONObject(it)?.optString("name") }
    val companiesArray = data.optJSONArray("production_companies") ?: JSONArray()
    val companies = (0 until minOf(companiesArray.length(), 4)).mapNotNull { companiesArray.optJSONObject(it)?.optString("name") }.joinToString(", ")
    val countryArray = data.optJSONArray("production_countries") ?: JSONArray()
    val country = (0 until minOf(countryArray.length(), 3)).mapNotNull { countryArray.optJSONObject(it)?.optString("name") }.joinToString(", ")
    val runtime = if (movie.mediaType == "tv") {
        val episodes = data.optJSONArray("episode_run_time")
        if (episodes != null && episodes.length() > 0) "${episodes.optInt(0)} мин/серия" else "Не указано"
    } else {
        val minutes = data.optInt("runtime")
        if (minutes > 0) "${minutes / 60} ч ${minutes % 60} мин" else "Не указано"
    }
    val seasons = if (movie.mediaType == "tv") "${data.optInt("number_of_seasons")} сез., ${data.optInt("number_of_episodes")} сер." else ""
    val producers = crewNames("Producer").ifBlank { crewNames("Executive Producer") }
    val creatorsArray = data.optJSONArray("created_by") ?: JSONArray()
    val creators = (0 until creatorsArray.length()).mapNotNull { creatorsArray.optJSONObject(it)?.optString("name") }.joinToString(", ")
    return MovieDetails(
        data.optString("tagline"), genres, runtime, data.optString("status"),
        data.optString("original_title").ifBlank { data.optString("original_name") },
        country, crewNames("Director").ifBlank { crewNames("Director of Photography") },
        crewNames("Writer").ifBlank { crewNames("Screenplay") }, cast, companies, seasons, producers, creators
    )
}

private fun readFavorites(context: Context): List<Movie> {
    val raw = context.getSharedPreferences("media_center", Context.MODE_PRIVATE).getString("favorites", "[]") ?: "[]"
    return try {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            Movie(o.optInt("id"), o.optString("type"), o.optString("title"), o.optString("overview"), o.optString("poster"), o.optString("backdrop"), o.optString("date"), o.optDouble("rating"))
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveFavorites(context: Context, movies: List<Movie>) {
    val array = JSONArray()
    movies.forEach { m ->
        array.put(JSONObject().put("id", m.id).put("type", m.mediaType).put("title", m.title)
            .put("overview", m.overview).put("poster", m.poster).put("backdrop", m.backdrop)
            .put("date", m.date).put("rating", m.rating))
    }
    context.getSharedPreferences("media_center", Context.MODE_PRIVATE).edit().putString("favorites", array.toString()).apply()
}

@Composable
private fun CinemaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val categories = listOf("Популярное", "Сейчас в кино", "Лучшее", "Скоро", "Сериалы", "Избранное")
    val paths = listOf("movie/popular", "movie/now_playing", "movie/top_rated", "movie/upcoming", "tv/popular")
    var category by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var favorites by remember { mutableStateOf(readFavorites(context)) }
    var selected by remember { mutableStateOf<Movie?>(null) }
    var details by remember { mutableStateOf<MovieDetails?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var showWatchInfo by remember { mutableStateOf(false) }

    BackHandler(enabled = selected != null) { selected = null; details = null }
    LaunchedEffect(category, search) {
        if (category == 5 && search.isBlank()) {
            movies = favorites
        } else {
            loading = true
            error = ""
            try {
                val path = if (search.isNotBlank()) "search/multi?query=" + URLEncoder.encode(search.trim(), "UTF-8") + "&include_adult=false" else paths[category.coerceIn(0, 4)]
                val type = if (category == 4) "tv" else "movie"
                movies = tmdb(path).toMovies(type).filter { it.poster.isNotBlank() && it.mediaType != "person" }
            } catch (e: Exception) {
                error = e.message ?: "Не удалось загрузить каталог"
            } finally { loading = false }
        }
    }

    LaunchedEffect(selected?.key) {
        val movie = selected ?: return@LaunchedEffect
        details = null
        detailLoading = true
        try { details = loadMovieDetails(movie) } catch (_: Exception) { details = null }
        detailLoading = false
    }

    if (showWatchInfo) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showWatchInfo = false },
            title = { Text("Просмотр") },
            text = { Text("Каталог TMDB предоставляет информацию о фильмах, но не видеопотоки. Для воспроизведения нужно подключить легальный источник видео.") },
            confirmButton = { Text("Понятно", modifier = Modifier.clickable { showWatchInfo = false }.padding(12.dp)) },
            containerColor = CardColor,
            titleContentColor = White,
            textContentColor = Muted
        )
    }

    val current = selected
    if (current != null) {
        MovieDetailScreen(
            movie = current,
            details = details,
            loading = detailLoading,
            isFavorite = favorites.any { it.key == current.key },
            onBack = { selected = null; details = null },
            onFavorite = {
                favorites = if (favorites.any { it.key == current.key }) favorites.filterNot { it.key == current.key }
                    else listOf(current) + favorites
                saveFavorites(context, favorites)
            },
            onWatch = { showWatchInfo = true }
        )
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
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().background(CardColor, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, "Поиск", tint = Muted)
            Spacer(Modifier.width(10.dp))
            BasicTextField(value = search, onValueChange = { search = it; if (it.isNotBlank()) category = 0 }, modifier = Modifier.weight(1f), singleLine = true, textStyle = TextStyle(color = White, fontSize = 15.sp), cursorBrush = SolidColor(White), decorationBox = { inner -> if (search.isEmpty()) Text("Найти фильм или сериал…", color = Muted); inner() })
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
            movies.isEmpty() -> Text(if (category == 5) "В избранном пока ничего нет." else "Ничего не найдено.", color = Muted, modifier = Modifier.padding(12.dp))
            else -> LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 112.dp), modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(movies, key = { it.key }) { movie ->
                    Column(Modifier.clickable { selected = movie }.focusable()) {
                        AsyncImage(model = movie.poster, contentDescription = movie.title, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(CardColor), contentScale = ContentScale.Crop)
                        Text(movie.title, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
                        Text("★ " + String.format("%.1f", movie.rating) + "  •  " + movie.date.take(4), color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieDetailScreen(
    movie: Movie,
    details: MovieDetails?,
    loading: Boolean,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onWatch: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Background)) {
        val wide = maxWidth >= 700.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(if (wide) 390.dp else 330.dp)) {
                if (movie.backdrop.isNotBlank()) {
                    AsyncImage(model = movie.backdrop, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize().background(CardColor))
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color(0xAA000000), Background))))
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(Color(0x99000000), RoundedCornerShape(14.dp)).clickable(onClick = onBack).focusable(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ArrowBack, "Назад", tint = White)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("МЕДИАЦЕНТР", color = White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = if (wide) 32.dp else 18.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (movie.poster.isNotBlank()) {
                        AsyncImage(model = movie.poster, contentDescription = movie.title, modifier = Modifier.width(if (wide) 170.dp else 112.dp).height(if (wide) 255.dp else 168.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(movie.title, color = White, fontSize = if (wide) 30.sp else 22.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (details?.tagline?.isNotBlank() == true) Text(details.tagline, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        Text("★ " + String.format("%.1f", movie.rating) + "  •  " + movie.date.take(4) + "  •  " + if (movie.mediaType == "tv") "Сериал" else "Фильм", color = White, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = if (wide) 32.dp else 18.dp, vertical = 18.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    ActionButton("Смотреть", Icons.Rounded.PlayArrow, Modifier.weight(1f), true, onWatch)
                    ActionButton(if (isFavorite) "В избранном" else "Избранное", if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, Modifier.weight(1f), false, onFavorite)
                }
                Spacer(Modifier.height(20.dp))
                Text("О фильме", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(movie.overview.ifBlank { "Описание пока отсутствует." }, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(18.dp))
                if (loading) {
                    Text("Загружаем подробную информацию…", color = Muted)
                } else if (details != null) {
                    if (details.genres.isNotEmpty()) InfoLine("Жанры", details.genres.joinToString(" • "), Icons.Rounded.Category)
                    InfoLine("Оригинальное название", details.originalTitle, Icons.Rounded.Language)
                    InfoLine("Дата выхода", movie.date, Icons.Rounded.CalendarToday)
                    InfoLine("Статус", details.status, Icons.Rounded.Info)
                    InfoLine("Длительность", details.runtime, Icons.Rounded.AccessTime)
                    if (details.seasons.isNotBlank()) InfoLine("Сезоны и серии", details.seasons, Icons.Rounded.Theaters)
                    InfoLine("Страна", details.country, Icons.Rounded.Public)
                    InfoLine("Режиссёр", details.director, Icons.Rounded.Person)
                    InfoLine("Сценарий", details.writers, Icons.Rounded.Edit)
                    InfoLine("Продюсеры", details.producers, Icons.Rounded.Groups)
                    InfoLine("Создатели", details.creators, Icons.Rounded.Person)
                    InfoLine("Студии", details.companies, Icons.Rounded.Business)
                    Spacer(Modifier.height(18.dp))
                    if (details.cast.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Groups, contentDescription = null, tint = White, modifier = Modifier.size(21.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("В ролях", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(details.cast.joinToString("  •  "), color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text("Информация предоставлена TMDB.", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    if (value.isNotBlank()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.widthIn(min = 112.dp, max = 145.dp))
            Spacer(Modifier.width(8.dp))
            Text(value, color = White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    primary: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f, spring(stiffness = 500f), label = "actionScale")
    Row(
        modifier.heightIn(min = 50.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) White else CardColor)
            .border(1.dp, if (focused) White else Color(0xFF484848), RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable().padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (primary) Color.Black else White, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = if (primary) Color.Black else White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
