package io.github.mangi.eta.ui.web

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import io.github.mangi.eta.agent.terminal.LinuxPackageProfiles
import io.github.mangi.eta.agent.terminal.terminalEnvironment
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.repository.AppearanceSettingsRepository
import io.github.mangi.eta.data.repository.LinuxEnvironmentSettingsRepository
import io.github.mangi.eta.ui.MainActivity
import io.github.mangi.eta.ui.app.AgentAppTheme
import io.github.mangi.eta.ui.app.message
import io.github.mangi.eta.ui.screens.terminal.LinuxFileManagerActivity
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 扩展 Agent 的内置移动浏览器。
 *
 * 支持多个独立 WebView 页面（顶部 tab 栏），并注入：
 * - 手机宽度下 Enter 插入换行（Shift/Ctrl/Cmd/Alt 语义保留）；
 * - DSH 的移动布局 CSS 与顶栏/抽屉脚本（主界面 + 设置弹窗）；
 * - 文件选择、返回栈和外部链接转系统浏览器。
 *
 * 只接受 loopback URL；外部链接交给系统浏览器，避免把 WebView 变成任意网页容器。
 */
internal class ExtensionAgentWebActivity : ComponentActivity() {
    private lateinit var contentHost: FrameLayout
    private val tabs = mutableListOf<Tab>()
    private val tabUi = mutableStateListOf<ExtensionAgentTabUi>()
    private var selectedTabId by mutableStateOf<Long?>(null)
    private var newPageDialog by mutableStateOf(false)
    private var nextTabId = 1L
    private var current: Tab? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private data class Tab(
        val id: Long,
        val webView: WebView,
        var title: String = "",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra(EXTRA_URL)?.takeIf(::isLoopbackHttpUrl)
        if (target == null) {
            finish()
            return
        }

        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) WebView.setWebContentsDebuggingEnabled(true)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tabBar = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val appearance by AppearanceSettingsRepository.settingsFlow()
                    .collectAsState(initial = AppearanceSettings())
                AgentAppTheme(appearance = appearance, applyInterfaceScale = false) {
                    ExtensionAgentTabBar(
                        tabs = tabUi,
                        selectedId = selectedTabId,
                        onSelect = { id -> tabs.firstOrNull { it.id == id }?.let(::selectTab) },
                        onClose = { id -> tabs.firstOrNull { it.id == id }?.let(::closeTab) },
                        onAdd = { newPageDialog = true },
                        onSyncProviders = {
                            OpenCodeProviderDialog.show(this@ExtensionAgentWebActivity) {
                                current?.webView?.reload()
                            }
                        },
                        onReload = { current?.webView?.reload() },
                    )
                    NewPageDialog(
                        show = newPageDialog,
                        onDismiss = { newPageDialog = false },
                        onOpenAgents = {
                            newPageDialog = false
                            openAgents()
                        },
                        onOpenFileManager = {
                            newPageDialog = false
                            openFileManager()
                        },
                        onOpenUrl = { url ->
                            if (isLoopbackHttpUrl(url)) {
                                newPageDialog = false
                                openTab(url)
                            } else {
                                Toast.makeText(
                                    this@ExtensionAgentWebActivity,
                                    getString(R.string.webview_loopback_only),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                }
            }
        }
        contentHost = FrameLayout(this)
        root.addView(
            tabBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)),
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
        val title = "页面 ${tabs.size + 1}"
        val tab = Tab(id = nextTabId++, webView = webView, title = title)
        tabs += tab
        tabUi += ExtensionAgentTabUi(tab.id, title)
        webView.loadUrl(url)
        selectTab(tab)
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

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Register the Enter capture listener before the web app's own handlers,
                // otherwise Lexical/OpenCode can submit before we can turn Enter into a newline.
                if (url != null && isLoopbackHttpUrl(url)) {
                    view.evaluateJavascript(MOBILE_INPUT_BRIDGE, null)
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && isLoopbackHttpUrl(url)) {
                    view.evaluateJavascript(MOBILE_INPUT_BRIDGE, null)
                    view.evaluateJavascript(MOBILE_LAYOUT_BRIDGE, null)
                    view.evaluateJavascript(OPENCODE_LAYOUT_BRIDGE, null)
                    view.evaluateJavascript(DSH_MOBILE_SHELL_JS, null)
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
        val index = tabUi.indexOfFirst { it.id == tab.id }
        if (index >= 0) tabUi[index] = tabUi[index].copy(title = title)
    }

    private fun selectTab(tab: Tab) {
        current = tab
        selectedTabId = tab.id
        contentHost.removeAllViews()
        (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
        contentHost.addView(tab.webView)
    }

    private fun closeTab(tab: Tab) {
        tabs.remove(tab)
        tabUi.removeAll { it.id == tab.id }
        contentHost.removeView(tab.webView)
        tab.webView.stopLoading()
        tab.webView.destroy()
        if (current === tab) {
            current = null
            tabs.lastOrNull()?.let(::selectTab)
        }
        if (tabs.isEmpty()) finish()
    }

    private fun openFileManager() {
        val distribution = LinuxEnvironmentSettingsRepository.current(this)
        startActivity(
            Intent(this, LinuxFileManagerActivity::class.java)
                .putExtra(LinuxFileManagerActivity.EXTRA_DISTRIBUTION, distribution.wireName),
        )
    }

    private fun openAgents() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_AGENTS),
        )
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

