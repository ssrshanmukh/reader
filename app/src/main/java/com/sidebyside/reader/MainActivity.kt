package com.sidebyside.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient.FileChooserParams
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sidebyside.reader.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

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
        // builtInZoomControls must be true for pinch-to-zoom to work in WebView;
        // displayZoomControls=false hides the +/- buttons so only pinch works.
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        // Prefer text reflow off so layout stays stable while zooming
        try {
            settings.setTextZoom(100)
        } catch (_: Exception) {}

        // Needed for File System Access API polyfills / modern APIs in WebView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = false
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                // Keep navigation inside the app for local assets
                if (url.startsWith("file://") || url.startsWith("about:")) {
                    return false
                }
                // External links open in browser
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {}
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
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
        val prefs = getSharedPreferences("sbs_install", MODE_PRIVATE)
        val info = packageManager.getPackageInfo(packageName, 0)
        val firstInstallTime = info.firstInstallTime
        val remembered = prefs.getLong("first_install_time", -1L)
        if (remembered != firstInstallTime) {
            prefs.edit().putLong("first_install_time", firstInstallTime).apply()
            return true
        }
        return false
    }

    private fun loadApp() {
        // On every new install / reinstall, force the first-run setup popup
        val url = if (isFreshInstall()) {
            "file:///android_asset/www/index.html?forceSetup=1"
        } else {
            "file:///android_asset/www/index.html"
        }
        binding.webView.loadUrl(url)
    }


    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    // Pass the content URI to the web app via query or JS if needed.
                    // For simplicity we reload with a hint; advanced sharing can be extended.
                    binding.webView.post {
                        // WebView can access content:// via file chooser; for VIEW we just open the app.
                        // User can use in-app import.
                    }
                }
            }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                // Shared files – user can import via the in-app UI
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
        binding.webView.destroy()
        super.onDestroy()
    }
}
