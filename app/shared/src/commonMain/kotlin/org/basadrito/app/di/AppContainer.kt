package org.basadrito.app.di

import org.basadrito.app.domain.auth.AuthRepository
import org.basadrito.app.domain.courses.CoursesRepository
import org.basadrito.app.feature.auth.AuthViewModel
import org.basadrito.app.feature.dashboard.DashboardViewModel
import org.basadrito.app.feature.onboarding.MemoryOnboardingPreferences
import org.basadrito.app.feature.onboarding.OnboardingPreferences
import org.basadrito.app.feature.onboarding.OnboardingViewModel

class AppContainer(
    private val authRepository: AuthRepository,
    private val coursesRepository: CoursesRepository,
    private val onboardingPreferences: OnboardingPreferences = MemoryOnboardingPreferences(),
) {
    fun createOnboardingViewModel() = OnboardingViewModel(onboardingPreferences)

    fun createAuthViewModel(): AuthViewModel {
        return AuthViewModel(authRepository)
    }

    fun createDashboardViewModel(): DashboardViewModel = DashboardViewModel(coursesRepository)
}
