package io.github.mangi.eta.agent.terminal

import android.content.Context
import io.github.mangi.eta.core.AndroidAgentLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

internal enum class PackageProfileInstallStage {
    CHECKING,
    DOWNLOADING,
    INSTALLING,
    COMPLETE,
}

internal data class PackageProfileInstallProgress(
    val stage: PackageProfileInstallStage,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
)

internal sealed interface PackageProfileInstallResult {
    data object AlreadyReady : PackageProfileInstallResult
    data object EnvironmentNotReady : PackageProfileInstallResult

    /** 依赖的 profile 尚未安装，按依赖链先装它。 */
    data class DependencyMissing(val profileId: String) : PackageProfileInstallResult
    data object Installed : PackageProfileInstallResult
    data class Failed(val stage: PackageProfileInstallStage) : PackageProfileInstallResult
}

internal data class LinuxPackageSpec(
    val packages: List<String> = emptyList(),
    val managedTool: ManagedLinuxTool? = null,
    val setupScript: String? = null,
    /** Optional smoke check run after setup, before the ready marker is written. */
    val verifyCommand: String? = null,
)

internal data class LinuxPackageProfile(
    val id: String,
    val markerName: String,
    val revision: Int,
    val specs: Map<LinuxDistribution, LinuxPackageSpec>,
    /** 安装前必须就绪的前置 profile。 */
    val dependsOn: LinuxPackageProfile? = null,
    /** npm/apt 安装与校验的最长等待时间；DSH 的 610 个包需要更长窗口。 */
    val installTimeoutSeconds: Long = 1800L,
) {
    fun spec(distribution: LinuxDistribution): LinuxPackageSpec = requireNotNull(specs[distribution])
}

internal object LinuxPackageProfiles {
    val PYTHON = LinuxPackageProfile(
        id = "python",
        markerName = AlpineEnvironmentPaths.PYTHON_TOOLS_MARKER,
        revision = AlpineEnvironmentPaths.PYTHON_TOOLS_REVISION,
        specs = mapOf(
            LinuxDistribution.ALPINE to LinuxPackageSpec(
                managedTool = ManagedLinuxTool.UV,
                setupScript = """
                    UV_PYTHON_INSTALL_DIR=/opt/eta/python UV_PYTHON_BIN_DIR=/usr/local/bin UV_PYTHON_INSTALL_BIN=1 uv python install --default --force
                """.trimIndent(),
            ),
            LinuxDistribution.DEBIAN to LinuxPackageSpec(
                managedTool = ManagedLinuxTool.UV,
                setupScript = """
                    UV_PYTHON_INSTALL_DIR=/opt/eta/python UV_PYTHON_BIN_DIR=/usr/local/bin UV_PYTHON_INSTALL_BIN=1 uv python install --default --force
                """.trimIndent(),
            ),
        ),
    )
    val NODE = LinuxPackageProfile(
        id = "node",
        markerName = AlpineEnvironmentPaths.NODE_TOOLS_MARKER,
        revision = AlpineEnvironmentPaths.NODE_TOOLS_REVISION,
        specs = mapOf(
            LinuxDistribution.ALPINE to LinuxPackageSpec(
                packages = listOf("nodejs-current", "npm"),
            ),
            LinuxDistribution.DEBIAN to LinuxPackageSpec(
                // Node 官方 arm64 二进制链接 libatomic.so.1，归档安装不含系统依赖，需补装。
                packages = listOf("libatomic1"),
                managedTool = ManagedLinuxTool.NODE,
            ),
        ),
    )
    val SSH = LinuxPackageProfile(
        id = "ssh",
        markerName = AlpineEnvironmentPaths.SSH_TOOLS_MARKER,
        revision = AlpineEnvironmentPaths.SSH_TOOLS_REVISION,
        specs = mapOf(
            LinuxDistribution.ALPINE to LinuxPackageSpec(
                packages = listOf("openssh"),
                setupScript = "ssh-keygen -A >/dev/null 2>&1 || true",
            ),
            LinuxDistribution.DEBIAN to LinuxPackageSpec(
                packages = listOf("openssh-client", "openssh-server"),
                setupScript = "ssh-keygen -A >/dev/null 2>&1 || true",
            ),
        ),
    )

