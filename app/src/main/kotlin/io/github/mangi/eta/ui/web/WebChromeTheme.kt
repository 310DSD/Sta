package io.github.mangi.eta.ui.web

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.Window
import android.webkit.WebView
import androidx.core.view.WindowCompat

private const val LIGHT_CHROME = 0xFFF7F6F1.toInt()
private const val DARK_CHROME = 0xFF121214.toInt()

internal fun defaultWebChromeBackground(context: Context): Int =
    if ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    ) {
        DARK_CHROME
    } else {
        LIGHT_CHROME
    }

/**
 * Eta 内置 WebView 的窗口外框：根容器、状态栏、导航栏和每个 WebView 的底色
 * 必须使用同一套主题色，否则暗色下会出现白色边缘或闪白。
 */
internal class WebChromeTheme(
    private val window: Window,
    private val root: View,
    private val webViews: () -> List<WebView>,
) {
    var backgroundColor: Int = LIGHT_CHROME
        private set

    fun apply(color: Int) {
        backgroundColor = color
        root.setBackgroundColor(color)
        webViews().forEach {
            it.setBackgroundColor(color)
            syncWebView(it)
        }
        // Android 15 edge-to-edge may ignore navigationBarColor; the window
        // surface still needs the same color behind gesture areas.
        window.setBackgroundDrawable(ColorDrawable(color))
        window.navigationBarColor = color
        window.statusBarColor = color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightNavigationBars = Color.luminance(color) > 0.5f
            isAppearanceLightStatusBars = Color.luminance(color) > 0.5f
        }
    }

    fun syncWebView(view: WebView) {
        val dark = Color.luminance(backgroundColor) < 0.5f
        view.evaluateJavascript(
            "window.__etaSetDarkMode && window.__etaSetDarkMode(" +
                if (dark) "true" else "false" +
                ");",
            null,
        )
    }
}
