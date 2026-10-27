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

    // Official ZCode CLI distribution, built from the Apache-2.0 source tree in
    // the Eta fork. The artifact is a Node.js web/TUI distribution; the phone
    // only downloads and extracts it, it never runs the Electron desktop app.
    private const val ZCODE_INSTALL_SCRIPT =
        "set -e; " +
            "curl -fL --retry 3 --connect-timeout 20 " +
            "https://github.com/310DSD/ZCode/releases/latest/download/eta-zcode-arm64.tar.gz " +
            "-o /tmp/eta-zcode.tar.gz; " +
            "mkdir -p /opt/eta; " +
            "tar -xzf /tmp/eta-zcode.tar.gz -C /opt/eta; " +
            "chmod +x /opt/eta/zcode/bin/zcode.mjs; " +
            "ln -sf /opt/eta/zcode/bin/zcode.mjs /usr/local/bin/zcode; " +
            "rm -f /tmp/eta-zcode.tar.gz"

    private const val CODEX_INSTALL_SCRIPT =
        "npm install -g --prefix /usr/local --include=optional --registry=https://registry.npmmirror.com " +
            "@openai/codex@0.164.0 || " +
            "npm install -g --prefix /usr/local --include=optional @openai/codex@0.164.0"

    private const val ACP_INSTALL_SCRIPT =
        "mkdir -p /opt/eta/acp && " +
            "npm install -g --prefix /usr/local --include=optional " +
            "--registry=https://registry.npmmirror.com " +
            "@agentclientprotocol/claude-agent-acp@0.94.0 " +
            "@agentclientprotocol/codex-acp@2.2.2 || " +
            "npm install -g --prefix /usr/local --include=optional " +
            "@agentclientprotocol/claude-agent-acp@0.94.0 " +
            "@agentclientprotocol/codex-acp@2.2.2; " +
            "npm install --prefix /opt/eta/acp --registry=https://registry.npmmirror.com ws@8 || " +
            "npm install --prefix /opt/eta/acp ws@8"

    private const val CLOUDCLI_INSTALL_SCRIPT =
        "npm install -g --prefix /usr/local --include=optional " +
            "--registry=https://registry.npmmirror.com @cloudcli-ai/cloudcli@1.37.5 || " +
            "npm install -g --prefix /usr/local --include=optional @cloudcli-ai/cloudcli@1.37.5"

    private const val CLAUDE_CODE_INSTALL_SCRIPT =
        "npm install -g --prefix /usr/local --include=optional --registry=https://registry.npmmirror.com " +
            "@anthropic-ai/claude-code@2.1.309 || " +
            "npm install -g --prefix /usr/local --include=optional @anthropic-ai/claude-code@2.1.309"

    private const val OPENCODE_INSTALL_SCRIPT =
        "npm install -g --prefix /usr/local --include=optional --registry=https://registry.npmmirror.com " +
            "@opencode/cli@2.1.2 || " +
            "npm install -g --prefix /usr/local --include=optional @opencode/cli@2.1.2"

    private val DSH_INSTALL_SCRIPT =
        "(npm install -g --prefix /usr/local --include=optional --registry=https://registry.npmmirror.com " +
            "@deepseek-ai/dsh@0.2.1-rc.2 || " +
            "npm install -g --prefix /usr/local --include=optional @deepseek-ai/dsh@0.2.1-rc.2) && " +
            DshMobileCss.installScript

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
    val ZCODE = LinuxPackageProfile(
        id = "zcode",
        markerName = AlpineEnvironmentPaths.ZCODE_TOOLS_MARKER,
        revision = AlpineEnvironmentPaths.ZCODE_TOOLS_REVISION,
        dependsOn = NODE,
        installTimeoutSeconds = 1200L,
        specs = mapOf(
            LinuxDistribution.DEBIAN to LinuxPackageSpec(
                packages = listOf("curl", "ca-certificates"),
                setupScript = ZCODE_INSTALL_SCRIPT,
                verifyCommand = "command -v zcode >/dev/null && zcode --version >/dev/null",
            ),
            LinuxDistribution.ALPINE to LinuxPackageSpec(
                packages = listOf("curl", "ca-certificates"),
                setupScript = ZCODE_INSTALL_SCRIPT,
                verifyCommand = "command -v zcode >/dev/null && zcode --version >/dev/null",
            ),
        ),
    )

    val CODEX = LinuxPackageProfile(
        id = "codex",
        markerName = AlpineEnvironmentPaths.CODEX_TOOLS_MARKER,
        revision = AlpineEnvironmentPaths.CODEX_TOOLS_REVISION,
        dependsOn = NODE,
        installTimeoutSeconds = 3600L,
        specs = mapOf(
            LinuxDistribution.ALPINE to LinuxPackageSpec(
                setupScript = CODEX_INSTALL_SCRIPT,
                verifyCommand = "command -v codex >/dev/null && codex --version >/dev/null",
            ),
            LinuxDistribution.DEBIAN to LinuxPackageSpec(
                setupScript = CODEX_INSTALL_SCRIPT,
                verifyCommand = "command -v codex >/dev/null && codex --version >/dev/null",
            ),
        ),
    )

    val CLAUDE_CODE = LinuxPackageProfile(
        id = "claude-code",
        markerName = AlpineEnvironmentPaths.CLAUDE_CODE_TOOLS_MARKER,
        revision = AlpineEnvironmentPaths.CLAUDE_CODE_TOOLS_REVISION,
        dependsOn = NODE,
        installTimeoutSeconds = 3600L,
        specs = mapOf(
            LinuxDistribution.ALPINE to LinuxPackageSpec(
                setupScript = CLAUDE_CODE_INSTALL_SCRIPT,
                verifyCommand = "command -v claude >/dev/null && claude --version >/dev/null",
            ),
            LinuxDistribution.DEBIAN to LinuxPackageSpec(
                setupScript = CLAUDE_CODE_INSTALL_SCRIPT,
                verifyCommand = "command -v claude >/dev/null && claude --version >/dev/null",
            ),
        ),
    )

    val CLOUDCLI = LinuxPackageProfile(
        id = "cloudcli",
        markerName = AlpineEnvironmentPaths.CLOUDCLI_TOOLS_MARKER,
        revision = AlpineEnvironmentPaths.CLOUDCLI_TOOLS_REVISION,
        dependsOn = NODE,
        installTimeoutSeconds = 3600L,
        specs = mapOf(
            LinuxDistribution.ALPINE to LinuxPackageSpec(
                packages = listOf("build-base", "python3", "linux-headers", "sqlite-dev", "pkgconf"),
                setupScript = CLOUDCLI_INSTALL_SCRIPT,
                verifyCommand = "command -v cloudcli >/dev/null && cloudcli --version >/dev/null",
            ),
            LinuxDistribution.DEBIAN to LinuxPackageSpec(
                packages = listOf(
                    "build-essential",
                    "python3",
                    "libsqlite3-dev",
                    "pkg-config",
                ),
                setupScript = CLOUDCLI_INSTALL_SCRIPT,
                verifyCommand = "command -v cloudcli >/dev/null && cloudcli --version >/dev/null",
            ),
        ),
    )

    val ACP = LinuxPackageProfile(
        id = "acp",
        markerName = AlpineEnvironmentPaths.ACP_TOOLS_MARKER,
        revision = AlpineEnvironmentPaths.ACP_TOOLS_REVISION,
        dependsOn = NODE,
        installTimeoutSeconds = 3600L,
        specs = mapOf(
            LinuxDistribution.ALPINE to LinuxPackageSpec(
                packages = listOf("curl", "ca-certificates"),
                setupScript = ACP_INSTALL_SCRIPT,
                verifyCommand = "command -v claude-agent-acp >/dev/null && command -v codex-acp >/dev/null && test -d /opt/eta/acp/node_modules/ws",
            ),
            LinuxDistribution.DEBIAN to LinuxPackageSpec(
                packages = listOf("curl", "ca-certificates"),
                setupScript = ACP_INSTALL_SCRIPT,
                verifyCommand = "command -v claude-agent-acp >/dev/null && command -v codex-acp >/dev/null && test -d /opt/eta/acp/node_modules/ws",
            ),
        ),
    )

    val ALL = listOf(PYTHON, NODE, SSH, OPENCODE, DSH, KIMI, ZCODE, CODEX, CLAUDE_CODE, CLOUDCLI, ACP)
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
