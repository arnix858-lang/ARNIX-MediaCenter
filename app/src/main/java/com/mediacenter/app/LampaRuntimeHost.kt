package com.mediacenter.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

private val RuntimeBg = Color(0xFF080808)

/**
 * Hosts a user-supplied, HTTPS-served Lampa runtime. This does not bundle Lampa
 * source code; it provides a constrained WebView host and an explicit native bridge.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun LampaRuntimeHost(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("arnix_sources", 0) }
    var runtimeUrl by remember { mutableStateOf(prefs.getString("lampa_runtime_url", "") ?: "") }
    var activeUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Укажите HTTPS-адрес экземпляра Lampa Runtime.") }
    var lastEvent by remember { mutableStateOf("События плагинов появятся здесь после подключения моста.") }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        if (webView?.canGoBack() == true) webView?.goBack() else onBack()
    }

    Column(Modifier.fillMaxSize().background(RuntimeBg).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Назад", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("LAMPA RUNTIME", color = Color.White, fontSize = 18.sp)
                Text("Внешний оригинальный runtime + мост ARNIX", color = Color.LightGray, fontSize = 11.sp)
            }
        }
        androidx.compose.foundation.text.BasicTextField(
            value = runtimeUrl, onValueChange = { runtimeUrl = it },
            modifier = Modifier.fillMaxWidth().background(Color(0xFF191919)).padding(12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
            singleLine = true
        )
        Text("Подключить runtime", color = Color.White, fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                .background(Color(0xFF303030)).clickable {
                    val candidate = runtimeUrl.trim()
                    val uri = runCatching { Uri.parse(candidate) }.getOrNull()
                    if (uri?.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) {
                        prefs.edit().putString("lampa_runtime_url", candidate).apply()
                        activeUrl = candidate
                        status = "Подключение к $candidate"
                    } else status = "Нужен корректный HTTPS URL без логина и пароля."
                }.padding(12.dp))
        Text(status, color = Color.LightGray, fontSize = 12.sp)
        Text(lastEvent, color = Color(0xFFB8B8B8), fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 6.dp))
        if (activeUrl.isNotBlank()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val uri = request.url
                                if (uri.scheme == "arnix") {
                                    val data = uri.getQueryParameter("data").orEmpty()
                                    runCatching {
                                        val event = JSONObject(data)
                                        val type = event.optString("type")
                                        if (type in setOf("ready", "search", "play", "catalog", "error")) {
                                            lastEvent = "Событие: $type\n" + event.toString().take(700)
                                        }
                                    }.onFailure { lastEvent = "Некорректное сообщение моста" }
                                    return true
                                }
                                if (uri.scheme != "https" || uri.host != Uri.parse(activeUrl).host) {
                                    status = "Переход заблокирован: разрешён только HTTPS-хост runtime."
                                    return true
                                }
                                return false
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                status = "Загружается оригинальный runtime…"
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                status = "Runtime загружен. Ожидание событий от подключённых плагинов."
                                val bridge = """
                                    (function(){
                                      if(window.ArnixNative) return;
                                      window.ArnixNative = {
                                        postMessage: function(message) {
                                          try {
                                            var data = typeof message === 'string' ? message : JSON.stringify(message);
                                            window.location.href = 'arnix://bridge?data=' + encodeURIComponent(data);
                                          } catch(e) {}
                                        }
                                      };
                                      window.dispatchEvent(new CustomEvent('arnixnative', {detail: window.ArnixNative}));
                                    })();
                                """.trimIndent()
                                view.evaluateJavascript(bridge, null)
                            }
                        }
                        loadUrl(activeUrl)
                        webView = this
                    }
                },
                update = { webView = it }
            )
        } else {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Оригинальный runtime не встроен в APK", color = Color.White, fontSize = 17.sp)
                Spacer(Modifier.height(8.dp))
                Text("Укажите адрес HTTPS-размещения Lampa, которым вы вправе пользоваться. После загрузки плагины выполняются внутри его настоящей среды; поддержка нативных действий ARNIX требует вызовов window.ArnixNative.postMessage(...).",
                    color = Color.LightGray, fontSize = 13.sp)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { webView?.stopLoading(); webView?.destroy(); webView = null }
    }
}
