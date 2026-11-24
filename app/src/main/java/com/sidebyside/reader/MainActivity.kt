package com.sidebyside.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.ConsoleMessage
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import com.sidebyside.reader.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SideBySideReader"
        // Official domain that Android treats as a secure origin for local assets
        private const val ASSET_DOMAIN = "appassets.androidplatform.net"
        private const val BASE_URL = "https://$ASSET_DOMAIN/assets/www/index.html"
    }

    private lateinit var binding: ActivityMainBinding
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var assetLoader: WebViewAssetLoader

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results: Array<Uri>? = if (result.resultCode == RESULT_OK && result.data != null) {
            val data = result.data
            when {
                data?.clipData != null -> {
                    val clip = data.clipData!!
                    Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
        } else null
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge black theme
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Serve assets under an https:// origin so the page is a secure context.
        // This unlocks IndexedDB reliability, blob URLs, and modern web APIs.
        assetLoader = WebViewAssetLoader.Builder()
            .setDomain(ASSET_DOMAIN)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        setupWebView()
        handleIncomingIntent(intent)
        loadApp()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun setupWebView() {
        val webView = binding.webView
        webView.setBackgroundColor(Color.BLACK)

        // Required on some OEMs so the WebView can debug / receive console
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportZoom(true)
        // builtInZoomControls must be true for pinch-to-zoom; hide the +/- buttons
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(false)
        try {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } catch (_: Exception) {}

        try {
            settings.setTextZoom(100)
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = false
        }

        // Extra file-access flags that some older WebViews still need
        try {
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true
        } catch (_: Exception) {}

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            // Return the asset-loader response, or null so WebView handles the request itself.
            // Do not call super.shouldInterceptRequest — it is overload-ambiguous in Kotlin.
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                // Keep local / asset navigation inside the WebView
                if (url.startsWith("https://$ASSET_DOMAIN/") ||
                    url.startsWith("file://") ||
                    url.startsWith("about:")
                ) {
                    return false
                }
                // External links open in the system browser
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Log.w(TAG, "Could not open external url: $url", e)
                }
                return true
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    Log.e(TAG, "Main frame error: ${error.description} code=${error.errorCode} url=${request.url}")
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "Page finished: $url")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage ?: return true
                val level = when (consoleMessage.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
                    ConsoleMessage.MessageLevel.WARNING -> Log.WARN
                    ConsoleMessage.MessageLevel.DEBUG -> Log.DEBUG
                    else -> Log.INFO
                }
                Log.println(
                    level,
                    TAG,
                    "JS ${consoleMessage.messageLevel()}: ${consoleMessage.message()} " +
                        "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                )
                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "File chooser failed", e)
                    this@MainActivity.filePathCallback = null
                    false
                }
            }
        }
    }

    /**
     * Detect a brand-new install (or reinstall after uninstall).
     * Android may restore WebView localStorage via backup; we compare
     * PackageInfo.firstInstallTime so every fresh install shows the setup popup.
     */
    private fun isFreshInstall(): Boolean {
        return try {
            val prefs = getSharedPreferences("sbs_install", MODE_PRIVATE)
            val info = packageManager.getPackageInfo(packageName, 0)
            val firstInstallTime = info.firstInstallTime
            val remembered = prefs.getLong("first_install_time", -1L)
            if (remembered != firstInstallTime) {
                prefs.edit().putLong("first_install_time", firstInstallTime).apply()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "isFreshInstall check failed", e)
            false
        }
    }

    private fun loadApp() {
        // Load via the asset loader https origin (secure context).
        // On a fresh install force the first-run setup dialog once.
        val url = if (isFreshInstall()) {
            "$BASE_URL?forceSetup=1"
        } else {
            BASE_URL
        }
        Log.d(TAG, "Loading $url")
        binding.webView.loadUrl(url)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    // Content URI from "Open with". The web app's file picker
                    // is the reliable path; we just ensure the UI is ready.
                    Log.d(TAG, "VIEW intent: $uri")
                }
            }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                Log.d(TAG, "SEND intent received")
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        try {
            binding.webView.apply {
                loadUrl("about:blank")
                stopLoading()
                webChromeClient = null
                destroy()
            }
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
