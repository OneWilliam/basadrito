package org.basadrito.app.di

import org.basadrito.app.domain.auth.AuthRepository
import org.basadrito.app.domain.courses.CoursesRepository
import org.basadrito.app.feature.auth.AuthViewModel
import org.basadrito.app.feature.dashboard.DashboardViewModel
import org.basadrito.app.feature.onboarding.MemoryOnboardingPreferences
import org.basadrito.app.feature.onboarding.OnboardingPreferences
import org.basadrito.app.feature.onboarding.OnboardingViewModel
import org.basadrito.app.designsystem.AppearancePreferences
import org.basadrito.app.designsystem.MemoryAppearancePreferences
import org.basadrito.app.domain.academic.AcademicRepository
import org.basadrito.app.domain.academic.NoAcademicRepository
import org.basadrito.app.domain.calendar.*
import org.basadrito.app.feature.calendar.CalendarViewModel

class AppContainer(
    private val authRepository: AuthRepository,
    private val coursesRepository: CoursesRepository,
    private val onboardingPreferences: OnboardingPreferences = MemoryOnboardingPreferences(),
    val appearance: AppearancePreferences = MemoryAppearancePreferences(),
    private val academicRepository: AcademicRepository = NoAcademicRepository,
    private val personalRepository: PersonalAgendaRepository = NoPersonalAgendaRepository,
    private val calendarRepository: CalendarRepository = NoCalendarRepository,
) {
    fun createOnboardingViewModel() = OnboardingViewModel(onboardingPreferences)

    fun createAuthViewModel(): AuthViewModel {
        return AuthViewModel(authRepository)
    }

    fun createDashboardViewModel(): DashboardViewModel = DashboardViewModel(coursesRepository, academic = academicRepository, personal = personalRepository)
    fun createCalendarViewModel() = CalendarViewModel(calendarRepository, personalRepository)
}
