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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BridgeModule {

    /**
     * JS glue injected into the WebView after EccoPath signals readiness via onBridgeReady().
     *
     * Uses .then()/.catch() instead of async/await because evaluateJavascript injects raw JS
     * and some Android WebView versions have inconsistent async function support in injected scripts.
     *
     * The glue adds a null-guard on window.__eccoFindBridge so early invocations produce an
     * Error result rather than a silent crash (Research Pitfall 3).
     */
    private const val BRIDGE_GLUE_JS = """
        window.startBridge = function(start, end) {
            if (typeof window.__eccoFindBridge !== 'function') {
                window.MeldBridge.createPlaylist(JSON.stringify({found: false, path: []}));
                return;
            }
            window.__eccoFindBridge(start, end, {
                onProgress: function(info) {
                    try { window.MeldBridge.onProgress(JSON.stringify(info)); } catch(e) {}
                }
            }).then(function(result) {
                window.MeldBridge.createPlaylist(JSON.stringify(result));
            }).catch(function(e) {
                window.MeldBridge.createPlaylist(JSON.stringify({found: false, path: []}));
            });
        };
    """

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

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()

        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false

            addJavascriptInterface(meldBridgeInterface, "MeldBridge")

            meldBridgeInterface.onReady = {
                Timber.d("BridgeModule: EccoPath ready, injecting JS glue")
                evaluateJavascript(BRIDGE_GLUE_JS, null)
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Timber.d("BridgeModule: EccoPath loaded at %s", url)
                }
            }

            loadUrl("https://appassets.androidplatform.net/assets/eccopath/index.html")
        }
    }
}
