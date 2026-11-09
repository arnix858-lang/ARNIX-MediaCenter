package com.mediacenter.app

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Bitmap

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun LampaScreen(onBack: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("Lampa") }
    var loading by remember { mutableStateOf(true) }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onBack()
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF080808))) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                val view = webView
                if (view != null && view.canGoBack()) view.goBack() else onBack()
            }) { Icon(Icons.Rounded.ArrowBack, "Назад", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(pageTitle, color = Color.White, maxLines = 1)
                Text(if (loading) "Загрузка Lampa…" else "Lampa • веб-клиент", color = Color.LightGray, fontSize = androidx.compose.ui.unit.sp(11))
            }
            IconButton(onClick = { webView?.reload() }) {
                Icon(Icons.Rounded.Refresh, "Обновить", tint = Color.White)
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    settings.allowFileAccess = false
                    settings.allowContentAccess = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    settings.userAgentString = settings.userAgentString
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            loading = true
                            super.onPageStarted(view, url, favicon)
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                            pageTitle = view.title?.takeIf { it.isNotBlank() } ?: "Lampa"
                            super.onPageFinished(view, url)
                        }
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val scheme = request.url.scheme?.lowercase()
                            return scheme != "https" && scheme != "http"
                        }
                    }
                    loadUrl("https://lampa.mx/")
                }
            },
            update = { webView = it }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                webChromeClient = null
                webViewClient = WebViewClient()
                destroy()
            }
            webView = null
        }
    }
}
