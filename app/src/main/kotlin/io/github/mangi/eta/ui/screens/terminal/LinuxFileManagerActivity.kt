package io.github.mangi.eta.ui.screens.terminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.repository.AppearanceSettingsRepository
import io.github.mangi.eta.data.repository.LinuxEnvironmentSettingsRepository
import io.github.mangi.eta.ui.app.AgentAppTheme

/** 从扩展 Agent WebView 等入口独立打开的 Linux 文件管理器。 */
internal class LinuxFileManagerActivity : ComponentActivity() {
    companion object {
        const val EXTRA_DISTRIBUTION = "io.github.mangi.eta.extra.LINUX_DISTRIBUTION"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val distribution = intent.getStringExtra(EXTRA_DISTRIBUTION)
            ?: LinuxEnvironmentSettingsRepository.current(this).wireName
        setContent {
            val appearance by AppearanceSettingsRepository.settingsFlow()
                .collectAsState(initial = AppearanceSettings())
            AgentAppTheme(appearance = appearance, applyInterfaceScale = false) {
                LinuxFilesScreen(
                    context = this,
                    distribution = distribution,
                    onBack = { finish() },
                )
            }
        }
    }
}