        private fun isLoopbackHttpUrl(value: String): Boolean {
            val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
            return uri.scheme == "http" &&
                (uri.host == "127.0.0.1" || uri.host == "localhost" || uri.host == "::1")
        }

        private val DSH_MOBILE_SHELL_JS = """
(function () {
  function install() {
  if (document.getElementById('eta-mobile-bar')) return true;
  var frame = document.querySelector('.pI_x6G_frame');
  if (!frame) return false;

  var bar = document.createElement('header');
  bar.id = 'eta-mobile-bar';
  bar.innerHTML =
    '<button type="button" class="eta-icon" id="eta-menu" aria-label="打开菜单">' +
      '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>' +
    '</button>' +
    '<div class="eta-heading"><span class="eta-title">DeepSeek Harness</span></div>' +
    '<button type="button" class="eta-icon" id="eta-new" aria-label="新建会话">' +
      '<svg width="21" height="21" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>' +
    '</button>' +
    '<button type="button" class="eta-icon" id="eta-panel" aria-label="打开右侧边栏">' +
      '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="3.5" y="4.5" width="17" height="15" rx="2.5" stroke="currentColor" stroke-width="1.4"/><path d="M15.5 4.5v15" stroke="currentColor" stroke-width="1.4"/></svg>' +
    '</button>';
  document.body.appendChild(bar);
  document.body.classList.add('eta-has-bar');

  var scrim = document.createElement('div');
  scrim.id = 'eta-scrim';
  document.body.appendChild(scrim);

  function isDrawerOpen() {
    var f = document.querySelector('.pI_x6G_frame');
    return !!f && !f.hasAttribute('data-sidebar-collapsed');
  }
  function toggleDrawer(open) {
    var f = document.querySelector('.pI_x6G_frame');
    if (!f) return;
    var currentlyOpen = !f.hasAttribute('data-sidebar-collapsed');
    if (open === true && currentlyOpen) return;
    if (open === false && !currentlyOpen) return;
    var toggle = document.querySelector('.hHd-Xa_toggle');
    if (toggle) toggle.click();
  }
  document.getElementById('eta-menu').addEventListener('click', function () { toggleDrawer(); });
  scrim.addEventListener('click', function () { toggleDrawer(false); });
  document.getElementById('eta-new').addEventListener('click', function () {
    var b = document.querySelector('.hHd-Xa_newSession');
    if (b) b.click();
  });
  document.getElementById('eta-panel').addEventListener('click', function () {
    var b = document.querySelector('.wSkVaW_headerCorner button') ||
            document.querySelector('[aria-label="打开右侧边栏"]');
    if (b) b.click();
  });

  function syncDrawer() {
    document.body.classList.toggle('eta-drawer-open', isDrawerOpen());
  }
  new MutationObserver(syncDrawer).observe(frame, {
    attributes: true,
    attributeFilter: ['data-sidebar-collapsed']
  });
  syncDrawer();

  var titleEl = bar.querySelector('.eta-title');
  function syncTitle() {
    var value = document.title || 'DeepSeek Harness';
    var dash = value.indexOf(' — ');
    titleEl.textContent = dash > 0 ? value.slice(0, dash) : 'DeepSeek Harness';
  }
  var titleNode = document.querySelector('title');
  if (titleNode) new MutationObserver(syncTitle).observe(titleNode, { childList: true });
  syncTitle();
  return true;
  }

  if (install()) return;
  var attempts = 0;
  var timer = setInterval(function () {
    attempts += 1;
    if (install() || attempts > 120) clearInterval(timer);
  }, 250);
})();
        """.trimIndent()