    /**
     * 扩展 Agent 使用 npm 分发，运行在 Node profile 之上。固定已验证的版本，避免
     * 开发者预览/CLI 参数变化导致启动命令失效；`--prefix /usr/local` 让命令进入 PATH 首位。
     * 国内镜像优先，官方 registry 兜底。
     */
    private const val KIMI_INSTALL_SCRIPT =
        "npm install -g --prefix /usr/local --registry=https://registry.npmmirror.com " +
            "@moonshot-ai/kimi-code@latest || " +
            "npm install -g --prefix /usr/local @moonshot-ai/kimi-code@latest"

    private const val OPENCODE_INSTALL_SCRIPT =
        "npm install -g --prefix /usr/local --include=optional --registry=https://registry.npmmirror.com " +
            "@opencode/cli@2.1.2 || " +
            "npm install -g --prefix /usr/local --include=optional @opencode/cli@2.1.2"

    internal val DSH_MOBILE_CSS = """
/* Eta mobile shell v4 - DSH
   Goals: match Eta quiet, card-based mobile look without touching DSH logic.
   Every selector below is either an existing DSH anchor or a hook created by
   Eta own mobile shell (#eta-mobile-bar / #eta-scrim). */

:root {
  --eta-topbar-h: calc(56px + env(safe-area-inset-top, 0px));
  --eta-ease: cubic-bezier(0.22, 1, 0.36, 1);
  --eta-ease-out: cubic-bezier(0.16, 1, 0.3, 1);
  --eta-spring: cubic-bezier(0.34, 1.28, 0.64, 1);
  --eta-dur: 0.24s;
}

@media (max-width: 720px) {
  /* ---------- Design tokens (body carries the DSH alias variables) ---------- */
  body:has(.pI_x6G_frame) {
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
  body:has(.pI_x6G_frame),
  .pI_x6G_frame,
  .pI_x6G_centerCol,
  .wSkVaW_root,
  .uV2eYG_card,
  .VOzbGW_panel {
    transition: background-color 0.3s ease, color 0.3s ease, border-color 0.3s ease;
  }
  body:has(.pI_x6G_frame) :focus-visible {
    outline: 2px solid color-mix(in srgb, var(--eta-accent) 55%, transparent);
    outline-offset: 2px;
    border-radius: 8px;
  }
  body:has(.pI_x6G_frame) ::selection {
    background: color-mix(in srgb, var(--eta-accent) 22%, transparent);
  }

  html:has(.pI_x6G_frame),
  body:has(.pI_x6G_frame) {
    width: 100%;
    height: 100%;
    overflow: hidden !important;
    background: var(--eta-canvas);
  }
  body:has(.pI_x6G_frame) { overscroll-behavior: none; }
  #root:has(.pI_x6G_frame) {
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
    padding: env(safe-area-inset-top, 0px) 8px 0;
    display: flex;
    align-items: center;
    gap: 2px;
    background: var(--eta-canvas);
    border-bottom: 0.5px solid var(--eta-hairline);
    color: var(--dsw-alias-label-primary, #0f1115);
    transition: background-color var(--eta-dur) ease, border-color var(--eta-dur) ease;
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
    transition: background-color 0.16s ease, transform 0.16s var(--eta-spring), opacity 0.16s ease;
  }
  .eta-icon:active {
    background: var(--eta-active);
    transform: scale(0.9);
  }

  .eta-heading {
    flex: 1;
    min-width: 0;
    padding: 0 4px;
    display: flex;
    flex-direction: column;
    justify-content: center;
  }
  .eta-title {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 16px;
    line-height: 22px;
    font-weight: 600;
    letter-spacing: 0.005em;
  }

  /* ---------- Drawer scrim ---------- */
  #eta-scrim {
    position: fixed;
    z-index: 80;
    inset: 0;
    background: rgb(0 0 0 / 36%);
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.28s ease;
  }
  body.eta-drawer-open #eta-scrim { opacity: 1; pointer-events: auto; }
  body.eta-drawer-open #eta-mobile-bar { visibility: hidden; }

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

  /* ---------- Sidebar -> off-canvas drawer ---------- */
  .pI_x6G_sidebarCol {
    position: fixed !important;
    z-index: 120 !important;
    inset: 0 auto 0 0 !important;
    width: min(84vw, 320px) !important;
    min-width: 0 !important;
    max-width: none !important;
    padding-top: env(safe-area-inset-top, 0px) !important;
    background: var(--eta-surface) !important;
    border-right: 0.5px solid var(--eta-hairline) !important;
    border-radius: 0 22px 22px 0 !important;
    box-shadow: 0 18px 48px rgb(0 0 0 / 20%) !important;
    transform: translate3d(-102%, 0, 0) !important;
    transition: transform 0.34s var(--eta-ease-out) !important;
    will-change: transform;
    contain: layout paint style;
    overflow: hidden !important;
  }
  .pI_x6G_frame:not([data-sidebar-collapsed]) .pI_x6G_sidebarCol {
    transform: translate3d(0, 0, 0) !important;
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
    transition: background-color 0.16s ease, transform 0.16s var(--eta-ease) !important;
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
    transition: background-color 0.16s ease, transform 0.16s var(--eta-ease) !important;
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
    transition: background-color 0.16s ease, transform 0.16s var(--eta-spring) !important;
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
    animation: eta-rise 0.28s var(--eta-ease) both;
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
    transition: background-color 0.16s ease, transform 0.16s var(--eta-ease) !important;
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
    border-color: color-mix(in srgb, var(--dsw-alias-label-primary) 20%, var(--eta-hairline-strong)) !important;
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
    transition: background-color 0.16s ease, transform 0.16s var(--eta-ease) !important;
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
    animation: eta-pop 0.16s var(--eta-ease) both;
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
    padding-top: env(safe-area-inset-top, 0px) !important;
    border-radius: 0 !important;
    display: flex !important;
    flex-direction: column !important;
    overflow: hidden !important;
    background: var(--eta-canvas) !important;
    animation: eta-sheet-in 0.32s var(--eta-ease-out) both;
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
    transition: background-color 0.16s ease, color 0.16s ease, box-shadow 0.16s ease, transform 0.16s var(--eta-spring) !important;
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
    top: calc(env(safe-area-inset-top, 0px) + 10px) !important;
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
    transition: background-color 0.16s ease, transform 0.16s var(--eta-ease) !important;
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
    animation: eta-slide-in 0.34s var(--eta-ease-out) both;
  }
  body.eta-drawer-open .hHd-Xa_panelList {
    animation: eta-slide-in 0.34s var(--eta-ease-out) 0.03s both;
  }

  @keyframes eta-slide-in {
    from { opacity: 0; transform: translate3d(-10px, 0, 0); }
    to { opacity: 1; transform: translate3d(0, 0, 0); }
  }
  @keyframes eta-rise {
    from { opacity: 0; transform: translate3d(0, 6px, 0); }
    to { opacity: 1; transform: translate3d(0, 0, 0); }
  }
  @keyframes eta-sheet-in {
    from { opacity: 0; transform: translate3d(0, 26px, 0); }
    to { opacity: 1; transform: translate3d(0, 0, 0); }
  }
  @keyframes eta-pop {
    from { opacity: 0; transform: scale(0.97); }
    to { opacity: 1; transform: scale(1); }
  }
}

@media (max-width: 720px) and (prefers-reduced-motion: reduce) {
  body:has(.pI_x6G_frame) *,
  body:has(.pI_x6G_frame) *::before,
  body:has(.pI_x6G_frame) *::after {
    animation-duration: 0.001ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.001ms !important;
    scroll-behavior: auto !important;
  }
}
""".trimIndent()

