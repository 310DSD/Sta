package io.github.mangi.eta.ui.web

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import io.github.mangi.eta.agent.terminal.LinuxPackageProfiles
import org.json.JSONObject

/**
 * 扩展 Agent 的内置移动浏览器。
 *
 * 系统浏览器无法注入移动端输入行为，而 DSH/OpenCode 的桌面编辑器把裸 Enter 当成发送。
 * 这里用最小 WebView 承载 loopback Web UI，并在页面加载后注入：
 *  - 手机宽度下 Enter 插入换行（Shift/Ctrl/Cmd/Alt 语义保留）；
 *  - textarea 继续可编辑，Lexical contenteditable 用合成 Shift+Enter 触发其原生换行命令。
 *
 * 只接受 loopback URL；外部链接交给系统浏览器，避免把 WebView 变成任意网页容器。
 */
internal class ExtensionAgentWebActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra(EXTRA_URL)?.takeIf(::isLoopbackHttpUrl)
        if (target == null) {
            finish()
            return
        }
        webView = WebView(this).apply {
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
        setContentView(webView)
        onBackPressedDispatcher.addCallback(this) {
            if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else finish()
        }
        webView.loadUrl(target)
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
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "io.github.mangi.eta.extra.EXTENSION_AGENT_URL"
        private const val REQUEST_FILE_CHOOSER = 7401

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
