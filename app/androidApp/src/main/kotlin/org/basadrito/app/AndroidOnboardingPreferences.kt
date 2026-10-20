package org.basadrito.app

import android.content.Context
import org.basadrito.app.feature.onboarding.OnboardingPreferences

class AndroidOnboardingPreferences(context: Context) : OnboardingPreferences {
    private val preferences = context.applicationContext.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
    override fun isCompleted() = preferences.getBoolean("completed", false)
    override fun markCompleted() { preferences.edit().putBoolean("completed", true).apply() }
}
