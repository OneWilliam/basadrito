package org.basadrito.app.domain.calendar

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.basadrito.app.domain.academic.AcademicClass
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours

/** Civil dates belong to the institution's UTC-05 zone, independently of the device zone. */
fun institutionToday(): String = (Clock.System.now() - 5.hours).toString().take(10)
fun institutionDate(epochSeconds: Long): String = (Instant.fromEpochSeconds(epochSeconds) - 5.hours).toString().take(10)
fun institutionTime(epochSeconds: Long): String = (Instant.fromEpochSeconds(epochSeconds) - 5.hours).toString().substring(11, 16)
fun validCivilDate(date: String): Boolean = Regex("\\d{4}-\\d{2}-\\d{2}").matches(date) &&
    runCatching { Instant.parse("${date}T00:00:00Z").toString().startsWith(date) }.getOrDefault(false)

data class CalendarMonth(val year: Int, val month: Int) {
    init { require(year in 1..9998 && month in 1..12) }
    val key: String get() = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}"
    val firstDate: String get() = "$key-01"
    val days: Int get() = when (month) { 2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30; else -> 31 }
    val mondayOffset: Int get() = (((Instant.parse("${firstDate}T00:00:00Z").epochSeconds / 86400 + 3) % 7 + 7) % 7).toInt()
    fun date(day: Int): String { require(day in 1..days); return "$key-${day.toString().padStart(2, '0')}" }
    fun shift(delta: Int): CalendarMonth {
        val index = year * 12 + month - 1 + delta
        return CalendarMonth(index / 12, index % 12 + 1)
    }
    companion object {
        fun containing(date: String): CalendarMonth { require(validCivilDate(date)); return CalendarMonth(date.take(4).toInt(), date.substring(5, 7).toInt()) }
    }
}

enum class PersonalKind { TASK, EVENT, EXAM }
data class PersonalItem(val id: String, val title: String, val description: String = "", val kind: PersonalKind,
    val date: String? = null, val time: String? = null, val completed: Boolean = false) {
    fun validate() {
        require(title.isNotBlank() && title.length <= 240) { "Escribe un título de hasta 240 caracteres." }
        require(description.length <= 4000) { "La descripción es demasiado larga." }
        require(date == null || validCivilDate(date)) { "Usa una fecha válida: AAAA-MM-DD." }
        require(time == null || (date != null && Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(time))) { "Usa una hora válida: HH:mm." }
        require(kind == PersonalKind.TASK || date != null) { "El evento necesita una fecha." }
    }
}
interface PersonalAgendaRepository {
    fun observe(): Flow<List<PersonalItem>>
    suspend fun save(item: PersonalItem): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
}
object NoPersonalAgendaRepository : PersonalAgendaRepository {
    override fun observe() = flowOf(emptyList<PersonalItem>())
    override suspend fun save(item: PersonalItem) = Result.failure<Unit>(IllegalStateException("Agenda no disponible."))
    override suspend fun delete(id: String) = Result.failure<Unit>(IllegalStateException("Agenda no disponible."))
}

enum class CalendarOrigin { CLASS, MOODLE, PERSONAL_TASK, PERSONAL_EVENT, PERSONAL_EXAM }
data class CalendarEvent(val id: String, val title: String, val detail: String, val date: String, val time: String?,
    val origin: CalendarOrigin, val completed: Boolean? = null)
data class CalendarMonthData(val classes: List<AcademicClass>, val updatedAtMillis: Long)
data class CalendarFeed(val events: List<CalendarEvent> = emptyList(), val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false, val storageFailed: Boolean = false, val hasSchedule: Boolean = false,
    val hasTasks: Boolean = false)
interface CalendarRepository {
    fun observe(month: CalendarMonth): Flow<CalendarFeed>
    suspend fun refresh(month: CalendarMonth, refreshTasks: Boolean = false)
}
object NoCalendarRepository : CalendarRepository {
    override fun observe(month: CalendarMonth) = flowOf(CalendarFeed())
    override suspend fun refresh(month: CalendarMonth, refreshTasks: Boolean) = Unit
}
