package org.basadrito.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.basadrito.app.designsystem.AppearancePreferences
import org.basadrito.app.designsystem.ThemeMode

class AndroidAppearancePreferences(context: Context, name: String = "appearance") : AppearancePreferences {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val value = MutableStateFlow(ThemeMode.entries.firstOrNull {
        it.name == preferences.getString("theme", null)
    } ?: ThemeMode.SYSTEM)
    override val mode = value.asStateFlow()
    override fun setMode(mode: ThemeMode) {
        preferences.edit().putString("theme", mode.name).apply()
        value.value = mode
    }
}
