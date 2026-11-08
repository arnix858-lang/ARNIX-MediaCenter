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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Строгая чёрно-белая палитра
private val Background = Color(0xFF050505)
private val CardColor = Color(0xFF151515)
private val CardFocused = Color(0xFF292929)
private val White = Color(0xFFFFFFFF)
private val Muted = Color(0xFFB8B8B8)
private val BorderIdle = Color(0xFF3A3A3A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Background) {
                    HomeScreen()
                }
            }
        }
    }
}

private data class MenuItem(val title: String, val subtitle: String, val icon: ImageVector)

@Composable
private fun HomeScreen() {
    val items = listOf(
        MenuItem("ТВ", "Телеканалы", Icons.Rounded.Tv),
        MenuItem("Кинотеатр", "Фильмы и сериалы", Icons.Rounded.Movie),
        MenuItem("Настройки", "Параметры приложения", Icons.Rounded.Settings)
    )

    var showHeader by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showHeader = true }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Background)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        val wide = maxWidth >= 600.dp

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(
                visible = showHeader,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 3 })
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "◈  МЕДИАЦЕНТР",
                        color = White,
                        fontSize = if (wide) 24.sp else 19.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text("ANDROID", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = showHeader,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Добро пожаловать!",
                        color = White,
                        fontSize = if (wide) 34.sp else 27.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Выберите раздел",
                        color = Muted,
                        fontSize = if (wide) 17.sp else 14.sp
                    )
                }
            }

            Spacer(Modifier.height(if (wide) 38.dp else 26.dp))

            if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items.forEach { item -> MenuCard(item, Modifier.weight(1f), true) }
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items.forEach { item -> MenuCard(item, Modifier.fillMaxWidth(), false) }
                }
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(visible = showHeader, enter = fadeIn()) {
                Text("Медиацентр  •  Версия 1.0", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MenuCard(item: MenuItem, modifier: Modifier = Modifier, wide: Boolean) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else if (focused) 1.025f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "cardScale"
    )
    val textScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 550f),
        label = "textScale"
    )

    Row(
        modifier
            .height(if (wide) 190.dp else 88.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(
                color = if (focused) CardFocused else CardColor,
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) White else BorderIdle,
                shape = RoundedCornerShape(20.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { /* Навигацию по разделам подключим следующим этапом */ }
            )
            .focusable(),
        horizontalArrangement = if (wide) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (wide) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.title,
                    tint = White,
                    modifier = Modifier.size(46.dp).graphicsLayer {
                        scaleX = textScale
                        scaleY = textScale
                    }
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    item.title,
                    color = White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.graphicsLayer {
                        scaleX = textScale
                        scaleY = textScale
                    }
                )
                Spacer(Modifier.height(5.dp))
                Text(item.subtitle, color = Muted, fontSize = 12.sp)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.title,
                    tint = White,
                    modifier = Modifier.size(34.dp).graphicsLayer {
                        scaleX = textScale
                        scaleY = textScale
                    }
                )
                Spacer(Modifier.width(18.dp))
                Column {
                    Text(
                        item.title,
                        color = White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.graphicsLayer {
                            scaleX = textScale
                            scaleY = textScale
                        }
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(item.subtitle, color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}
