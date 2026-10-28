package io.github.mangi.eta.ui.web

/**
 * ZCode's desktop top overlay is painted underneath the Android WebView chrome
 * on some narrow devices. Its sidebar button therefore exists in the DOM but
 * never receives a touch. This bridge adds a small, explicit mobile hit target
 * that delegates to the real React button; it remains inside the page so it
 * follows every SPA route.
 */
internal object ZcodeMobileWebAssets {
    val sidebarBridge: String = """
        (function() {
          if (window.__etaZcodeSidebarFix) return;
          window.__etaZcodeSidebarFix = true;
          function install() {
            if (!document.body) return;
            if (document.getElementById('eta-zcode-sidebar-fix')) return;
            var button = document.createElement('button');
            button.id = 'eta-zcode-sidebar-fix';
            button.type = 'button';
            button.setAttribute('aria-label', 'Toggle sidebar');
            button.style.cssText = 'position:fixed;right:8px;top:54px;z-index:2147483647;' +
              'width:40px;height:40px;border-radius:10px;background:rgba(20,20,20,.88);' +
              'color:#fff;border:1px solid rgba(255,255,255,.18);display:flex;' +
              'align-items:center;justify-content:center;padding:0;';
            button.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" ' +
              'stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
              '<rect x="3" y="4" width="18" height="16" rx="2"></rect><path d="M9 4v16"></path></svg>';
            button.addEventListener('click', function() {
              var target = document.querySelector(
                '[data-testid="desktop-top-mobile-sidebar-toggle"]'
              );
              if (target) target.click();
            });
            document.body.appendChild(button);
          }
          install();
          new MutationObserver(install).observe(document.body, { childList: true });
        })();
    """.trimIndent()
}
