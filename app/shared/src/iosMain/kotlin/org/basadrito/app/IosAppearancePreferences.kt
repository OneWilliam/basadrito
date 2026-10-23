package org.basadrito.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.basadrito.app.designsystem.*
import platform.Foundation.NSUserDefaults

internal class IosAppearancePreferences : AppearancePreferences {
    private val preferences = NSUserDefaults.standardUserDefaults
    private val value = MutableStateFlow(ThemeMode.entries.firstOrNull { it.name == preferences.stringForKey("appearance.theme") } ?: ThemeMode.SYSTEM)
    override val mode = value.asStateFlow()
    override fun setMode(mode: ThemeMode) {
        preferences.setObject(mode.name, "appearance.theme")
        value.value = mode
    }
}
