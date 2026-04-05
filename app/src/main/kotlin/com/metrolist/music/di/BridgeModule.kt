/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.di

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.metrolist.music.bridge.MeldBridgeInterface
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import java.io.IOException
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BridgeModule {

    /**
     * JS glue injected on page load. Polls for __eccoFindBridge (set by React useEffect)
     * since hydration may not complete before the user taps Find Bridge.
     */
    private const val BRIDGE_GLUE_JS = """
        (function() {
            function doStartBridge(start, end) {
                window.__eccoFindBridge(start, end, {
                    onProgress: function(info) {
                        try { window.MeldBridge.onProgress(JSON.stringify(info)); } catch(e) {}
                    }
                }).then(function(result) {
                    window.MeldBridge.createPlaylist(JSON.stringify(result));
                }).catch(function(e) {
                    window.MeldBridge.createPlaylist(JSON.stringify({found: false, path: []}));
                });
            }

            window.startBridge = function(start, end) {
                if (typeof window.__eccoFindBridge === 'function') {
                    doStartBridge(start, end);
                    return;
                }
                var attempts = 0;
                var poll = setInterval(function() {
                    attempts++;
                    if (typeof window.__eccoFindBridge === 'function') {
                        clearInterval(poll);
                        doStartBridge(start, end);
                    } else if (attempts > 60) {
                        clearInterval(poll);
                        window.MeldBridge.createPlaylist(JSON.stringify({found: false, path: []}));
                    }
                }, 500);
            };
        })();
    """

    /**
     * Infer correct MIME type for asset files. Android's URLConnection.guessContentTypeFromName()
     * can return null or text/plain for .js files, which causes the WebView to silently refuse
     * to execute them as JavaScript.
     */
    private fun mimeTypeForPath(path: String): String = when {
        path.endsWith(".js") -> "application/javascript"
        path.endsWith(".mjs") -> "application/javascript"
        path.endsWith(".css") -> "text/css"
        path.endsWith(".html") -> "text/html"
        path.endsWith(".json") -> "application/json"
        path.endsWith(".woff2") -> "font/woff2"
        path.endsWith(".woff") -> "font/woff"
        path.endsWith(".svg") -> "image/svg+xml"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".ico") -> "image/x-icon"
        else -> "application/octet-stream"
    }

    @Singleton
    @Provides
    fun provideMeldBridgeInterface(): MeldBridgeInterface {
        return MeldBridgeInterface()
    }

    @Singleton
    @Provides
    @BridgeWebView
    fun provideBridgeWebView(
        @ApplicationContext context: Context,
        meldBridgeInterface: MeldBridgeInterface,
    ): WebView {
        Timber.d("BridgeModule: Creating singleton WebView")

        // Custom PathHandler for /_next/ that maps to eccopath/_next/ with correct MIME types
        val nextJsHandler = WebViewAssetLoader.PathHandler { path ->
            val assetPath = "eccopath/next/$path"
            try {
                val inputStream = context.assets.open(assetPath)
                val mimeType = mimeTypeForPath(path)
                Timber.d("BridgeModule: /_next/%s -> %s [%s]", path, assetPath, mimeType)
                WebResourceResponse(mimeType, "utf-8", inputStream)
            } catch (e: IOException) {
                Timber.w("BridgeModule: asset not found: %s", assetPath)
                null
            }
        }

        // Custom PathHandler for /assets/eccopath/ with correct MIME types
        val eccoPathHandler = WebViewAssetLoader.PathHandler { path ->
            val assetPath = "eccopath/$path"
            try {
                val inputStream = context.assets.open(assetPath)
                val mimeType = mimeTypeForPath(path)
                WebResourceResponse(mimeType, "utf-8", inputStream)
            } catch (e: IOException) {
                Timber.w("BridgeModule: asset not found: %s", assetPath)
                null
            }
        }

        // Registration order matters — /_next/ MUST be before /assets/ so it matches first
        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/_next/", nextJsHandler)
            .addPathHandler("/assets/eccopath/", eccoPathHandler)
            .build()

        // Enable remote debugging so we can use chrome://inspect
        WebView.setWebContentsDebuggingEnabled(true)

        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false

            addJavascriptInterface(meldBridgeInterface, "MeldBridge")

            meldBridgeInterface.onReady = {
                Timber.d("BridgeModule: EccoPath onBridgeReady signal received")
            }

            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                    msg?.let {
                        Timber.tag("EccoPathJS").d("[%s:%d] %s", it.sourceId(), it.lineNumber(), it.message())
                    }
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?,
                ) {
                    Timber.e("BridgeModule: load error [%d] %s for %s",
                        error?.errorCode, error?.description, request?.url)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Timber.d("BridgeModule: EccoPath loaded at %s, injecting JS glue", url)
                    view?.evaluateJavascript(BRIDGE_GLUE_JS, null)
                }
            }

            loadUrl("https://appassets.androidplatform.net/assets/eccopath/index.html")
        }
    }
}
