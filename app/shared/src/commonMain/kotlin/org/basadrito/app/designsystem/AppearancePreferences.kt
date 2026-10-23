package org.basadrito.app.designsystem

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("Usar el sistema"), LIGHT("Claro"), DARK("Oscuro") }

interface AppearancePreferences {
    val mode: StateFlow<ThemeMode>
    fun setMode(mode: ThemeMode)
}

class MemoryAppearancePreferences(initial: ThemeMode = ThemeMode.SYSTEM) : AppearancePreferences {
    private val value = MutableStateFlow(initial)
    override val mode = value.asStateFlow()
    override fun setMode(mode: ThemeMode) { value.value = mode }
}
