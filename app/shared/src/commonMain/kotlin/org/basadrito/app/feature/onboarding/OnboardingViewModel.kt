package org.basadrito.app.feature.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 0: bienvenida, 1..3: presentación, 4: entrada al flujo de autenticación. */
class OnboardingViewModel(private val preferences: OnboardingPreferences) : ViewModel() {
    private val mutableStep = MutableStateFlow(if (preferences.isCompleted()) COMPLETE else 0)
    val step = mutableStep.asStateFlow()

    fun next() {
        if (mutableStep.value == COMPLETE) return
        if (mutableStep.value == COMPLETE - 1) preferences.markCompleted()
        mutableStep.value++
    }

    fun back() {
        if (mutableStep.value in 1 until COMPLETE) mutableStep.value--
    }

    companion object { const val COMPLETE = 4 }
}
