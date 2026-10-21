package io.github.mangi.eta.ui.web

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.github.mangi.eta.agent.terminal.LinuxPackageProfiles
import org.json.JSONObject

/**
 * 扩展 Agent 的内置移动浏览器。
 *
 * 支持多个独立 WebView 页面（顶部 tab 栏），并注入：
 * - 手机宽度下 Enter 插入换行（Shift/Ctrl/Cmd/Alt 语义保留）；
 * - DSH 的移动布局 CSS（主界面 + 设置弹窗）；
 * - 文件选择、返回栈和外部链接转系统浏览器。
 *
 * 只接受 loopback URL；外部链接交给系统浏览器，避免把 WebView 变成任意网页容器。
 */
internal class ExtensionAgentWebActivity : ComponentActivity() {
    private lateinit var tabStrip: LinearLayout
    private lateinit var contentHost: FrameLayout
    private val tabs = mutableListOf<Tab>()
    private var current: Tab? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private data class Tab(
        val webView: WebView,
        val button: LinearLayout,
        val titleView: TextView,
        var title: String = "",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra(EXTRA_URL)?.takeIf(::isLoopbackHttpUrl)
        if (target == null) {
            finish()
            return
        }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val tabScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabStrip, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
        }
        contentHost = FrameLayout(this)
        root.addView(
            tabScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)),
        )
        root.addView(contentHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime(),
            )
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        setContentView(root)
        ViewCompat.requestApplyInsets(root)

        onBackPressedDispatcher.addCallback(this) {
            val web = current?.webView
            when {
                web != null && web.canGoBack() -> web.goBack()
                tabs.size > 1 -> closeTab(requireNotNull(current))
                else -> finish()
            }
        }
        openTab(target)
    }

    private fun openTab(url: String) {
        val webView = createWebView()
        val titleView = TextView(this).apply {
            text = "页面 ${tabs.size + 1}"
            maxLines = 1
            setTextColor(Color.DKGRAY)
            textSize = 13f
            setPadding(dp(10), 0, dp(6), 0)
        }
        val close = TextView(this).apply {
            text = "×"
            textSize = 18f
            setTextColor(Color.DKGRAY)
            setPadding(dp(4), 0, dp(8), 0)
            setOnClickListener { closeTab(tabs.firstOrNull { it.webView === webView } ?: return@setOnClickListener) }
        }
        val button = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            addView(titleView)
            addView(close)
            setOnClickListener {
                tabs.firstOrNull { it.webView === webView }?.let(::selectTab)
            }
        }
        val tab = Tab(webView, button, titleView)
        tabs += tab
        tabStrip.addView(
            button,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply {
                marginEnd = dp(6)
            },
        )
        webView.loadUrl(url)
        selectTab(tab)
        updateAddButton()
    }

    private fun createWebView(): WebView = WebView(this).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            textZoom = 100
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (isLoopbackHttpUrl(url.toString())) return false
                if (url.scheme !in setOf("http", "https")) return true
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && isLoopbackHttpUrl(url)) {
                    view.evaluateJavascript(MOBILE_INPUT_BRIDGE, null)
                    view.evaluateJavascript(MOBILE_LAYOUT_BRIDGE, null)
                    updateTitle(view, url)
                }
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback
                return runCatching {
                    startActivityForResult(
                        Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        },
                        REQUEST_FILE_CHOOSER,
                    )
                    true
                }.getOrDefault(false)
            }
        }
    }

    private fun updateTitle(view: WebView, url: String) {
        val tab = tabs.firstOrNull { it.webView === view } ?: return
        val title = view.title?.trim().takeUnless { it.isNullOrBlank() }
            ?: runCatching { Uri.parse(url).host }.getOrNull()
            ?: "页面 ${tabs.indexOf(tab) + 1}"
        tab.title = title
        tab.titleView.text = title.take(18)
        updateAddButton()
    }

    private fun selectTab(tab: Tab) {
        current = tab
        contentHost.removeAllViews()
        (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
        contentHost.addView(tab.webView)
        tabs.forEach {
            it.button.setBackgroundColor(if (it === tab) 0x1A007AFF else Color.TRANSPARENT)
        }
    }

    private fun closeTab(tab: Tab) {
        tabs.remove(tab)
        tabStrip.removeView(tab.button)
        contentHost.removeView(tab.webView)
        tab.webView.stopLoading()
        tab.webView.destroy()
        if (current === tab) {
            current = null
            tabs.lastOrNull()?.let(::selectTab)
        }
        if (tabs.isEmpty()) finish() else updateAddButton()
    }

    private fun updateAddButton() {
        // Keep one compact “+” button at the end of the tab strip.
        val existing = tabStrip.findViewWithTag<View>(ADD_TAG)
        if (existing == null) {
            val add = TextView(this).apply {
                tag = ADD_TAG
                text = "+"
                textSize = 22f
                gravity = Gravity.CENTER
                setPadding(dp(12), 0, dp(12), 0)
                setOnClickListener { promptForUrl() }
            }
            tabStrip.addView(add, LinearLayout.LayoutParams(dp(44), dp(40)))
        } else {
            tabStrip.removeView(existing)
            tabStrip.addView(existing)
        }
    }

    private fun promptForUrl() {
        val input = EditText(this).apply {
            hint = "http://127.0.0.1:端口/"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText("http://127.0.0.1:")
        }
        AlertDialog.Builder(this)
            .setTitle("打开新页面")
            .setView(input)
            .setPositiveButton("打开") { _, _ ->
                val url = input.text.toString().trim()
                if (isLoopbackHttpUrl(url)) openTab(url) else {
                    Toast.makeText(this, "只允许本机 loopback 地址", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_FILE_CHOOSER) return
        val callback = fileChooserCallback
        fileChooserCallback = null
        if (callback == null) return
        if (resultCode != Activity.RESULT_OK) {
            callback.onReceiveValue(null)
            return
        }
        val uris = data?.clipData?.let { clip ->
            Array(clip.itemCount) { index -> clip.getItemAt(index).uri }
        } ?: data?.data?.let { arrayOf(it) }
        callback.onReceiveValue(uris)
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        tabs.forEach {
            it.webView.stopLoading()
            it.webView.destroy()
        }
        tabs.clear()
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_URL = "io.github.mangi.eta.extra.EXTENSION_AGENT_URL"
        private const val REQUEST_FILE_CHOOSER = 7401
        private const val ADD_TAG = "eta-webbrowser-add"

        private fun isLoopbackHttpUrl(value: String): Boolean {
            val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
            return uri.scheme == "http" &&
                (uri.host == "127.0.0.1" || uri.host == "localhost" || uri.host == "::1")
        }

        private val MOBILE_INPUT_BRIDGE = """
            (function() {
              if (window.__etaMobileInputInstalled) return;
              window.__etaMobileInputInstalled = true;
              var mobile = window.matchMedia('(pointer: coarse)').matches || window.innerWidth < 800;
              if (!mobile) return;
              document.addEventListener('keydown', function(event) {
                if (event.key !== 'Enter' || event.isComposing || event.keyCode === 229 ||
                    event.shiftKey || event.ctrlKey || event.metaKey || event.altKey) return;
                var target = event.target;
                if (!target || !(target.tagName === 'TEXTAREA' || target.isContentEditable)) return;
                event.preventDefault();
                event.stopImmediatePropagation();
                if (target.tagName === 'TEXTAREA') {
                  var start = target.selectionStart == null ? target.value.length : target.selectionStart;
                  var end = target.selectionEnd == null ? start : target.selectionEnd;
                  target.setRangeText('\n', start, end, 'end');
                  target.dispatchEvent(new Event('input', { bubbles: true }));
                } else {
                  target.dispatchEvent(new KeyboardEvent('keydown', {
                    key: 'Enter', code: 'Enter', keyCode: 13, which: 13,
                    shiftKey: true, bubbles: true, cancelable: true
                  }));
                }
              }, true);
            })();
        """.trimIndent()

        private val MOBILE_LAYOUT_BRIDGE: String by lazy {
            val css = JSONObject.quote(LinuxPackageProfiles.DSH_MOBILE_CSS)
            """
            (function() {
              if (document.getElementById('eta-mobile-webview')) return;
              var style = document.createElement('style');
              style.id = 'eta-mobile-webview';
              style.textContent = $css;
              (document.head || document.documentElement).appendChild(style);
            })();
            """.trimIndent()
        }
    }
}
