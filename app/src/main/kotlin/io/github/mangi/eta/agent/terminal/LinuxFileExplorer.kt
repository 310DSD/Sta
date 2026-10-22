package io.github.mangi.eta.agent.terminal

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64

/**
 * 面向用户的 Linux rootfs 只读文件浏览后端。
 *
 * Root 环境沿用宿主 Root Shell；免 Root 环境通过 PRoot 访问 guest 路径，
 * 保留符号链接、工作区和共享目录的 Linux 语义。路径只做词法归一化。
 */
internal object LinuxFileExplorer {
    const val DEFAULT_MAX_READ_BYTES = 256L * 1024L

    // 脚本内约定退出码；其余非零退出码一律视为命令失败。
    private const val EXIT_NOT_DIRECTORY = 41
    private const val EXIT_UNREADABLE = 42

    data class Entry(
        val name: String,
        val isDir: Boolean,
        val sizeBytes: Long,
        val mtimeEpochSeconds: Long,
    )

    sealed interface ListResult {
        data class Success(val entries: List<Entry>) : ListResult
        data object NotInstalled : ListResult
        data object NotDirectory : ListResult
        data object Unreadable : ListResult
        data object CommandFailed : ListResult
    }

    sealed interface ReadResult {
        data class Text(val content: String, val truncated: Boolean) : ReadResult
        data object Binary : ReadResult
        data object NotInstalled : ReadResult
        data object NotFile : ReadResult
        data object Unreadable : ReadResult
        data object CommandFailed : ReadResult
    }

    sealed interface WriteResult {
        data object Success : WriteResult
        data object NotInstalled : WriteResult
        data object NotFile : WriteResult
        data object Unreadable : WriteResult
        data object CommandFailed : WriteResult
    }

    data class Metadata(
        val permissions: String,
        val owner: String,
        val group: String,
        val sizeBytes: Long,
        val mtimeEpochSeconds: Long,
        val name: String,
    )

    sealed interface StatResult {
        data class Success(val metadata: Metadata) : StatResult
        data object NotInstalled : StatResult
        data object NotFound : StatResult
        data object Unreadable : StatResult
        data object CommandFailed : StatResult
    }

    sealed interface MutateResult {
        data object Success : MutateResult
        data object NotInstalled : MutateResult
        data object InvalidPath : MutateResult
        data object NotFound : MutateResult
        data object AlreadyExists : MutateResult
        data object Unreadable : MutateResult
        data object CommandFailed : MutateResult
    }

    private const val EXIT_NOT_FOUND = 44
    private const val EXIT_EXISTS = 45

