package org.basadrito.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.basadrito.app.designsystem.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class AndroidAppearancePreferencesTest {
    @Test fun chosenAppearanceSurvivesAnotherPreferencesInstance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "appearance-test-${UUID.randomUUID()}"
        try {
            val first = AndroidAppearancePreferences(context, name)
            assertEquals(ThemeMode.SYSTEM, first.mode.value)
            first.setMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, AndroidAppearancePreferences(context, name).mode.value)
            first.setMode(ThemeMode.SYSTEM)
            assertEquals(ThemeMode.SYSTEM, AndroidAppearancePreferences(context, name).mode.value)
        } finally { context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
    }
}