        private val MOBILE_INPUT_BRIDGE = """
            (function() {
              if (window.__etaMobileInputInstalled) return;
              window.__etaMobileInputInstalled = true;
              var mobile = window.matchMedia('(pointer: coarse)').matches || window.innerWidth < 800;
              if (!mobile) return;
              window.addEventListener('keydown', function(event) {
                if (event.key !== 'Enter' || event.isComposing || event.keyCode === 229 ||
                    event.shiftKey || event.ctrlKey || event.metaKey || event.altKey) return;
                var target = event.target;
                if (!target || !(target.tagName === 'TEXTAREA' || target.isContentEditable)) return;
                event.preventDefault();
                event.stopImmediatePropagation();
                if (target.tagName === 'TEXTAREA') {
                  var start = target.selectionStart == null ? target.value.length : target.selectionStart;
                  var end = target.selectionEnd == null ? start : target.selectionEnd;
                  target.setRangeText(String.fromCharCode(10), start, end, 'end');
                  target.dispatchEvent(new Event('input', { bubbles: true }));
                } else if (target.closest('[data-lexical-editor="true"], .uV2eYG_input')) {
                  // Lexical (DSH) ignores execCommand; drive its beforeinput path instead.
                  var lexical = target.closest('[data-lexical-editor="true"], .uV2eYG_input');
                  lexical.dispatchEvent(new InputEvent('beforeinput', {
                    bubbles: true, cancelable: true, inputType: 'insertLineBreak'
                  }));
                } else if (!document.execCommand('insertLineBreak')) {
                  document.execCommand('insertText', false, String.fromCharCode(10));
                }
              }, true);
            })();
        """.trimIndent()

        private val OPENCODE_LAYOUT_BRIDGE = """
            (function() {
              if (document.getElementById('eta-opencode-webview')) return;
              var style = document.createElement('style');
              style.id = 'eta-opencode-webview';
              style.textContent = '@media (max-width:767px){' +
                '#root:not(:has(.pI_x6G_frame)){margin-top:0!important}' +
                'header[data-slot=titlebar-v2]{height:36px!important;padding-top:0!important;padding-bottom:0!important}' +
                'main[data-component=connect-server]{padding-top:16px!important}' +
                '.server-connect-content{margin-top:0!important;margin-bottom:0!important}' +
                '}';
              (document.head || document.documentElement).appendChild(style);
            })();
        """.trimIndent()

        private val MOBILE_LAYOUT_BRIDGE: String by lazy {
            val css = JSONObject.quote(LinuxPackageProfiles.DSH_MOBILE_CSS)
            """
            (function() {
              if (document.getElementById('eta-mobile-webview')) return;
              function install() {
                if (!document.querySelector('.pI_x6G_frame')) return false;
                var style = document.createElement('style');
                style.id = 'eta-mobile-webview';
                style.textContent = $css;
                (document.head || document.documentElement).appendChild(style);
                return true;
              }
              if (install()) return;
              var attempts = 0;
              var timer = setInterval(function() {
                attempts += 1;
                if (install() || attempts > 120) clearInterval(timer);
              }, 250);
            })();
            """.trimIndent()
        }
    }
}