    internal val DSH_MOBILE_CSS_INSTALL =
        "for f in /usr/local/lib/node_modules/@deepseek-ai/dsh-web-frontend/dist/assets/index-*.css; do " +
            "[ -f \"${'$'}f\" ] || continue; " +
            "sed -i '/^\\/\\* eta-mobile/,${'$'}d' \"${'$'}f\"; " +
            "printf '%s\\n' '/* eta-mobile-start */' '$DSH_MOBILE_CSS' '/* eta-mobile-end */' >> \"${'$'}f\"; done"

    private val DSH_INSTALL_SCRIPT =
        "(npm install -g --prefix /usr/local --include=optional --registry=https://registry.npmmirror.com " +
            "@deepseek-ai/dsh@0.2.1-rc.2 || " +
            "npm install -g --prefix /usr/local --include=optional @deepseek-ai/dsh@0.2.1-rc.2) && " +
            DSH_MOBILE_CSS_INSTALL

    val OPENCODE = LinuxPackageProfile(
        id = "opencode",
        markerName = AlpineEnvironmentPaths.OPENCODE_TOOLS_MARKER,
        revision = AlpineEnvironmentPaths.OPENCODE_TOOLS_REVISION,
        dependsOn = NODE,
        specs = mapOf(
            LinuxDistribution.ALPINE to LinuxPackageSpec(
                setupScript = OPENCODE_INSTALL_SCRIPT,
                verifyCommand = "command -v opencode >/dev/null && opencode --version >/dev/null",
            ),
            LinuxDistribution.DEBIAN to LinuxPackageSpec(
                setupScript = OPENCODE_INSTALL_SCRIPT,
                verifyCommand = "command -v opencode >/dev/null && opencode --version >/dev/null",
            ),
        ),
    )

