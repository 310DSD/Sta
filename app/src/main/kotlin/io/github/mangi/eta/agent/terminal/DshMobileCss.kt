package io.github.mangi.eta.agent.terminal

/**
 * DSH Web 前端的 Eta 移动壳样式与安装脚本。
 *
 * 样式只在 WebView 中注入；安装脚本会把同一份 CSS 追加到 DSH 前端构建产物，
 * 保证首次加载和后续覆盖安装使用同一套外壳。
 */
internal object DshMobileCss {
    val css: String = """
/* Eta mobile shell v5 - DSH
   Goals: match Eta quiet, card-based mobile look without touching DSH logic.
   Every selector below is either an existing DSH anchor or a hook created by
   Eta own mobile shell (#eta-mobile-bar / #eta-scrim). */

:root {
  --eta-topbar-h: 64px;
  --eta-ease: cubic-bezier(0.33, 1, 0.68, 1);
  --eta-ease-out: cubic-bezier(0.32, 0.72, 0, 1);
  --eta-spring: cubic-bezier(0.3, 1.12, 0.6, 1);
  --eta-spring-soft: cubic-bezier(0.34, 1.2, 0.64, 1);
  --eta-dur: 0.26s;
}

@media (max-width: 720px) {
  /* ---------- Design tokens (body carries the DSH alias variables) ---------- */
  body.eta-dsh-page {
    --eta-canvas: color-mix(in srgb, var(--dsw-alias-label-primary, #0f1115) 3%, var(--dsw-alias-bg-base, #fff));
    --eta-surface: var(--dsw-alias-bg-base, #fff);
    --eta-hairline: var(--dsw-alias-border-l2, #0000001a);
    --eta-hairline-strong: var(--dsw-alias-border-l3, #0000001f);
    --eta-muted: var(--dsw-alias-label-secondary, #61666b);
    --eta-faint: var(--dsw-alias-label-tertiary, #81858c);
    --eta-hover: var(--dsw-alias-interactive-bg-hover, #2631480f);
    --eta-active: var(--dsw-alias-interactive-bg-active, #2631481f);
    --eta-accent: var(--dsw-alias-link, #4176e6);
    --eta-on-accent: var(--dsw-alias-label-primary-foreground, #fff);
    --eta-card-shadow: 0 10px 30px rgb(0 0 0 / 7%), 0 1px 2px rgb(0 0 0 / 4%);
    -webkit-tap-highlight-color: transparent;
  }
  body.eta-dsh-page,
  .pI_x6G_frame,
  .pI_x6G_centerCol,
  .wSkVaW_root,
  .uV2eYG_card,
  .VOzbGW_panel {
    transition: background-color 0.3s ease, color 0.3s ease, border-color 0.3s ease;
  }
  body.eta-dsh-page :focus-visible {
    outline: 2px solid color-mix(in srgb, var(--eta-accent) 55%, transparent);
    outline-offset: 2px;
    border-radius: 8px;
  }
  body.eta-dsh-page ::selection {
    background: color-mix(in srgb, var(--eta-accent) 22%, transparent);
  }

  html.eta-dsh-document,
  body.eta-dsh-page {
    width: 100%;
    height: 100%;
    overflow: hidden !important;
    background: var(--eta-canvas);
  }
  body.eta-dsh-page { overscroll-behavior: none; }
  body.eta-dsh-page #root {
    height: 100dvh !important;
    margin-top: 0 !important;
  }
  /* Only reserve the app bar once the shell script actually inserted it; otherwise a
     failed injection would leave a blank strip at the top of DSH. */
  body.eta-has-bar #root {
    height: calc(100dvh - var(--eta-topbar-h)) !important;
    margin-top: var(--eta-topbar-h) !important;
  }

  /* ---------- Top bar ---------- */
  #eta-mobile-bar,
  #eta-scrim,
  .eta-icon,
  .eta-heading,
  .eta-title { box-sizing: border-box !important; }

  #eta-mobile-bar {
    position: fixed;
    z-index: 90;
    inset: 0 0 auto;
    height: var(--eta-topbar-h);
    padding: 0 8px;
    display: flex;
    align-items: center;
    gap: 2px;
    background: var(--eta-canvas);
    border-bottom: 0.5px solid var(--eta-hairline);
    color: var(--dsw-alias-label-primary, #0f1115);
    transition: background-color var(--eta-dur) ease, border-color var(--eta-dur) ease, opacity 0.2s ease;
    contain: layout paint style;
  }

  .eta-icon {
    width: 42px;
    height: 42px;
    flex: none;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 0;
    border: 0;
    border-radius: 14px;
    background: transparent;
    color: inherit;
    transition: background-color 0.2s ease, transform 0.2s var(--eta-spring), opacity 0.2s ease;
  }
  .eta-icon:active {
    background: var(--eta-active);
    transform: scale(0.94);
  }
  #eta-mobile-bar > .eta-icon,
  #eta-mobile-bar > .eta-dsh-action { align-self: center !important; }
  .eta-heading {
    align-self: center !important;
    align-items: stretch !important;
    overflow: hidden !important;
    text-align: left !important;
  }
  .eta-title, .eta-preset-line {
    width: 100% !important;
    max-width: 100% !important;
    text-align: left !important;
  }
  .eta-preset-line {
    min-width: 0;
    display: inline-flex;
    align-items: center;
    gap: 3px;
    color: var(--eta-faint);
    animation: eta-preset-in 0.24s var(--eta-ease-out) both;
  }
  .eta-preset-line[hidden] { display: none !important; }
  .eta-preset-icon { flex: none; width: 12px; height: 12px; }
  .eta-dsh-action {
    position: relative;
    color: var(--eta-muted);
    overflow: visible;
    transition: color 0.18s ease, background-color 0.2s ease, transform 0.2s var(--eta-spring);
  }
  .eta-dsh-action.eta-action-running {
    color: var(--dsw-alias-state-business-primary, #4d93f8);
  }
  .eta-dsh-action.eta-action-empty { color: var(--eta-faint); }
  .eta-badge {
    position: absolute;
    top: 3px;
    right: 2px;
    min-width: 14px;
    height: 14px;
    padding: 0 3px;
    border: 0.5px solid var(--eta-hairline);
    border-radius: 999px;
    background: var(--eta-surface);
    color: var(--eta-muted);
    font-size: 9px;
    line-height: 12px;
    font-variant-numeric: tabular-nums;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    box-sizing: border-box;
  }
  .eta-action-running .eta-badge {
    border-color: color-mix(in srgb, var(--dsw-alias-state-business-primary, #4d93f8) 30%, transparent);
    background: color-mix(in srgb, var(--dsw-alias-state-business-primary, #4d93f8) 12%, var(--eta-surface));
    color: var(--dsw-alias-state-business-primary, #4d93f8);
  }
  .eta-action-empty .eta-badge { opacity: 0.55; }

  .eta-heading {
    flex: 1;
    min-width: 0;
    padding: 0 4px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    transition: padding-right 0.2s var(--eta-ease-out);
  }
  body[data-ds-dark-theme] #eta-mobile-bar,
  body[data-ds-dark-theme] .eta-title {
    color: var(--dsw-alias-label-primary, #f2f3f5) !important;
  }
  body[data-ds-dark-theme] .eta-preset {
    color: var(--dsw-alias-label-tertiary, #a8adb7) !important;
  }
  body[data-ds-dark-theme] .wSkVaW_headerActions {
    color: var(--dsw-alias-label-secondary, #a8adb7) !important;
  }
  body[data-ds-dark-theme] .pI_x6G_frame {
    color-scheme: dark;
    color: var(--dsw-alias-label-primary, #f2f3f5);
  }
  body[data-ds-dark-theme] .pI_x6G_frame svg { color: inherit; }
  body[data-ds-dark-theme] #eta-mobile-bar .eta-icon {
    color: var(--dsw-alias-label-primary, #f2f3f5) !important;
  }
  body[data-ds-dark-theme] #eta-mobile-bar .eta-dsh-action {
    color: var(--dsw-alias-label-secondary, #a8adb7) !important;
  }
  body[data-ds-dark-theme] #eta-mobile-bar .eta-dsh-action.eta-action-running {
    color: var(--dsw-alias-state-business-primary, #7aaaff) !important;
  }
  .eta-title {
    display: block !important;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 15px;
    max-width: 100% !important;
    line-height: 22px;
    font-weight: 600;
    letter-spacing: 0.005em;
  }
  .eta-preset {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--eta-faint);
    font-size: 10.5px;
    line-height: 13px;
    font-weight: 500;
    animation: eta-preset-in 0.24s var(--eta-ease-out) both;
  }
  @keyframes eta-preset-in {
    from { opacity: 0; transform: translate3d(0, 2px, 0); }
    to { opacity: 1; transform: translate3d(0, 0, 0); }
  }
  .eta-preset[hidden] { display: none !important; }

  /* ---------- Drawer scrim ---------- */
  #eta-scrim {
    position: fixed;
    z-index: 80;
    inset: 0;
    background: rgb(0 0 0 / 36%);
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.34s var(--eta-ease);
  }
  body.eta-drawer-open #eta-scrim { opacity: 1; pointer-events: auto; }
  body.eta-drawer-open #eta-mobile-bar {
    opacity: 0;
    visibility: hidden;
    transition: opacity 0.2s ease, visibility 0s 0.2s;
  }

  /* ---------- Frame: desktop 3 columns -> one mobile column ---------- */
  .pI_x6G_frame {
    grid-template-columns: minmax(0, 1fr) !important;
    grid-template-rows: minmax(0, 1fr) auto !important;
    height: 100% !important;
    background: var(--eta-canvas) !important;
  }
  .pI_x6G_centerCol {
    grid-column: 1 / -1 !important;
    width: 100% !important;
    border: 0 !important;
    background: var(--eta-canvas) !important;
  }
  .pI_x6G_rightbarCol { grid-column: 1 / -1 !important; }
  /* Right panel: DSH computes a zero track on a phone, so present its already
     fullscreen mobile panel as a real overlay above the conversation. */
  .pI_x6G_rightbarCol {
    position: fixed !important;
    inset: var(--eta-topbar-h) 0 0 0 !important;
    z-index: 115 !important;
    width: 100vw !important;
    height: calc(100dvh - var(--eta-topbar-h)) !important;
    pointer-events: none !important;
    background: transparent !important;
    overflow: visible !important;
  }
  .pI_x6G_rightbarCol:has([data-sidebar-right-open]) {
    pointer-events: auto !important;
    background: var(--eta-surface) !important;
    animation: eta-right-in 0.3s var(--eta-ease-out) both;
  }
  .pI_x6G_rightbarCol:has([data-sidebar-right-open]) .OUqwTW_panel {
    pointer-events: auto !important;
    width: 100vw !important;
    max-width: 100vw !important;
  }
  .pI_x6G_rightbarCol:has([data-sidebar-right-open]) .OUqwTW_panelBody {
    padding-top: 4px !important;
  }
  @keyframes eta-right-in {
    from { opacity: 0; transform: translate3d(24px, 0, 0); }
    to { opacity: 1; transform: translate3d(0, 0, 0); }
  }

  /* ---------- Sidebar -> off-canvas drawer ---------- */
  .pI_x6G_sidebarCol {
    position: fixed !important;
    z-index: 120 !important;
    inset: 0 auto 0 0 !important;
    width: min(84vw, 320px) !important;
    min-width: 0 !important;
    max-width: none !important;
    background: var(--eta-surface) !important;
    border-right: 0.5px solid var(--eta-hairline) !important;
    border-radius: 0 22px 22px 0 !important;
    box-shadow: 0 18px 48px rgb(0 0 0 / 20%) !important;
    transform: translate3d(-100%, 0, 0) scale(0.99) !important;
    opacity: 0.55;
    transition: transform 0.42s var(--eta-ease-out), opacity 0.32s ease !important;
    will-change: transform, opacity;
    contain: layout paint style;
    overflow: hidden !important;
  }
  .pI_x6G_frame:not([data-sidebar-collapsed]) .pI_x6G_sidebarCol {
    transform: translate3d(0, 0, 0) scale(1) !important;
    opacity: 1;
  }
  .pI_x6G_sidebarCol .hHd-Xa_root {
    width: 100% !important;
    padding: 0 10px !important;
    background: transparent !important;
  }
  .pI_x6G_sidebarCol .hHd-Xa_collapsed { width: 100% !important; }

  .hHd-Xa_logoRow {
    height: 56px !important;
    margin-bottom: 8px !important;
    padding: 0 6px !important;
    border-bottom: 0.5px solid var(--eta-hairline) !important;
  }
  .hHd-Xa_newSession {
    height: 44px !important;
    justify-content: flex-start !important;
    padding: 0 12px !important;
    gap: 8px !important;
    border-radius: 14px !important;
    background: var(--eta-hover) !important;
    border: 0.5px solid var(--eta-hairline) !important;
    transition: background-color 0.2s ease, transform 0.2s var(--eta-spring-soft) !important;
  }
  .hHd-Xa_newSession:active { transform: scale(0.98); }
  .hHd-Xa_newSessionContent { justify-content: flex-start !important; }
  .hHd-Xa_newSessionShortcut { opacity: 0.5; }

  .hHd-Xa_panelRow {
    height: 40px !important;
    border-radius: 12px !important;
    transition: background-color 0.16s ease !important;
  }
  .hHd-Xa_panelRow:active,
  .hHd-Xa_panelRow:hover { background: var(--eta-hover) !important; }

  .YDXeBa_sessionRow {
    position: relative !important;
    height: 42px !important;
    margin: 1px 0 !important;
    padding: 0 10px 0 14px !important;
    border-radius: 12px !important;
    transition: background-color 0.2s ease, transform 0.2s var(--eta-spring-soft) !important;
  }
  .YDXeBa_selected::before {
    content: "";
    position: absolute;
    left: 4px;
    top: 11px;
    bottom: 11px;
    width: 3px;
    border-radius: 2px;
    background: var(--eta-accent);
  }
  .YDXeBa_sessionRow:active {
    transform: scale(0.985);
    background: var(--eta-active) !important;
  }
  .YDXeBa_selected {
    background: var(--eta-active) !important;
  }
  .YDXeBa_title { font-size: 14px !important; }
  .YDXeBa_time { font-size: 11px !important; color: var(--eta-faint) !important; }
  .YDXeBa_projectRow { border-radius: 12px !important; }

  /* ---------- Conversation ---------- */
  .wSkVaW_root {
    --dsh-conversation-column-width: 100vw !important;
    background: var(--eta-canvas) !important;
  }
  .wSkVaW_body,
  .wSkVaW_embeddedBody {
    --dsh-chat-content-width: calc(100vw - 28px) !important;
    --dsh-composer-card-max-width: calc(100vw - 28px) !important;
    --dsh-composer-side-clearance: 14px !important;
  }
  .wSkVaW_header { display: none !important; }
  .wSkVaW_scrollBody {
    margin-right: 0 !important;
    scrollbar-gutter: auto !important;
    overscroll-behavior: contain;
  }
  .wSkVaW_composerHero {
    width: 100% !important;
    padding: 0 14px calc(14px + env(safe-area-inset-bottom, 0px)) !important;
    gap: 12px !important;
  }
  .wSkVaW_heroWorkspaceRow { padding: 0 14px !important; }
  .pXSMma_workspace,
  .cubgiG_seat {
    height: 30px !important;
    padding: 0 10px !important;
    border-radius: 999px !important;
    background: color-mix(in srgb, var(--eta-surface) 72%, transparent) !important;
    border: 0.5px solid var(--eta-hairline) !important;
    transition: background-color 0.2s ease, transform 0.2s var(--eta-spring) !important;
  }
  .pXSMma_workspace:active,
  .cubgiG_seat:active { transform: scale(0.96); background: var(--eta-hover) !important; }

  .EvIC1a_column {
    max-width: 100% !important;
    font-size: 15px !important;
    line-height: 1.7 !important;
    letter-spacing: 0.004em;
  }
  .EvIC1a_flowItem {
    contain: layout style;
  }
  .EvIC1a_flowItem:last-child {
    animation: eta-rise 0.36s var(--eta-ease-out) both;
  }
  .EvIC1a_column pre {
    border-radius: 14px !important;
    border: 0.5px solid var(--eta-hairline) !important;
  }
  .EvIC1a_column :not(pre) > code {
    border-radius: 6px !important;
    padding: 1px 5px !important;
    background: color-mix(in srgb, var(--dsw-alias-label-primary) 6%, transparent) !important;
  }
  .xzv4MW_action {
    border-radius: 50% !important;
    transition: background-color 0.2s ease, transform 0.2s var(--eta-spring-soft) !important;
  }
  .xzv4MW_action:active { transform: scale(0.9); }

  /* User bubble: soft surface, generous radius, Eta-like rhythm. */
  .Sixlwa_userRow { justify-content: flex-end !important; }
  .Sixlwa_bubble {
    max-width: 84% !important;
    padding: 10px 14px !important;
    border-radius: 20px 20px 7px 20px !important;
    background: color-mix(in srgb, var(--dsw-alias-label-primary) 6%, var(--eta-surface)) !important;
    border: 0.5px solid var(--eta-hairline) !important;
    font-size: 15px !important;
    line-height: 1.6 !important;
  }
  .Sixlwa_turnErrorRow {
    display: flex !important;
    align-items: flex-start !important;
    gap: 10px !important;
    box-sizing: border-box !important;
    padding: 12px 14px !important;
    border-radius: 16px !important;
    background: color-mix(in srgb, #f25a5a 8%, var(--eta-surface)) !important;
    border: 0.5px solid color-mix(in srgb, #f25a5a 22%, transparent) !important;
  }
  .Sixlwa_turnErrorCopy { flex: 1 1 auto !important; min-width: 0 !important; }
  .Sixlwa_turnErrorMessage { font-size: 12.5px !important; line-height: 1.6 !important; }

  /* ---------- Composer ---------- */
  .uV2eYG_card {
    border-radius: 24px !important;
    background: var(--eta-surface) !important;
    border: 0.5px solid var(--eta-hairline-strong) !important;
    box-shadow: var(--eta-card-shadow) !important;
    transition: border-color 0.18s ease !important;
  }
  .uV2eYG_card:focus-within {
    border-color: var(--eta-hairline-strong) !important;
    outline: none !important;
    box-shadow: var(--eta-card-shadow) !important;
  }
  .uV2eYG_input {
    min-height: 64px !important;
    padding: 14px 16px 2px !important;
    font-size: 15px !important;
    line-height: 24px !important;
  }
  .uV2eYG_placeholder {
    min-height: 64px !important;
    padding: 14px 16px 2px !important;
    font-size: 15px !important;
    color: var(--eta-faint) !important;
  }
  .uV2eYG_dock { padding: 6px 10px 10px !important; gap: 4px !important; }
  .uV2eYG_add {
    width: 32px !important;
    height: 32px !important;
    border-radius: 50% !important;
    transition: background-color 0.2s ease, transform 0.2s var(--eta-spring-soft) !important;
  }
  .uV2eYG_add:active { background: var(--eta-active) !important; transform: scale(0.92); }
  .uV2eYG_primary {
    width: 36px !important;
    height: 36px !important;
    border-radius: 50% !important;
    background: var(--dsw-alias-button-primary-fill, #0f1115) !important;
    color: var(--eta-on-accent) !important;
    transition: transform 0.16s var(--eta-ease), opacity 0.16s ease !important;
  }
  .uV2eYG_primary:active { transform: scale(0.9); }

  /* ---------- Menus & popovers ---------- */
  ._list_4ub78_7,
  ._submenu_4ub78_8 {
    padding: 6px !important;
    border-radius: 16px !important;
    border: 0.5px solid var(--eta-hairline) !important;
    box-shadow: 0 18px 48px rgb(0 0 0 / 16%), 0 2px 8px rgb(0 0 0 / 6%) !important;
    animation: eta-pop 0.2s var(--eta-ease-out) both;
    transform-origin: top center;
  }
  ._item_4ub78_90 {
    border-radius: 10px !important;
    transition: background-color 0.14s ease !important;
  }
  ._item_4ub78_90:hover {
    background: var(--eta-hover) !important;
  }

  /* ---------- Settings / dialogs -> full-screen sheet ---------- */
  .VOzbGW_panel,
  .VOzbGW_panel * { box-sizing: border-box !important; }

  .VOzbGW_overlay {
    align-items: stretch !important;
    justify-content: stretch !important;
    padding: 0 !important;
    background: var(--eta-canvas) !important;
  }
  .VOzbGW_panel {
    position: relative !important;
    width: 100vw !important;
    max-width: none !important;
    height: 100dvh !important;
    max-height: none !important;
    border-radius: 0 !important;
    display: flex !important;
    flex-direction: column !important;
    overflow: hidden !important;
    background: var(--eta-canvas) !important;
    animation: eta-sheet-in 0.4s var(--eta-ease-out) both;
  }
  .VOzbGW_nav {
    width: 100% !important;
    height: auto !important;
    max-height: none !important;
    flex: none !important;
    flex-direction: column !important;
    padding: 0 !important;
    border-right: 0 !important;
    border-bottom: 0.5px solid var(--eta-hairline) !important;
    background: var(--eta-canvas) !important;
  }
  .VOzbGW_navTitle {
    display: block !important;
    padding: 14px 60px 8px 18px !important;
    font-size: 22px !important;
    line-height: 30px !important;
    font-weight: 600 !important;
  }
  .VOzbGW_navList {
    width: 100% !important;
    -webkit-mask-image: linear-gradient(to right, #000 0, #000 calc(100% - 24px), transparent 100%);
    mask-image: linear-gradient(to right, #000 0, #000 calc(100% - 24px), transparent 100%);
    flex-direction: row !important;
    flex-wrap: nowrap !important;
    gap: 8px !important;
    padding: 2px 14px 12px !important;
    overflow-x: auto !important;
    overflow-y: hidden !important;
    scrollbar-width: none !important;
    scroll-snap-type: x proximity;
    overscroll-behavior-x: contain;
  }
  .VOzbGW_navList::-webkit-scrollbar { display: none; }
  .VOzbGW_navCell {
    flex: 0 0 auto !important;
    width: auto !important;
    height: 36px !important;
    padding: 0 14px !important;
    gap: 7px !important;
    border-radius: 999px !important;
    white-space: nowrap !important;
    scroll-snap-align: start;
    background: var(--eta-surface) !important;
    border: 0.5px solid var(--eta-hairline) !important;
    transition: background-color 0.22s ease, color 0.22s ease, box-shadow 0.22s ease, transform 0.22s var(--eta-spring) !important;
  }
  .VOzbGW_navCell:active { transform: scale(0.96); }
  .VOzbGW_active {
    background: var(--dsw-alias-button-primary-fill, #0f1115) !important;
    color: var(--eta-on-accent) !important;
    border-color: transparent !important;
    box-shadow: 0 3px 10px rgb(0 0 0 / 14%) !important;
  }
  .VOzbGW_navLabel { width: auto !important; font-size: 13.5px !important; white-space: nowrap !important; }

  .VOzbGW_content {
    width: 100% !important;
    max-width: none !important;
    min-width: 0 !important;
    flex: 1 !important;
    min-height: 0 !important;
    display: flex !important;
    flex-direction: column !important;
    background: var(--eta-canvas) !important;
  }
  .VOzbGW_header {
    position: absolute !important;
    z-index: 3 !important;
    top: 10px !important;
    right: 10px !important;
    width: auto !important;
    min-height: 0 !important;
    height: auto !important;
    padding: 0 !important;
    border: 0 !important;
    background: transparent !important;
  }
  .VOzbGW_close {
    width: 34px !important;
    height: 34px !important;
    border-radius: 50% !important;
    background: var(--eta-hover) !important;
    transition: background-color 0.2s ease, transform 0.2s var(--eta-spring-soft) !important;
  }
  .VOzbGW_close:active { transform: scale(0.9); }

  .VOzbGW_options {
    width: 100% !important;
    max-width: none !important;
    flex: 1 !important;
    min-height: 0 !important;
    overflow-y: auto !important;
    padding: 6px 14px calc(36px + env(safe-area-inset-bottom, 0px)) !important;
    overscroll-behavior: contain;
  }
  .VOzbGW_options > div,
  .VOzbGW_options > div > div { width: 100% !important; min-width: 0 !important; }

  /* Setting rows: never let Chinese labels collapse into one-character columns. */
  .oY77xG_row,
  .hVGvvW_row,
  ._2XZxNq_row,
  .Pt1bsG_row,
  .nhfO0a_setting,
  .T1PP_q_row,
  .gi5gtW_row,
  .bVCLcG_row {
    display: flex !important;
    flex-direction: row !important;
    align-items: center !important;
    justify-content: space-between !important;
    gap: 14px !important;
    width: 100% !important;
    min-width: 0 !important;
    margin: 0 0 8px !important;
    padding: 12px 14px !important;
    border: 0.5px solid var(--eta-hairline) !important;
    border-radius: 16px !important;
    background: var(--eta-surface) !important;
    box-shadow: 0 1px 2px rgb(0 0 0 / 3%) !important;
    transition: border-color 0.16s ease, background-color 0.16s ease !important;
  }
  .VOzbGW_options [class*="_rowText"],
  .VOzbGW_options [class*="_settingText"],
  .oY77xG_rowText,
  ._2XZxNq_rowText,
  .T1PP_q_rowText,
  .nhfO0a_settingText {
    flex: 1 1 auto !important;
    width: auto !important;
    min-width: 0 !important;
    padding-right: 0 !important;
  }
  .oY77xG_selector,
  ._2XZxNq_selector,
  .T1PP_q_selector {
    flex: 0 0 auto !important;
    width: auto !important;
    min-width: 0 !important;
    white-space: nowrap !important;
  }
  ._8HJdBW_themeCube {
    padding: 14px 8px !important;
    font-size: 13px !important;
    white-space: nowrap !important;
    border-radius: 14px !important;
    transition: background-color 0.16s ease, border-color 0.16s ease, transform 0.16s var(--eta-ease) !important;
  }
  ._8HJdBW_themeCube:active { transform: scale(0.96); }
  ._01tsjW_group .bVCLcG_row {
    margin: 0 !important;
    padding: 0 !important;
    border: 0 !important;
    background: transparent !important;
  }
  ._8HJdBW_group,
  ._01tsjW_group {
    width: 100% !important;
    min-width: 0 !important;
    margin: 0 0 8px !important;
    padding: 12px 14px !important;
    border: 0.5px solid var(--eta-hairline) !important;
    border-radius: 16px !important;
    background: var(--eta-surface) !important;
  }
  ._8HJdBW_cubeRow {
    display: grid !important;
    grid-template-columns: repeat(3, minmax(0, 1fr)) !important;
    gap: 8px !important;
    width: 100% !important;
  }
  .yIbyla_row { padding: 12px 4px !important; color: var(--eta-faint) !important; }

  .wSkVaW_scrollBody::-webkit-scrollbar,
  .VOzbGW_options::-webkit-scrollbar {
    width: 4px;
  }
  .wSkVaW_scrollBody::-webkit-scrollbar-thumb,
  .VOzbGW_options::-webkit-scrollbar-thumb {
    border-radius: 999px;
    background: color-mix(in srgb, var(--dsw-alias-label-primary) 18%, transparent);
  }

  body.eta-drawer-open .hHd-Xa_newSession {
    animation: eta-slide-in 0.42s var(--eta-ease-out) both;
  }
  body.eta-drawer-open .hHd-Xa_panelList {
    animation: eta-slide-in 0.42s var(--eta-ease-out) 0.05s both;
  }

  @keyframes eta-slide-in {
    from { opacity: 0; transform: translate3d(-10px, 0, 0); }
    to { opacity: 1; transform: translate3d(0, 0, 0); }
  }
  @keyframes eta-rise {
    from { opacity: 0; transform: translate3d(0, 10px, 0); }
    to { opacity: 1; transform: translate3d(0, 0, 0); }
  }
  @keyframes eta-sheet-in {
    from { opacity: 0; transform: translate3d(0, 44px, 0) scale(0.985); }
    to { opacity: 1; transform: translate3d(0, 0, 0) scale(1); }
  }
  @keyframes eta-pop {
    from { opacity: 0; transform: translate3d(0, -6px, 0) scale(0.96); }
    to { opacity: 1; transform: translate3d(0, 0, 0) scale(1); }
  }
  /* ---------- DSH native header actions (preset / jobs / subagents) ---------- */
  body.eta-dsh-page {
    --dsw-menu-surface-fill: var(--eta-surface) !important;
    --dsw-specific-menu: var(--eta-surface) !important;
    --dsw-menu-backdrop-filter: none !important;
  }
  /* Eta previously hid the whole DSH header because the custom bar replaces its
     title. Keep the actions band so jobs, subagent catalog and the preset label
     remain reachable instead of becoming invisible mobile dead ends. */
  /* The native DSH header duplicated the Eta title row. Keep only its action
     components in the Eta top bar; title/crumbs/utilities stay hidden. */
  .wSkVaW_header { display: contents !important; }
  .wSkVaW_headerBlank { display: none !important; }
  .wSkVaW_titleRow,
  .wSkVaW_titleCluster { display: contents !important; }
  .wSkVaW_crumbs,
  .wSkVaW_headerUtilities,
  .wSkVaW_headerCorner,
  .wSkVaW_tabs { display: none !important; }
  .wSkVaW_headerActions {
    position: fixed !important;
    top: calc(env(safe-area-inset-top, 0px) + 10px) !important;
    right: calc(env(safe-area-inset-right, 0px) + 180px) !important;
    z-index: 95 !important;
    display: flex !important;
    flex: none !important;
    flex-wrap: nowrap !important;
    align-items: center !important;
    justify-content: flex-end !important;
    max-width: 130px !important;
    margin: 0 !important;
    gap: 2px !important;
    opacity: 0 !important;
    pointer-events: none !important;
  }
  .wSkVaW_headerActions .b06baG_label { display: none !important; }
  .wSkVaW_headerActions {
    animation: eta-header-actions-in 0.24s var(--eta-ease-out) both;
  }
  @keyframes eta-header-actions-in {
    from { opacity: 0; transform: translate3d(0, -3px, 0); }
    to { opacity: 1; transform: translate3d(0, 0, 0); }
  }
  .wSkVaW_headerActions .foD-wG_trigger,
  .wSkVaW_headerActions .oXE0lW_trigger,
  .wSkVaW_headerActions .oXE0lW_switcherTrigger {
    min-height: 30px !important;
  }
  .b06baG_label {
    display: inline-flex !important;
    max-width: 116px !important;
    height: 22px !important;
    padding: 0 6px !important;
    border: 0.5px solid var(--eta-hairline) !important;
    background: var(--eta-surface) !important;
    color: var(--eta-muted) !important;
    font-size: 11px !important;
    line-height: 20px !important;
  }
  /* The composer is a single contenteditable surface. Keep the focus ring on
     the outer card; the generic :focus-visible ring puts a stray blue line
     around the inner editor on mobile. */
  body.eta-dsh-page .uV2eYG_card :focus-visible,
  body.eta-dsh-page .uV2eYG_card:focus-visible,
  body.eta-dsh-page .uV2eYG_card:focus-within,
  body.eta-dsh-page [contenteditable="true"]:focus-visible {
    outline: none !important;
  }
  .uV2eYG_card { padding-bottom: 0 !important; }
  .uV2eYG_input,
  .uV2eYG_placeholder {
    min-height: 40px !important;
    height: auto !important;
    padding: 8px 14px 2px !important;
    line-height: 24px !important;
    box-sizing: border-box !important;
    outline: none !important;
  }
  .uV2eYG_placeholder {
    top: 8px !important;
    left: 14px !important;
    right: 14px !important;
  }
  .uV2eYG_dock { padding-bottom: 6px !important; }
  /* The sticky reasoning header was clipped to a square white block when
     expanded. Round it and let the surrounding row own the surface. */
  ._3GBCTG_root[data-expanded] [data-open] [data-disclosure-header],
  body.eta-dsh-page [data-disclosure-header] {
    background: transparent !important;
    border-radius: 12px !important;
  }
  ._3GBCTG_root[data-expanded] ._3GBCTG_row {
    border-radius: 12px !important;
    background: transparent !important;
  }
  /* Menus in the mobile shell are opaque surfaces: backdrop-filter is not
     reliable in every Android WebView, and a translucent surface reads as a
     broken dialog when the blur is absent. */
  ._surface_ri079_1 > ._material_ri079_21,
  .foD-wG_menu,
  .oXE0lW_menu,
  .Xt1eiG_panel {
    background: var(--eta-surface) !important;
    backdrop-filter: none !important;
  }
  .foD-wG_menu,
  .oXE0lW_menu {
    top: calc(var(--eta-topbar-h) + 4px) !important;
    left: 12px !important;
    right: 12px !important;
    width: auto !important;
    max-width: none !important;
    max-height: calc(100dvh - var(--eta-topbar-h) - 16px) !important;
  }
  /* DSH's model menu uses width:max-content; long model ids make it wider
     than the phone. Use the same full-width sheet language as Eta dialogs. */
  .wq12jW_menu {
    left: 12px !important;
    right: 12px !important;
    width: auto !important;
    min-width: 0 !important;
    max-width: none !important;
    max-height: calc(100dvh - var(--eta-topbar-h) - 96px) !important;
    border: 0.5px solid var(--eta-hairline) !important;
    border-radius: 18px !important;
    background: var(--eta-surface) !important;
    box-shadow: 0 18px 48px rgb(0 0 0 / 16%), 0 2px 8px rgb(0 0 0 / 6%) !important;
    backdrop-filter: none !important;
  }
  .wq12jW_option,
  .wq12jW_cell {
    border-radius: 10px !important;
  }
  .wq12jW_searchRow .wq12jW_search {
    border-radius: 10px !important;
    background: var(--eta-hover) !important;
  }

}

@media (max-width: 720px) and (prefers-reduced-motion: no-preference) {
  .Sixlwa_userRow {
    animation: eta-rise 0.28s var(--eta-ease-out) both;
  }
  ._3GBCTG_root[data-expanded] ._3GBCTG_thinkBody {
    animation: eta-rise 0.24s var(--eta-ease-out) both;
  }
}

@media (max-width: 720px) and (prefers-reduced-motion: reduce) {
  body.eta-dsh-page *,
  body.eta-dsh-page *::before,
  body.eta-dsh-page *::after {
    animation-duration: 0.001ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.001ms !important;
    scroll-behavior: auto !important;
  }
}
""".trimIndent()

val installScript: String =
        "for f in /usr/local/lib/node_modules/@deepseek-ai/dsh-web-frontend/dist/assets/index-*.css; do " +
            "[ -f \"${'$'}f\" ] || continue; " +
            "sed -i '/^\\/\\* eta-mobile/,${'$'}d' \"${'$'}f\"; " +
            "printf '%s\\n' '/* eta-mobile-start */' '$css' '/* eta-mobile-end */' >> \"${'$'}f\"; done"
}
