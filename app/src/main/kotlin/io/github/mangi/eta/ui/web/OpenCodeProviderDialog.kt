package io.github.mangi.eta.ui.web

import android.app.AlertDialog
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.mangi.eta.agent.extension.OpenCodeProviderConfig
import io.github.mangi.eta.agent.extension.OpenCodeProviderDraft
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.agent.terminal.LinuxFileExplorer
import io.github.mangi.eta.agent.terminal.LinuxExecutionBackend
import io.github.mangi.eta.agent.terminal.ShellProcessSupervisor
import io.github.mangi.eta.agent.terminal.runOneShotShell
import io.github.mangi.eta.agent.terminal.terminalEnvironment
import io.github.mangi.eta.data.repository.LinuxEnvironmentSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OpenCode 2.1.2 的“自定义提供商”网页表单保存实现是上游占位代码，必然报
 * “此服务器上无法使用自定义提供商”。这里直接按官方配置格式写入 opencode.json，
 * 然后请求运行中的服务重载配置。
 */
internal object OpenCodeProviderDialog {
    fun show(activity: ComponentActivity, onSaved: () -> Unit) {
        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 4), dp(activity, 20), dp(activity, 8))
        }
        val id = field(activity, "提供商 ID", "myprovider")
        val name = field(activity, "显示名称", "我的 AI 提供商")
        val baseUrl = field(activity, "基础 URL", "https://api.myprovider.com/v1")
        val apiKey = field(activity, "API 密钥（可留空）", "sk-...")
        val models = field(activity, "模型 ID（每行一个）", "model-id").apply {
            setSingleLine(false)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 5
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
        }
        listOf(id, name, baseUrl, apiKey, models).forEach { form.addView(it, matchWidth()) }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("OpenCode 自定义提供商")
            .setMessage(
                "OpenCode 2.1.2 的网页表单被上游禁用。这里会直接写入 " +
                    OpenCodeProviderConfig.CONFIG_PATH +
                    "，保存后请刷新模型列表。",
            )
            .setView(ScrollView(activity).apply { addView(form) })
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create()
        dialog.setOnShowListener {
            val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            save.setOnClickListener {
                val draft = OpenCodeProviderDraft(
                    id = id.text.toString().trim(),
                    name = name.text.toString().trim(),
                    baseUrl = baseUrl.text.toString().trim(),
                    apiKey = apiKey.text.toString().trim(),
                    modelIds = OpenCodeProviderConfig.parseModels(models.text.toString()),
                )
                val validation = validate(draft)
                if (validation != null) {
                    Toast.makeText(activity, validation, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                save.isEnabled = false
                save.text = "保存中…"
                activity.lifecycleScope.launch {
                    val error = withContext(Dispatchers.IO) { writeConfig(activity, draft) }
                    if (error == null) {
                        Toast.makeText(activity, "已写入配置，正在刷新", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        onSaved()
                    } else {
                        Toast.makeText(activity, error, Toast.LENGTH_LONG).show()
                        save.isEnabled = true
                        save.text = "保存"
                    }
                }
            }
        }
        dialog.show()
    }

    private fun validate(draft: OpenCodeProviderDraft): String? = when {
        !draft.id.matches(Regex("[a-z0-9_-]+")) -> "提供商 ID 只能使用小写字母、数字、- 和 _"
        draft.name.isBlank() -> "显示名称不能为空"
        !draft.baseUrl.startsWith("http://") && !draft.baseUrl.startsWith("https://") ->
            "基础 URL 必须以 http:// 或 https:// 开头"
        draft.modelIds.isEmpty() -> "至少填写一个模型 ID"
        else -> null
    }

    private fun writeConfig(activity: ComponentActivity, draft: OpenCodeProviderDraft): String? {
        val distribution = LinuxEnvironmentSettingsRepository.current(activity)
        val backend = LinuxEnvironmentSettingsRepository.backend(activity, distribution)
        val rootfs = LinuxEnvironmentPaths.rootfsDir(activity, distribution, backend)
        if (!LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath)) return "Linux 环境尚未安装"
        val supervisor = ShellProcessSupervisor()
        return try {
            val existing = when (val read = LinuxFileExplorer.readText(supervisor, rootfs, OpenCodeProviderConfig.CONFIG_PATH)) {
                is LinuxFileExplorer.ReadResult.Text -> read.content
                LinuxFileExplorer.ReadResult.NotFile -> null
                LinuxFileExplorer.ReadResult.NotInstalled -> return "Linux 环境尚未安装"
                LinuxFileExplorer.ReadResult.Unreadable,
                LinuxFileExplorer.ReadResult.CommandFailed -> return "无法读取 OpenCode 配置"
                LinuxFileExplorer.ReadResult.Binary -> return "OpenCode 配置不是文本文件"
            }
            val merged = runCatching { OpenCodeProviderConfig.merge(existing, draft) }
                .getOrElse { return "配置文件解析失败：${it.message}" }
            when (LinuxFileExplorer.writeText(supervisor, rootfs, OpenCodeProviderConfig.CONFIG_PATH, merged)) {
                LinuxFileExplorer.WriteResult.Success -> Unit
                LinuxFileExplorer.WriteResult.NotInstalled -> return "Linux 环境尚未安装"
                LinuxFileExplorer.WriteResult.NotFile -> return "配置路径不是文件"
                LinuxFileExplorer.WriteResult.Unreadable -> return "没有权限写入 OpenCode 配置"
                LinuxFileExplorer.WriteResult.CommandFailed -> return "写入 OpenCode 配置失败"
            }
            val identity = if (backend == LinuxExecutionBackend.PROOT) "user" else "root"
            runOneShotShell(
                processSupervisor = supervisor,
                identity = identity,
                command = "/usr/local/bin/opencode reload >/dev/null 2>&1 || true",
                timeoutSeconds = 20,
                environment = distribution.terminalEnvironment,
                linuxRootfsPath = rootfs.absolutePath,
            )
            null
        } finally {
            supervisor.beginClosing()
        }
    }

    private fun field(activity: ComponentActivity, label: String, example: String): EditText =
        EditText(activity).apply {
            hint = if (example.isBlank()) label else "$label，例如 $example"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setPadding(0, dp(activity, 8), 0, dp(activity, 8))
        }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(activity: ComponentActivity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
