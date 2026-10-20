package org.basadrito.app.feature.onboarding

/** Preferencia de presentación; no contiene credenciales ni representa una sesión. */
interface OnboardingPreferences {
    fun isCompleted(): Boolean
    fun markCompleted()
}

/** Las plataformas que aún no tienen almacenamiento conservan el indicador solo en memoria. */
class MemoryOnboardingPreferences : OnboardingPreferences {
    private var completed = false
    override fun isCompleted() = completed
    override fun markCompleted() { completed = true }
}
