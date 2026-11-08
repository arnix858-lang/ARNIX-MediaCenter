package com.mediacenter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF080E19)
private val CardColor = Color(0xFF14243A)
private val Accent = Color(0xFF39A7FF)
private val Muted = Color(0xFF9AAAC0)

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
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Background).padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        val wide = maxWidth >= 600.dp
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("◈  МЕДИАЦЕНТР", color = Color.White,
                    fontSize = if (wide) 24.sp else 19.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp)
                Text("ANDROID", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("Добро пожаловать!", color = Color.White,
                fontSize = if (wide) 34.sp else 27.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Выберите раздел", color = Muted, fontSize = if (wide) 17.sp else 14.sp)
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
            Text("Медиацентр  •  Версия 1.0", color = Color(0xFF60728B), fontSize = 11.sp)
        }
    }
}

@Composable
private fun MenuCard(item: MenuItem, modifier: Modifier = Modifier, wide: Boolean) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.height(if (wide) 190.dp else 88.dp)
            .background(if (focused) Color(0xFF1B3B5F) else CardColor, RoundedCornerShape(20.dp))
            .border(if (focused) 2.dp else 1.dp,
                if (focused) Accent else Color(0xFF263B55), RoundedCornerShape(20.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(if (wide) 16.dp else 18.dp),
        horizontalArrangement = if (wide) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (wide) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(item.icon, contentDescription = item.title, tint = Accent, modifier = Modifier.size(46.dp))
                Spacer(Modifier.height(14.dp))
                Text(item.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(item.subtitle, color = Muted, fontSize = 12.sp)
            }
        } else {
            Icon(item.icon, contentDescription = item.title, tint = Accent, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(18.dp))
            Column {
                Text(item.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(item.subtitle, color = Muted, fontSize = 12.sp)
            }
        }
    }
}
