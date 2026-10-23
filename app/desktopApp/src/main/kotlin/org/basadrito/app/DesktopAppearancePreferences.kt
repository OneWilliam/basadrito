package org.basadrito.app

import java.util.prefs.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.basadrito.app.designsystem.*

internal class DesktopAppearancePreferences : AppearancePreferences {
    private val preferences = Preferences.userRoot().node("org/basadrito/appearance")
    private val value = MutableStateFlow(ThemeMode.entries.firstOrNull { it.name == preferences.get("theme", "") } ?: ThemeMode.SYSTEM)
    override val mode = value.asStateFlow()
    override fun setMode(mode: ThemeMode) {
        preferences.put("theme", mode.name)
        value.value = mode
    }
}
