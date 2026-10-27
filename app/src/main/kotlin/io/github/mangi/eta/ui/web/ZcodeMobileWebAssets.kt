package io.github.mangi.eta.ui.web

import org.json.JSONObject

/** ZCode Web 的手机壳：只适配布局与交互，不修改 ZCode 的会话逻辑。 */
internal object ZcodeMobileWebAssets {
    val layoutBridge: String by lazy {
        val css = JSONObject.quote(CSS)
        """
        (function() {
          if (document.getElementById('eta-zcode-mobile')) return;
          var style = document.createElement('style');
          style.id = 'eta-zcode-mobile';
          style.textContent = $css;
          (document.head || document.documentElement).appendChild(style);
        })();
        """.trimIndent()
    }

    val shellScript: String = """
        (function() {
          if (window.__etaZcodeMobileInstalled) return;
          window.__etaZcodeMobileInstalled = true;

          function closeSidebar() {
            document.body.classList.remove('zcode-sidebar-open');
          }
          function openSidebar() {
            document.body.classList.add('zcode-sidebar-open');
          }
          function ensureChrome() {
            var header = document.querySelector('header[data-testid="workspace-header"]');
            if (!header) return false;
            document.body.classList.add('zcode-mobile');
            if (!document.getElementById('zcode-scrim')) {
              var scrim = document.createElement('div');
              scrim.id = 'zcode-scrim';
              scrim.addEventListener('click', closeSidebar);
              document.body.appendChild(scrim);
            }
            if (!document.getElementById('zcode-menu-button')) {
              var button = document.createElement('button');
              button.id = 'zcode-menu-button';
              button.type = 'button';
              button.className = 'zcode-menu-button';
              button.setAttribute('aria-label', '打开侧栏');
              button.innerHTML = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>';
              button.addEventListener('click', function() {
                document.body.classList.toggle('zcode-sidebar-open');
              });
              header.prepend(button);
            }
            return true;
          }

          document.addEventListener('keydown', function(event) {
            if (event.key === 'Escape') closeSidebar();
          }, true);
          document.addEventListener('click', function(event) {
            if (!document.body.classList.contains('zcode-sidebar-open')) return;
            if (!event.target || !event.target.closest) return;
            if (event.target.closest('#sidebar')) {
              window.setTimeout(closeSidebar, 160);
            }
          }, true);
          window.addEventListener('resize', function() {
            if (window.innerWidth > 767) closeSidebar();
          }, { passive: true });

          if (ensureChrome()) return;
          var attempts = 0;
          var timer = window.setInterval(function() {
            attempts += 1;
            if (ensureChrome() || attempts > 120) window.clearInterval(timer);
          }, 500);
        })();
    """.trimIndent()