    val DSH = LinuxPackageProfile(
        id = "dsh",
        markerName = AlpineEnvironmentPaths.DSH_TOOLS_MARKER,
        revision = AlpineEnvironmentPaths.DSH_TOOLS_REVISION,
        dependsOn = NODE,
        installTimeoutSeconds = 3600L,
        specs = mapOf(
            LinuxDistribution.ALPINE to LinuxPackageSpec(
                setupScript = DSH_INSTALL_SCRIPT,
                verifyCommand = "command -v dsh >/dev/null && dsh --version >/dev/null",
            ),
            LinuxDistribution.DEBIAN to LinuxPackageSpec(
                setupScript = DSH_INSTALL_SCRIPT,
                verifyCommand = "command -v dsh >/dev/null && dsh --version >/dev/null",
            ),
        ),
    )
    val KIMI = LinuxPackageProfile(
        id = "kimi",
        markerName = AlpineEnvironmentPaths.KIMI_TOOLS_MARKER,
        revision = AlpineEnvironmentPaths.KIMI_TOOLS_REVISION,
        dependsOn = NODE,
        specs = mapOf(
            LinuxDistribution.ALPINE to LinuxPackageSpec(
                setupScript = KIMI_INSTALL_SCRIPT,
                verifyCommand = "command -v kimi >/dev/null && kimi --version >/dev/null",
            ),
            LinuxDistribution.DEBIAN to LinuxPackageSpec(
                setupScript = KIMI_INSTALL_SCRIPT,
                verifyCommand = "command -v kimi >/dev/null && kimi --version >/dev/null",
            ),
        ),
    )
    val ALL = listOf(PYTHON, NODE, SSH, OPENCODE, DSH, KIMI)
}

internal fun linuxPackageProfileReady(rootfs: File, profile: LinuxPackageProfile): Boolean {
    val marker = File(rootfs, profile.markerName)
    if (!marker.isFile) return false
    return marker.useLines { lines ->
        lines.any { line -> line.trim() == "profile=${profile.revision}" }
    }
}

