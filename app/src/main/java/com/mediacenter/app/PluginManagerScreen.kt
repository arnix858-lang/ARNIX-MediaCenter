package com.mediacenter.app

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray

private val PluginBg = Color(0xFF080808)
private val PluginCard = Color(0xFF191919)

private fun savedPluginUrls(context: android.content.Context): List<String> {
    val raw = context.getSharedPreferences("arnix_plugins", 0).getString("urls", "[]") ?: "[]"
    return try {
        val a = JSONArray(raw)
        (0 until a.length()).map { a.optString(it) }.filter { it.startsWith("https://") }
    } catch (_: Exception) { emptyList() }
}

private fun storePluginUrls(context: android.content.Context, urls: List<String>) {
    val a = JSONArray()
    urls.forEach { a.put(it) }
    context.getSharedPreferences("arnix_plugins", 0).edit().putString("urls", a.toString()).apply()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun PluginManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var urls by remember { mutableStateOf(savedPluginUrls(context)) }
    var input by remember { mutableStateOf("") }
    var runtimeKey by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Среда плагинов готова") }

    BackHandler { onBack() }

    Column(Modifier.fillMaxSize().background(PluginBg).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Назад", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("ПЛАГИНЫ", color = Color.White, fontSize = 21.sp)
                Text("Совместимая среда Lampa", color = Color.LightGray, fontSize = 12.sp)
            }
            IconButton(onClick = { runtimeKey++ }) { Icon(Icons.Rounded.Refresh, "Перезапустить", tint = Color.White) }
        }
        Spacer(Modifier.height(12.dp))
        Text("Добавить плагин по HTTPS-ссылке", color = Color.White, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().background(PluginCard, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp), singleLine = true,
                decorationBox = { inner -> if (input.isEmpty()) Text("https://.../plugin.js", color = Color.Gray); inner() })
            Text("Добавить", color = Color.White, modifier = Modifier.clickable {
                val url = input.trim()
                if (url.startsWith("https://") && url.endsWith(".js", true) && url !in urls) {
                    urls = urls + url
                    storePluginUrls(context, urls)
                    input = ""
                    runtimeKey++
                }
            }.padding(8.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("Установлено: ${urls.size}", color = Color.White, fontSize = 14.sp)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(urls) { url ->
                Row(Modifier.fillMaxWidth().background(PluginCard, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Extension, null, tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text(url, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { urls = urls - url; storePluginUrls(context, urls); runtimeKey++ }) {
                        Icon(Icons.Rounded.Delete, "Удалить", tint = Color.LightGray)
                    }
                }
            }
        }
        Text(status, color = Color.LightGray, fontSize = 11.sp)
        Text("Важно: поддержка API Lampa частичная. Плагины, которым нужны внутренние компоненты Lampa, DOM-интерфейс или закрытые методы, могут не запуститься. Потоки с DRM не поддерживаются.", color = Color.Gray, fontSize = 11.sp, lineHeight = 16.sp)
    }

    // Hidden WebView is only a JavaScript compatibility runtime; app screens and video playback stay native.
    key(runtimeKey) {
        AndroidView(
            modifier = Modifier.size(1.dp),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            return request.isForMainFrame && request.url.host != "lampa.mx"
                        }
                    }
                    val scriptUrls = JSONArray(urls).toString()
                    val html = """
                        <!doctype html><html><head><meta charset="utf-8">
                        <script>
                        window.Lampa = window.Lampa || {};
                        (function(L){
                          const store = {};
                          L.Storage = L.Storage || {get:(k,d)=>Object.prototype.hasOwnProperty.call(store,k)?store[k]:d,set:(k,v)=>{store[k]=v;},remove:(k)=>{delete store[k];}};
                          L.Listener = L.Listener || {send:()=>{},follow:()=>{},remove:()=>{}};
                          L.Utils = L.Utils || {protocol:location.protocol,decode:(v)=>decodeURIComponent(v||''),escape:(v)=>String(v||'')};
                          L.Template = L.Template || {get:()=>''};
                          L.Component = L.Component || {add:()=>{},get:()=>null};
                          L.Plugin = L.Plugin || {create:(name,init)=>{try{if(typeof init==='function')init();}catch(e){console.error('Plugin '+name,e);}}};
                          L.Reguest = L.Reguest || function(){this.silent=()=>this;this.timeout=()=>this;this.error=()=>this;this.success=()=>this;this.complete=()=>this;this.get=(url)=>fetch(url).then(r=>r.text());this.post=(url,data)=>fetch(url,{method:'POST',body:data}).then(r=>r.text());};
                          L.Manifest = L.Manifest || {name:'ARNIX MediaCenter',version:'1.0'};
                        })(window.Lampa);
                        window.addEventListener('error',e=>console.error('plugin error',e.message));
                        </script></head><body>
                        <script>
                          const pluginUrls = $scriptUrls;
                          pluginUrls.forEach((src) => {
                            const script = document.createElement('script');
                            script.src = src;
                            script.async = false;
                            script.onerror = () => console.error('Не удалось загрузить плагин: ' + src);
                            document.body.appendChild(script);
                          });
                        </script>
                        </body></html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://lampa.mx/", html, "text/html", "UTF-8", null)
                }
            }
        )
    }
}