    private val CSS = """
@media (max-width: 767px) {
  html,
  body,
  #root {
    width: 100% !important;
    max-width: 100% !important;
    overflow: hidden !important;
    overscroll-behavior: none;
  }
  body {
    -webkit-text-size-adjust: 100%;
  }
  body.zcode-mobile [data-workspace-shell="true"] {
    width: 100% !important;
    min-width: 0 !important;
    overflow: hidden !important;
  }
  body.zcode-mobile #content {
    width: 100% !important;
    min-width: 0 !important;
    padding: 0 !important;
  }
  body.zcode-mobile #sidebar {
    position: fixed !important;
    top: 0 !important;
    bottom: 0 !important;
    left: 0 !important;
    width: min(86vw, 340px) !important;
    max-width: none !important;
    height: 100dvh !important;
    z-index: 80 !important;
    transform: translate3d(-102%, 0, 0) !important;
    opacity: 1 !important;
    visibility: visible !important;
    pointer-events: auto !important;
    background: var(--color-sidebar, var(--color-background)) !important;
    border-right: 1px solid var(--color-border) !important;
    box-shadow: 18px 0 48px rgb(0 0 0 / 18%);
    transition: transform 0.28s cubic-bezier(0.32, 0.72, 0, 1) !important;
    will-change: transform;
  }
  body.zcode-mobile.zcode-sidebar-open #sidebar {
    transform: translate3d(0, 0, 0) !important;
  }
  body.zcode-mobile #sidebar > aside {
    height: 100% !important;
  }
  body.zcode-mobile #zcode-scrim {
    position: fixed;
    inset: 0;
    z-index: 70;
    background: rgb(0 0 0 / 38%);
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.22s ease;
  }
  body.zcode-mobile.zcode-sidebar-open #zcode-scrim {
    opacity: 1;
    pointer-events: auto;
  }
  body.zcode-mobile header[data-testid="workspace-header"] {
    min-height: 52px !important;
    height: 52px !important;
    padding: 0 !important;
    background: var(--color-header, var(--color-background)) !important;
    border-bottom-color: var(--color-border) !important;
  }
  body.zcode-mobile header[data-testid="workspace-header"] > div {
    height: 52px !important;
    min-width: 0 !important;
    padding: 4px 6px !important;
    gap: 2px !important;
  }
  body.zcode-mobile .zcode-menu-button {
    display: inline-flex !important;
    align-items: center;
    justify-content: center;
    flex: none;
    width: 36px;
    height: 36px;
    margin-right: 2px;
    border: 0;
    border-radius: 10px;
    color: var(--color-foreground);
    background: transparent;
    cursor: pointer;
    -webkit-tap-highlight-color: transparent;
  }
  body.zcode-mobile .zcode-menu-button:active {
    background: var(--color-surface-hover);
    transform: scale(0.94);
  }
  body.zcode-mobile [data-testid="workspace-title"] {
    max-width: 100% !important;
    overflow: hidden !important;
    text-overflow: ellipsis !important;
    white-space: nowrap !important;
    font-size: 14px !important;
  }
  body.zcode-mobile header[data-testid="workspace-header"] [data-testid="workspace-help-menu-trigger"],
  body.zcode-mobile header[data-testid="workspace-header"] [data-testid="workspace-more-button"] {
    display: none !important;
  }
  body.zcode-mobile [data-workspace-conversation-frame="true"] {
    border-radius: 0 !important;
    border: 0 !important;
    min-width: 0 !important;
  }
  body.zcode-mobile [data-workspace-side-frame="true"],
  body.zcode-mobile [data-workspace-terminal-frame="true"] {
    position: fixed !important;
    inset: 0 !important;
    z-index: 90 !important;
    width: 100vw !important;
    max-width: 100vw !important;
    height: 100dvh !important;
    max-height: 100dvh !important;
    border-radius: 0 !important;
    border: 0 !important;
    background: var(--color-background) !important;
  }
  body.zcode-mobile [data-composer-layout] {
    min-width: 0 !important;
  }
  body.zcode-mobile [data-testid="v4-composer-input"],
  body.zcode-mobile [contenteditable="true"],
  body.zcode-mobile textarea {
    font-size: 16px !important;
    line-height: 1.5 !important;
    max-height: 36dvh !important;
    overflow-y: auto !important;
  }
  body.zcode-mobile .chat-composer-input-surface {
    border-radius: 20px !important;
    padding-bottom: env(safe-area-inset-bottom, 0px) !important;
  }
  body.zcode-mobile [data-testid="chat-model-select-trigger"] {
    max-width: 46vw !important;
    min-width: 0 !important;
    overflow: hidden !important;
    text-overflow: ellipsis !important;
    white-space: nowrap !important;
  }
  body.zcode-mobile [data-testid="chat-input"] {
    scroll-margin-bottom: 12px;
  }
  body.zcode-mobile pre,
  body.zcode-mobile table {
    max-width: 100% !important;
    overflow-x: auto !important;
  }
  body.zcode-mobile [role="dialog"] {
    width: calc(100vw - 24px) !important;
    max-width: calc(100vw - 24px) !important;
    max-height: calc(100dvh - 24px) !important;
    border-radius: 18px !important;
  }
  body.zcode-mobile [data-slot="dropdown-menu-content"]:has([data-composer-model-list]),
  body.zcode-mobile [data-slot="dropdown-menu-content"]:has([data-composer-provider-list]) {
    width: calc(100vw - 24px) !important;
    max-width: calc(100vw - 24px) !important;
    left: 12px !important;
    right: 12px !important;
  }
}

@media (max-width: 767px) and (prefers-reduced-motion: reduce) {
  body.zcode-mobile #sidebar,
  body.zcode-mobile #zcode-scrim {
    transition-duration: 0.001ms !important;
  }
}
""".trimIndent()
}