/** 为当前选中的发行版按需安装单个工具 profile；成功后只写对应完成标记。 */
internal class LinuxPackageProfileInstaller(
    private val context: Context,
    private val distribution: LinuxDistribution,
    private val backend: LinuxExecutionBackend,
    private val profile: LinuxPackageProfile,
) {
    private val rootfs = LinuxEnvironmentPaths.rootfsDir(context, distribution, backend)
    private val managedToolInstaller = PinnedLinuxToolInstaller(context)

    fun isReady(): Boolean = linuxPackageProfileReady(rootfs, profile)

    suspend fun install(
        onProgress: suspend (PackageProfileInstallProgress) -> Unit = {},
    ): PackageProfileInstallResult {
        installMutex.lock()
        return try {
            installLocked(onProgress)
        } finally {
            installMutex.unlock()
        }
    }

    private suspend fun installLocked(
        onProgress: suspend (PackageProfileInstallProgress) -> Unit,
    ): PackageProfileInstallResult = withContext(Dispatchers.IO) {
        if (isReady()) return@withContext PackageProfileInstallResult.AlreadyReady
        onProgress(PackageProfileInstallProgress(PackageProfileInstallStage.CHECKING))
        if (!LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath) ||
            !File(rootfs, AlpineEnvironmentPaths.COMMON_TOOLS_MARKER).isFile
        ) {
            return@withContext PackageProfileInstallResult.EnvironmentNotReady
        }
        profile.dependsOn?.let { dependency ->
            if (!linuxPackageProfileReady(rootfs, dependency)) {
                return@withContext PackageProfileInstallResult.DependencyMissing(dependency.id)
            }
        }

        val spec = profile.spec(distribution)
        spec.managedTool?.let { tool ->
            val installed = managedToolInstaller.install(
                tool = tool,
                distribution = distribution,
                rootfs = rootfs,
            ) { downloadedBytes, totalBytes ->
                onProgress(
                    PackageProfileInstallProgress(
                        stage = PackageProfileInstallStage.DOWNLOADING,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                    ),
                )
            }
            if (!installed) {
                return@withContext PackageProfileInstallResult.Failed(
                    PackageProfileInstallStage.DOWNLOADING,
                )
            }
        }

        val packageHelper = when (distribution) {
            LinuxDistribution.ALPINE -> "/usr/local/bin/eta-apk"
            LinuxDistribution.DEBIAN -> "/usr/local/bin/eta-apt"
        }
        onProgress(PackageProfileInstallProgress(PackageProfileInstallStage.INSTALLING))
        if (spec.packages.isNotEmpty()) {
            val installResult = InstallerShellRunner.run(
                command = "$packageHelper install ${spec.packages.joinToString(" ")}",
                timeoutSeconds = profile.installTimeoutSeconds,
                environment = distribution.terminalEnvironment,
                linuxRootfsPath = rootfs.absolutePath,
            )
            if (installResult.exitCode != 0) {
                return@withContext PackageProfileInstallResult.Failed(
                    PackageProfileInstallStage.INSTALLING,
                )
            }
        }

        val activateCommand = buildString {
            append("set -e\n")
            spec.setupScript?.let { script -> append(script).append('\n') }
            spec.verifyCommand?.let { verify -> append(verify).append('\n') }
            append("cat > /").append(profile.markerName).append(" <<'ETA_PROFILE_EOF'\n")
            append("profile=").append(profile.revision).append('\n')
            append("ETA_PROFILE_EOF\n")
            append("chmod 0644 /").append(profile.markerName).append(" || exit 71")
        }
        val result = InstallerShellRunner.run(
            command = activateCommand,
            timeoutSeconds = profile.installTimeoutSeconds,
            environment = distribution.terminalEnvironment,
            linuxRootfsPath = rootfs.absolutePath,
        )
        AndroidAgentLogger.info(
            "Package profile action=activate distribution=${distribution.wireName} profile=${profile.id} " +
                "outcome=${if (result.exitCode == 0) "succeeded" else "failed"} " +
                "exitCode=${result.exitCode} outputChars=${result.output.length}",
        )
        if (result.exitCode != 0) {
            return@withContext PackageProfileInstallResult.Failed(PackageProfileInstallStage.INSTALLING)
        }

        onProgress(PackageProfileInstallProgress(PackageProfileInstallStage.COMPLETE))
        PackageProfileInstallResult.Installed
    }

    companion object {
        // npm 包体积较大（OpenCode ARM64 约 200 MB），手机网络下 10 分钟不够。
        private val installMutex = Mutex()
    }
}
