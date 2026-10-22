package io.github.mangi.eta.ui.screens.terminal

import android.content.Context

/** Linux 文件管理器的路径书签，按发行版环境无关的 guest 路径保存。 */
internal object LinuxFileBookmarks {
    private const val PREFS = "linux_file_bookmarks"
    private const val KEY = "paths"

    fun load(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())
            .orEmpty()
            .sorted()

    fun add(context: Context, path: String): Boolean {
        val current = load(context).toMutableSet()
        if (!current.add(path)) return false
        save(context, current)
        return true
    }

    fun remove(context: Context, path: String): Boolean {
        val current = load(context).toMutableSet()
        if (!current.remove(path)) return false
        save(context, current)
        return true
    }

    fun contains(context: Context, path: String): Boolean = path in load(context)

    private fun save(context: Context, paths: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY, paths)
            .apply()
    }
}