    /**
     * 把 chroot 内绝对路径映射为宿主路径。只接受 `/` 开头的路径（空白归一为 `/`）；
     * `..` 弹栈、弹到根之上或相对路径返回 null。
     */
    fun resolveHostPath(rootfsDir: File, linuxPath: String): String? {
        val trimmed = linuxPath.trim()
        if (trimmed.isEmpty()) return rootfsDir.path
        if (!trimmed.startsWith("/") || '\n' in trimmed || '\r' in trimmed) return null
        val segments = mutableListOf<String>()
        trimmed.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) return null else segments.removeAt(segments.lastIndex)
                else -> segments += segment
            }
        }
        val normalized = "/" + segments.joinToString("/")
        // 子段已剔除 ..，File(parent, child) 的拼接不可能逃逸出 rootfsDir。
        return File(rootfsDir, normalized).path
    }

    /** 同步阻塞；协程切换由调用侧负责。rootfs 未就绪时不执行任何 Shell。 */
    fun list(
        supervisor: ShellProcessSupervisor,
        rootfsDir: File,
        linuxPath: String,
    ): ListResult {
        if (!LinuxEnvironmentPaths.rootfsReady(rootfsDir.absolutePath)) return ListResult.NotInstalled
        val hostPath = resolveHostPath(rootfsDir, linuxPath) ?: return ListResult.NotDirectory
        val rootless = LinuxEnvironmentPaths.backendOf(rootfsDir.absolutePath) == LinuxExecutionBackend.PROOT
        val quoted = shellQuote(if (rootless) "/" + File(rootfsDir.path).toPath().relativize(File(hostPath).toPath()).toString() else hostPath)
        // 空目录时通配符原样传给 stat，报错进 stderr 被吞掉，stdout 为空即空列表。
        val script = """
            if [ ! -d $quoted ]; then exit $EXIT_NOT_DIRECTORY; fi
            if [ ! -r $quoted ] || [ ! -x $quoted ]; then exit $EXIT_UNREADABLE; fi
            cd $quoted || exit $EXIT_UNREADABLE
            stat -c '%F|%s|%Y|%n' -- .[!.]* * 2>/dev/null
            exit 0
        """.trimIndent()
        val result = runOneShotShell(
            processSupervisor = supervisor,
            identity = if (LinuxEnvironmentPaths.backendOf(rootfsDir.absolutePath) == LinuxExecutionBackend.PROOT) "user" else "root",
            command = script,
            timeoutSeconds = 15,
            environment = if (rootless) TerminalEnvironment.ALPINE else TerminalEnvironment.ANDROID,
            linuxRootfsPath = rootfsDir.absolutePath,
        )
        if (result.exitCode != 0) {
            directList(hostPath)?.let { return it }
        }
        return when (result.exitCode) {
            0 -> ListResult.Success(parseStatOutput(result.output.decodeToString()))
            EXIT_NOT_DIRECTORY -> ListResult.NotDirectory
            EXIT_UNREADABLE -> ListResult.Unreadable
            else -> ListResult.CommandFailed
        }
    }

    /** Shell 不可用时（例如 rootfs 尚未装好）仍允许 App 进程直接浏览可读目录。 */
    private fun directList(hostPath: String): ListResult? {
        val dir = File(hostPath)
        if (!dir.exists()) return null
        if (!dir.isDirectory) return ListResult.NotDirectory
        val children = dir.listFiles() ?: return ListResult.Unreadable
        return ListResult.Success(
            sortEntries(
                children.map { child ->
                    Entry(
                        name = child.name,
                        isDir = child.isDirectory,
                        sizeBytes = if (child.isFile) child.length() else 0L,
                        mtimeEpochSeconds = child.lastModified() / 1000,
                    )
                },
            ),
        )
    }

    /** 同步阻塞；读取上限 [maxBytes]，多出 1 字节用于判定截断。 */
    fun readText(
        supervisor: ShellProcessSupervisor,
        rootfsDir: File,
        linuxPath: String,
        maxBytes: Long = DEFAULT_MAX_READ_BYTES,
    ): ReadResult {
        if (!LinuxEnvironmentPaths.rootfsReady(rootfsDir.absolutePath)) return ReadResult.NotInstalled
        val hostPath = resolveHostPath(rootfsDir, linuxPath) ?: return ReadResult.NotFile
        val rootless = LinuxEnvironmentPaths.backendOf(rootfsDir.absolutePath) == LinuxExecutionBackend.PROOT
        val quoted = shellQuote(if (rootless) "/" + File(rootfsDir.path).toPath().relativize(File(hostPath).toPath()).toString() else hostPath)
        val script = """
            if [ ! -f $quoted ]; then exit $EXIT_NOT_DIRECTORY; fi
            if [ ! -r $quoted ]; then exit $EXIT_UNREADABLE; fi
            head -c ${maxBytes + 1} $quoted
            exit 0
        """.trimIndent()
        val result = runOneShotShell(
            processSupervisor = supervisor,
            identity = if (LinuxEnvironmentPaths.backendOf(rootfsDir.absolutePath) == LinuxExecutionBackend.PROOT) "user" else "root",
            command = script,
            timeoutSeconds = 15,
            environment = if (rootless) TerminalEnvironment.ALPINE else TerminalEnvironment.ANDROID,
            linuxRootfsPath = rootfsDir.absolutePath,
        )
        if (result.exitCode != 0) {
            directReadText(hostPath, maxBytes)?.let { return it }
            return when (result.exitCode) {
                EXIT_NOT_DIRECTORY -> ReadResult.NotFile
                EXIT_UNREADABLE -> ReadResult.Unreadable
                else -> ReadResult.CommandFailed
            }
        }
        val truncated = result.output.size.toLong() > maxBytes
        val payload = if (truncated) result.output.copyOf(maxBytes.toInt()) else result.output
        // 含 NUL 字节按二进制处理，不把乱码塞进查看器。
        if (payload.contains(0.toByte())) return ReadResult.Binary
        return ReadResult.Text(content = payload.decodeToString(), truncated = truncated)
    }

    /**
     * 原子写入 guest 文件；内容经 stdin/base64 传输，避免配置里的引号、换行和 `$`
     * 被 shell 二次解释。目录不存在时按需创建。
     */
    /** Shell 不可用时直接读取 App 进程可访问的文件。 */
    private fun directReadText(hostPath: String, maxBytes: Long): ReadResult? {
        val file = File(hostPath)
        if (!file.exists()) return null
        if (!file.isFile) return ReadResult.NotFile
        if (!file.canRead()) return null
        val bytes = runCatching {
            file.inputStream().use { input ->
                val buffer = ByteArray((maxBytes + 1).toInt())
                var total = 0
                while (total < buffer.size) {
                    val read = input.read(buffer, total, buffer.size - total)
                    if (read < 0) break
                    total += read
                }
                buffer.copyOf(total)
            }
        }.getOrNull() ?: return null
        val truncated = bytes.size.toLong() > maxBytes
        val payload = if (truncated) bytes.copyOf(maxBytes.toInt()) else bytes
        if (payload.contains(0.toByte())) return ReadResult.Binary
        return ReadResult.Text(payload.decodeToString(), truncated)
    }

    fun writeText(
        supervisor: ShellProcessSupervisor,
        rootfsDir: File,
        linuxPath: String,
        content: String,
    ): WriteResult {
        if (!LinuxEnvironmentPaths.rootfsReady(rootfsDir.absolutePath)) return WriteResult.NotInstalled
        val hostPath = resolveHostPath(rootfsDir, linuxPath) ?: return WriteResult.NotFile
        val rootless = LinuxEnvironmentPaths.backendOf(rootfsDir.absolutePath) == LinuxExecutionBackend.PROOT
        val quoted = shellQuote(
            if (rootless) "/" + File(rootfsDir.path).toPath().relativize(File(hostPath).toPath()).toString() else hostPath,
        )
        val script = """
            eta_target=$quoted
            if [ -d "${'$'}eta_target" ]; then exit 43; fi
            eta_dir=${'$'}(dirname "${'$'}eta_target") || exit 42
            mkdir -p "${'$'}eta_dir" || exit 42
            eta_tmp="${'$'}eta_target.eta-tmp.${'$'}${'$'}"
            base64 -d > "${'$'}eta_tmp" || exit 42
            mv "${'$'}eta_tmp" "${'$'}eta_target" || exit 42
            exit 0
        """.trimIndent()
        val payload = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)).toByteArray()
        val result = runOneShotShell(
            processSupervisor = supervisor,
            identity = if (rootless) "user" else "root",
            command = script,
            timeoutSeconds = 15,
            stdin = payload,
            environment = if (rootless) TerminalEnvironment.ALPINE else TerminalEnvironment.ANDROID,
            linuxRootfsPath = rootfsDir.absolutePath,
        )
        if (result.exitCode != 0) {
            val direct = runCatching {
                val file = File(hostPath)
                file.parentFile?.mkdirs()
                file.writeText(content, Charsets.UTF_8)
            }
            if (direct.isSuccess) return WriteResult.Success
        }
        return when (result.exitCode) {
            0 -> WriteResult.Success
            42 -> WriteResult.Unreadable
            43 -> WriteResult.NotFile
            else -> WriteResult.CommandFailed
        }
    }

    /** 读取单条路径的权限、属主、大小与时间，用于属性面板。 */
    fun stat(
        supervisor: ShellProcessSupervisor,
        rootfsDir: File,
        linuxPath: String,
    ): StatResult {
        if (!LinuxEnvironmentPaths.rootfsReady(rootfsDir.absolutePath)) return StatResult.NotInstalled
        val target = shellPath(rootfsDir, linuxPath) ?: return StatResult.NotFound
        val quoted = shellQuote(target)
        val script = """
            if [ ! -e $quoted ] && [ ! -L $quoted ]; then exit $EXIT_NOT_FOUND; fi
            stat -c '%A|%U|%G|%s|%Y|%n' $quoted 2>/dev/null || exit $EXIT_UNREADABLE
            exit 0
        """.trimIndent()
        val result = runShell(supervisor, rootfsDir, script)
        if (result.exitCode != 0) {
            directStat(target)?.let { return StatResult.Success(it) }
        }
        return when (result.exitCode) {
            0 -> parseMetadata(result.output.decodeToString())?.let(StatResult::Success)
                ?: StatResult.CommandFailed
            EXIT_NOT_FOUND -> StatResult.NotFound
            EXIT_UNREADABLE -> StatResult.Unreadable
            else -> StatResult.CommandFailed
        }
    }

    private fun directStat(hostPath: String): Metadata? = runCatching {
        val path = File(hostPath).toPath()
        val attrs = Files.readAttributes(path, PosixFileAttributes::class.java)
        Metadata(
            permissions = (if (attrs.isDirectory) "d" else "-") +
                PosixFilePermissions.toString(attrs.permissions()),
            owner = attrs.owner().name,
            group = attrs.group().name,
            sizeBytes = attrs.size(),
            mtimeEpochSeconds = attrs.lastModifiedTime().toMillis() / 1000,
            name = path.fileName.toString(),
        )
    }.getOrNull()

    /** 删除一个或多个 guest 路径；根目录永远拒绝，避免误删整个 rootfs。 */
    fun delete(
        supervisor: ShellProcessSupervisor,
        rootfsDir: File,
        linuxPaths: List<String>,
    ): MutateResult {
        if (!LinuxEnvironmentPaths.rootfsReady(rootfsDir.absolutePath)) return MutateResult.NotInstalled
        if (linuxPaths.isEmpty()) return MutateResult.InvalidPath
        val targets = mutableListOf<String>()
        for (path in linuxPaths) {
            if (normalizeGuestPath(path) == "/") return MutateResult.InvalidPath
            targets += shellPath(rootfsDir, path) ?: return MutateResult.InvalidPath
        }
        val script = buildString {
            targets.forEach { target ->
                appendLine("rm -rf ${shellQuote(target)} || exit $EXIT_UNREADABLE")
            }
            appendLine("exit 0")
        }
        return runShell(supervisor, rootfsDir, script).toMutateResult()
    }

    /** 重命名同目录内条目。 */
    fun rename(
        supervisor: ShellProcessSupervisor,
        rootfsDir: File,
        linuxPath: String,
        newName: String,
    ): MutateResult {
        if (!LinuxEnvironmentPaths.rootfsReady(rootfsDir.absolutePath)) return MutateResult.NotInstalled
        val normalized = normalizeGuestPath(linuxPath) ?: return MutateResult.InvalidPath
        if (normalized == "/" || !isValidName(newName)) return MutateResult.InvalidPath
        if (normalized.substringAfterLast('/') == newName) return MutateResult.Success
        val parent = normalized.substringBeforeLast('/', "").ifBlank { "/" }
        val destination = joinGuestPath(parent, newName)
        val sourceShell = shellPath(rootfsDir, normalized) ?: return MutateResult.InvalidPath
        val destinationShell = shellPath(rootfsDir, destination) ?: return MutateResult.InvalidPath
        val script = """
            if [ ! -e ${shellQuote(sourceShell)} ] && [ ! -L ${shellQuote(sourceShell)} ]; then exit $EXIT_NOT_FOUND; fi
            if [ -e ${shellQuote(destinationShell)} ] || [ -L ${shellQuote(destinationShell)} ]; then exit $EXIT_EXISTS; fi
            mv ${shellQuote(sourceShell)} ${shellQuote(destinationShell)} || exit $EXIT_UNREADABLE
            exit 0
        """.trimIndent()
        return runShell(supervisor, rootfsDir, script).toMutateResult()
    }

    /** 复制到目标目录，保留符号链接、权限与时间。 */
    fun copyInto(
        supervisor: ShellProcessSupervisor,
        rootfsDir: File,
        linuxPaths: List<String>,
        targetDir: String,
    ): MutateResult = transfer(supervisor, rootfsDir, linuxPaths, targetDir, move = false)

    /** 移动到目标目录。 */
    fun moveInto(
        supervisor: ShellProcessSupervisor,
        rootfsDir: File,
        linuxPaths: List<String>,
        targetDir: String,
    ): MutateResult = transfer(supervisor, rootfsDir, linuxPaths, targetDir, move = true)

    fun createDirectory(
        supervisor: ShellProcessSupervisor,
        rootfsDir: File,
        parent: String,
        name: String,
    ): MutateResult = create(supervisor, rootfsDir, parent, name, directory = true)

    fun createFile(
        supervisor: ShellProcessSupervisor,
        rootfsDir: File,
        parent: String,
        name: String,
    ): MutateResult = create(supervisor, rootfsDir, parent, name, directory = false)

    private fun create(
        supervisor: ShellProcessSupervisor,
        rootfsDir: File,
        parent: String,
        name: String,
        directory: Boolean,
    ): MutateResult {
        if (!LinuxEnvironmentPaths.rootfsReady(rootfsDir.absolutePath)) return MutateResult.NotInstalled
        if (!isValidName(name)) return MutateResult.InvalidPath
        val parentPath = normalizeGuestPath(parent) ?: return MutateResult.InvalidPath
        val target = joinGuestPath(parentPath, name)
        val quoted = shellPath(rootfsDir, target)?.let(::shellQuote) ?: return MutateResult.InvalidPath
        val command = if (directory) "mkdir -p $quoted" else "touch $quoted"
        val script = """
            if [ -e $quoted ] || [ -L $quoted ]; then exit $EXIT_EXISTS; fi
            $command || exit $EXIT_UNREADABLE
            exit 0
        """.trimIndent()
        return runShell(supervisor, rootfsDir, script).toMutateResult()
    }

    private fun transfer(
        supervisor: ShellProcessSupervisor,
        rootfsDir: File,
        linuxPaths: List<String>,
        targetDir: String,
        move: Boolean,
    ): MutateResult {
        if (!LinuxEnvironmentPaths.rootfsReady(rootfsDir.absolutePath)) return MutateResult.NotInstalled
        if (linuxPaths.isEmpty()) return MutateResult.InvalidPath
        val normalizedTarget = normalizeGuestPath(targetDir) ?: return MutateResult.InvalidPath
        val targetShell = shellPath(rootfsDir, normalizedTarget) ?: return MutateResult.InvalidPath
        val sources = mutableListOf<Pair<String, String>>()
        for (path in linuxPaths) {
            val normalized = normalizeGuestPath(path) ?: return MutateResult.InvalidPath
            if (normalized == "/") return MutateResult.InvalidPath
            if (normalizedTarget == normalized || normalizedTarget.startsWith("$normalized/")) {
                return MutateResult.InvalidPath
            }
            val sourceShell = shellPath(rootfsDir, normalized) ?: return MutateResult.InvalidPath
            sources += normalized to sourceShell
        }
        val command = if (move) "mv" else "cp -a"
        val script = buildString {
            appendLine("if [ ! -d ${shellQuote(targetShell)} ]; then exit $EXIT_NOT_FOUND; fi")
            sources.forEach { (guest, sourceShell) ->
                val name = guest.substringAfterLast('/')
                val destination = joinGuestPath(normalizedTarget, name)
                val destinationShell = shellPath(rootfsDir, destination) ?: return MutateResult.InvalidPath
                appendLine(
                    "if [ -e ${shellQuote(destinationShell)} ] || [ -L ${shellQuote(destinationShell)} ]; " +
                        "then exit $EXIT_EXISTS; fi",
                )
                appendLine("$command ${shellQuote(sourceShell)} ${shellQuote(targetShell)}/ || exit $EXIT_UNREADABLE")
            }
            appendLine("exit 0")
        }
        return runShell(supervisor, rootfsDir, script).toMutateResult()
    }

    private fun runShell(
        supervisor: ShellProcessSupervisor,
        rootfsDir: File,
        script: String,
    ): OneShotShellResult {
        val rootless = LinuxEnvironmentPaths.backendOf(rootfsDir.absolutePath) == LinuxExecutionBackend.PROOT
        return runOneShotShell(
            processSupervisor = supervisor,
            identity = if (rootless) "user" else "root",
            command = script,
            timeoutSeconds = 120,
            environment = if (rootless) TerminalEnvironment.ALPINE else TerminalEnvironment.ANDROID,
            linuxRootfsPath = rootfsDir.absolutePath,
        )
    }

    private fun OneShotShellResult.toMutateResult(): MutateResult = when (exitCode) {
        0 -> MutateResult.Success
        EXIT_NOT_FOUND -> MutateResult.NotFound
        EXIT_EXISTS -> MutateResult.AlreadyExists
        EXIT_UNREADABLE -> MutateResult.Unreadable
        else -> MutateResult.CommandFailed
    }

    private fun shellPath(rootfsDir: File, linuxPath: String): String? {
        val hostPath = resolveHostPath(rootfsDir, linuxPath) ?: return null
        if (LinuxEnvironmentPaths.backendOf(rootfsDir.absolutePath) != LinuxExecutionBackend.PROOT) {
            return hostPath
        }
        val relative = File(rootfsDir.path).toPath().relativize(File(hostPath).toPath()).toString()
        return "/" + relative.replace(File.separatorChar, '/')
    }

    private fun normalizeGuestPath(path: String): String? {
        val trimmed = path.trim()
        if (!trimmed.startsWith("/") || '\n' in trimmed || '\r' in trimmed) return null
        val segments = mutableListOf<String>()
        trimmed.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) return null else segments.removeAt(segments.lastIndex)
                else -> segments += segment
            }
        }
        return "/" + segments.joinToString("/")
    }

    private fun joinGuestPath(parent: String, name: String): String =
        if (parent == "/") "/$name" else "$parent/$name"

    private fun isValidName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.isNotEmpty() && trimmed != "." && trimmed != ".." &&
            '/' !in trimmed && '\n' !in trimmed && '\r' !in trimmed && '\u0000' !in trimmed
    }

    internal fun parseMetadata(output: String): Metadata? {
        val line = output.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        val parts = line.split('|', limit = 6)
        if (parts.size < 6) return null
        return Metadata(
            permissions = parts[0],
            owner = parts[1],
            group = parts[2],
            sizeBytes = parts[3].toLongOrNull() ?: return null,
            mtimeEpochSeconds = parts[4].toLongOrNull() ?: return null,
            name = parts[5],
        )
    }

    /**
     * 解析 `stat -c '%F|%s|%Y|%n'` 输出：按前 3 个分隔符切分，其余全部归入文件名，
     * 容忍文件名含 `|`；畸形行与通配符残留行跳过。
     */
    internal fun parseStatOutput(output: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        output.lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split('|', limit = 4)
            if (parts.size < 4) return@forEach
            val name = parts[3]
            if (name == "*" || name == ".[!.]*") return@forEach
            val size = parts[1].toLongOrNull() ?: return@forEach
            val mtime = parts[2].toLongOrNull() ?: return@forEach
            entries += Entry(
                name = name,
                isDir = parts[0] == "directory",
                sizeBytes = size,
                mtimeEpochSeconds = mtime,
            )
        }
        return sortEntries(entries)
    }

    /** 目录在前，各自按名称排序。 */
    internal fun sortEntries(entries: List<Entry>): List<Entry> =
        entries.sortedWith(compareBy<Entry> { !it.isDir }.thenBy { it.name })
}
