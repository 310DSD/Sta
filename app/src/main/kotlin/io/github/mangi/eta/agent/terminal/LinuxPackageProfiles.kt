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
/* Eta mobile shell v2 – DSH */
:root { --eta-topbar-h: 56px; }

@media (max-width: 720px) {
  html, body {
    width: 100%;
    height: 100%;
    overflow: hidden !important;
    background: var(--dsw-alias-bg-base);
  }
  body { overscroll-behavior: none; }
  #root {
    height: calc(100dvh - var(--eta-topbar-h)) !important;
    margin-top: var(--eta-topbar-h) !important;
  }

  /* Native-feeling top bar. */
  #eta-mobile-bar {
    position: fixed;
    z-index: 90;
    inset: 0 0 auto;
    height: var(--eta-topbar-h);
    display: flex;
    align-items: center;
    gap: 2px;
    padding: 0 6px;
    box-sizing: border-box;
    background: color-mix(in srgb, var(--dsw-alias-bg-base) 88%, transparent);
    -webkit-backdrop-filter: blur(18px) saturate(1.2);
    backdrop-filter: blur(18px) saturate(1.2);
    border-bottom: .5px solid var(--dsw-alias-border-l3);
    color: var(--dsw-alias-label-primary);
  }
  .eta-icon {
    width: 40px;
    height: 40px;
    flex: none;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border: 0;
    border-radius: 12px;
    background: transparent;
    color: inherit;
    padding: 0;
    -webkit-tap-highlight-color: transparent;
  }
  .eta-icon:active { background: var(--dsw-alias-interactive-bg-hover); }
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
    font-size: 15px;
    line-height: 20px;
    font-weight: 600;
    letter-spacing: .01em;
  }
  #eta-scrim {
    position: fixed;
    z-index: 80;
    inset: 0;
    background: rgb(0 0 0 / 32%);
    opacity: 0;
    pointer-events: none;
    transition: opacity .24s ease;
  }
  body.eta-drawer-open #eta-scrim { opacity: 1; pointer-events: auto; }
  body.eta-drawer-open #eta-mobile-bar { visibility: hidden; }

  /* The DSH desktop three-column frame becomes a single mobile column. */
  .pI_x6G_frame {
    grid-template-columns: minmax(0, 1fr) !important;
    grid-template-rows: minmax(0, 1fr) auto !important;
    height: 100% !important;
    background: var(--dsw-alias-bg-base) !important;
  }
  .pI_x6G_centerCol {
    grid-column: 1 / -1 !important;
    width: 100% !important;
    border: 0 !important;
  }
  .pI_x6G_rightbarCol { grid-column: 1 / -1 !important; }

  /* Desktop rail becomes an off-canvas drawer. */
  .pI_x6G_sidebarCol {
    position: fixed !important;
    z-index: 120 !important;
    inset: 0 auto 0 0 !important;
    width: min(86vw, 340px) !important;
    min-width: 0 !important;
    max-width: none !important;
    border-right: 1px solid var(--dsw-alias-border-l3) !important;
    box-shadow: 0 18px 60px rgb(0 0 0 / 18%) !important;
    transform: translateX(-104%) !important;
    transition: transform .28s cubic-bezier(.2, .8, .2, 1) !important;
    overflow: hidden !important;
  }
  .pI_x6G_frame:not([data-sidebar-collapsed]) .pI_x6G_sidebarCol {
    transform: translateX(0) !important;
  }
  .pI_x6G_sidebarCol .hHd-Xa_root { width: 100% !important; }
  .pI_x6G_sidebarCol .hHd-Xa_collapsed { width: 100% !important; }

  /* Full-width conversation column. The desktop chat column has a 680px floor
     that clips every mobile message; replace it with the viewport width. */
  .wSkVaW_root { --dsh-conversation-column-width: 100vw !important; }
  .wSkVaW_body,
  .wSkVaW_embeddedBody {
    --dsh-chat-content-width: calc(100vw - 24px) !important;
    --dsh-composer-card-max-width: calc(100vw - 24px) !important;
    --dsh-composer-side-clearance: 12px !important;
  }
  .wSkVaW_header { display: none !important; }
  .wSkVaW_scrollBody {
    margin-right: 0 !important;
    scrollbar-gutter: auto !important;
  }
  .wSkVaW_composerHero {
    width: 100% !important;
    padding: 0 12px 14px !important;
    gap: 10px !important;
  }
  .wSkVaW_heroWorkspaceRow { padding: 0 12px !important; }

  /* Composer: larger touch area, softer surface, no desktop min-width. */
  .uV2eYG_card {
    border-radius: 20px !important;
    box-shadow: 0 8px 28px rgb(0 0 0 / 8%), 0 0 0 .5px var(--dsw-alias-border-l3) !important;
  }
  .uV2eYG_input {
    min-height: 56px !important;
    font-size: 15px !important;
    line-height: 24px !important;
    padding: 14px 16px 2px !important;
  }
  .uV2eYG_placeholder { padding: 14px 16px 2px !important; font-size: 15px !important; }
  .uV2eYG_dock { padding: 6px 10px 10px !important; gap: 4px !important; }
  .uV2eYG_primary { width: 38px !important; height: 38px !important; border-radius: 50% !important; }

  /* Settings / dialogs become full-screen sheets with horizontal nav chips. */
  .VOzbGW_overlay {
    align-items: stretch !important;
    justify-content: stretch !important;
    padding: 0 !important;
  }
  .VOzbGW_panel {
    position: relative !important;
    width: 100vw !important;
    max-width: none !important;
    height: 100dvh !important;
    max-height: none !important;
    border-radius: 0 !important;
    flex-direction: column !important;
    display: flex !important;
    overflow: hidden !important;
  }
  .VOzbGW_nav {
    width: 100% !important;
    height: auto !important;
    max-height: none !important;
    flex: none !important;
    flex-direction: column !important;
    padding: 0 !important;
    border-right: 0 !important;
    border-bottom: 1px solid var(--dsw-alias-border-l3) !important;
  }
  .VOzbGW_navTitle {
    display: block !important;
    padding: 14px 154px 6px 16px !important;
    font-size: 20px !important;
    line-height: 30px !important;
  }
  .VOzbGW_navList {
    width: 100% !important;
    flex-direction: row !important;
    flex-wrap: wrap !important;
    gap: 6px !important;
    padding: 4px 12px 10px !important;
    overflow: visible !important;
    scrollbar-width: none !important;
  }
  .VOzbGW_navCell {
    flex: 0 0 auto !important;
    width: auto !important;
    height: 34px !important;
    padding: 0 12px !important;
    border-radius: 999px !important;
    white-space: nowrap !important;
    gap: 6px !important;
  }
  .VOzbGW_navLabel {
    width: auto !important;
    font-size: 13px !important;
    white-space: nowrap !important;
  }
  .VOzbGW_content {
    width: 100% !important;
    max-width: none !important;
    min-width: 0 !important;
    flex: 1 !important;
    min-height: 0 !important;
    display: flex !important;
    flex-direction: column !important;
  }
  .VOzbGW_header {
    position: absolute !important;
    z-index: 3 !important;
    top: 8px !important;
    right: 8px !important;
    width: auto !important;
    min-height: 0 !important;
    height: auto !important;
    padding: 0 !important;
    border: 0 !important;
    background: transparent !important;
  }
  .VOzbGW_options {
    width: 100% !important;
    max-width: none !important;
    flex: 1 !important;
    min-height: 0 !important;
    overflow-y: auto !important;
    padding: 8px 16px 36px !important;
  }
}
    """.trimIndent()

    internal val DSH_MOBILE_CSS_INSTALL =
        "for f in /usr/local/lib/node_modules/@deepseek-ai/dsh-web-frontend/dist/assets/index-*.css; do " +
            "[ -f \"${'$'}f\" ] || continue; " +
            "sed -i '/^\\/\\* eta-mobile/d' \"${'$'}f\"; " +
            "printf '%s\n' '/* eta-mobile-v2 */ $DSH_MOBILE_CSS' >> \"${'$'}f\"; done"

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
