# Obtención de datos académicos

Las llamadas HTTP, cookies y tokens se administran en `core/data`. Los contratos consumidos por pantallas pertenecen a `core/domain`; Room y SQL pertenecen a `core/database`. La presentación no importa clientes HTTP ni modelos de proveedores.

## Moodle

[MoodleDataSource](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/moodle/MoodleDataSource.kt) utiliza [MoodleClient](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/moodle/MoodleClient.kt). Los DTO están en [MoodleModels](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/moodle/dto/MoodleModels.kt).

| Datos | Operación |
|---|---|
| Token e identidad | `login`, `getSiteInfo`. |
| Cursos | `getCourses`. |
| Notas generales | `getGrades`. |
| Tareas por curso | `getAssignments`; conserva el ID del curso de cada tarea. |
| Estado de entrega | `getSubmissionStatus`; distingue pendiente, enviada y desconocida. |
| Secciones, actividades y archivos | `getCourseContents`. |
| Usuarios y docentes | `MoodleClient.getEnrolledUsers`, `MoodleDataSource.getCourseTeachers`. |
| Archivo autenticado | `downloadFile(token, fileUrl)` devuelve bytes; solo admite el origen Moodle y rutas de archivos. |

Los errores de docentes se informan como fallo de fuente. Un estado de entrega desconocido no significa completado ni cero pendientes. El token no se entrega a la UI ni se añade a un origen arbitrario.

## Intranet

[IntranetDataSource](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/intranet/IntranetDataSource.kt) utiliza `IntranetClient` e `IntranetLoginClient`. El HTML/JSON se interpreta en `intranet/parser`; los DTO están en [IntranetModels](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/intranet/dto/IntranetModels.kt).

| Datos | Operación |
|---|---|
| Login OIDC y acceso | `login`, `checkAccess`, sesión y cookies compartidas. |
| Perfil | `getProfile`; nombre, código, carrera, correo, teléfono y dirección disponibles. |
| Períodos | `getPeriods`. |
| Horario semanal | `getWeekSchedule(todayIso)`. |
| Horario de un intervalo | `getSchedule(startIso, endIso)`; Calendario consulta cada mes. |
| Progreso por ciclo | `getAcademicProgress(cycles)`; los ciclos del resumen proceden de la malla. |
| Registros de un período | `getPeriodRecords(period, isCurrent)`; la selección es explícita. |
| Historial | `getAcademicHistory(currentPeriodId)`. |
| Malla | `getMalla`; código, nombre, créditos, horas, prerrequisito y ciclo. |
| Asistencia e inasistencias | `getAttendance`. |
| Notas y unidades | `getGrades(periodId, periodName)`, `getGradeDetails(periodId)`. |
| Sílabo | `downloadSyllabus(studentSectionId, syllabusTeacherId)`. |

La primera opción de períodos no se interpreta automáticamente como el período actual. El porcentaje de inasistencia del proveedor se diferencia de la asistencia calculada con asistidas/dictadas. La descripción del horario se conserva como descripción; no se convierte en un docente estructurado por suposición.

## Contratos de pantalla

| Contrato | Responsabilidad |
|---|---|
| `CoursesRepository` | Cursos y actualización compartida de la cuenta. |
| `AcademicRepository` | Créditos de la malla listada, horario, docentes, asistencia y tareas con éxito parcial por fuente. |
| `CalendarRepository` | Agenda mensual con clases, entregas y elementos personales, conservando origen y fechas. |
| `PersonalAgendaRepository` | Crear, editar, completar y eliminar tareas propias; crear, editar y eliminar eventos/exámenes personales. |

Las tareas propias pueden no tener fecha. Cuando tienen fecha aparecen también en Calendario. Se guardan por cuenta y se conservan al cerrar sesión, sin quedar accesibles a otra identidad. Su almacenamiento es local; no se anuncia sincronización con un servidor.

Inicio y Cursos ya consumen los contratos académicos. Calendario tiene ruta, ViewModel y operaciones propios. Notas detalladas, contenidos de curso y descarga/apertura de archivos disponen de adaptadores de datos, pero sus pantallas completas siguen pendientes. Planificación curricular, widget de notas, eventos institucionales y recordatorios no se presentan como funciones terminadas.

La configuración futura de planificación contempla cursos aprobados/seleccionados manualmente, horarios con código de curso, día, inicio, fin e instancia, límites configurables de créditos y cursos previstos por ciclo. No hay un límite institucional fijo incorporado al cálculo de avance.

## Reglas comprobables

Los créditos se deduplican por código e intento. Se distingue el total de la malla listada del requisito oficial de egreso, que puede depender de electivos. No se toma el total de un mockup como dato institucional. La semana requiere fechas comunes válidas de cursos; un intervalo desconocido no produce una semana ficticia.

Las pruebas cubren parsers, agregación, integración de respuestas controladas, límites de origen de archivos, uso local sin HTTP, aislamiento por cuenta, conservación de cursos existentes y operaciones personales transaccionales. La UI comprueba créditos y una tarea propia compartida entre Inicio y Calendario.
