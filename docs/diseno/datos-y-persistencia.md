# Datos, DB y persistencia de sesión

## Almacenamiento implementado

| Contenido | Adaptador | Alcance |
|---|---|---|
| Cursos de Moodle y fecha de actualización | Room 2.8.5 / SQLite 2.7.1, módulo `core/database` | Android y JVM; entrada iOS preparada. |
| Créditos, horario, asistencia, docentes, tareas, clases mensuales y agenda propia | Mismo archivo Room, esquema 2 | Separados por proveedor y cuenta; se conservan al cerrar sesión. |
| Apariencia claro/oscuro/sistema | Preferencias de plataforma | Independiente de las cuentas y de Room. |
| Token, identidad y cookies | `AndroidSessionStore` | Android: AES-256-GCM y Android Keystore. |
| Sesiones web y datos propios del servidor | Pendiente | BFF y almacenamiento web pospuestos. |

Room no almacena contraseñas, tokens ni cookies. Su archivo Android reside en el directorio privado `noBackupFilesDir`, excluido del respaldo y transferencia. La DB académica no está cifrada por Room: su aislamiento frente a otras aplicaciones depende del almacenamiento privado del sistema. El cifrado de la sesión es una responsabilidad distinta.

La [guía de Room KMP](https://developer.android.com/kotlin/multiplatform/room) orienta la configuración. `core/database` declara Android, JVM e iOS; no se introduce Room en los fuentes compartidos que compilan a JS/Wasm. Los esquemas JSON 1 y 2 se versionan. El esquema 2 añade tablas y fechas de curso conservando los datos existentes.

## Archivos y recorrido

| Archivo | Responsabilidad |
|---|---|
| [CoursesRepository](../../core/domain/src/commonMain/kotlin/org/basadrito/app/domain/courses/CoursesRepository.kt) | Modelo propio de cursos, origen y actualización, sin SQL. |
| [CourseCache](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/cache/CourseCache.kt) | Puerto de almacenamiento e identidad del propietario. |
| [CoursesRepositoryImpl](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/repository/CoursesRepositoryImpl.kt) | Obtiene el propietario de la sesión activa, consulta caché y Moodle, normaliza y guarda. |
| [AcademicDatabase](../../core/database/src/commonMain/kotlin/org/basadrito/app/database/AcademicDatabase.kt) | Entidades, consultas por propietario y transacciones. |
| [RoomCourseCache](../../core/database/src/commonMain/kotlin/org/basadrito/app/database/RoomCourseCache.kt) | Implementa el puerto y serializa operaciones. |
| [DashboardViewModel](../../app/shared/src/commonMain/kotlin/org/basadrito/app/feature/dashboard/DashboardViewModel.kt) | Observa los cursos de la cuenta y solicita una actualización compartida entre destinos. |
| [AuthRepositoryImpl](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/repository/AuthRepositoryImpl.kt) | Recupera acceso protegido, valida proveedores y decide si permite lectura local. |

La UI llama al ViewModel; este consume el contrato de dominio. El repositorio obtiene el ID de Moodle desde `AcademicSession`, lee la instantánea de esa cuenta y consulta el proveedor si está habilitado. Room reemplaza solo esa instantánea en una transacción. El resultado llega a `UiState` con origen, fecha y fallo de actualización; Compose lo representa sin conocer SQL ni tokens.

## Observación y actualización coordinada

`CoursesRepository.observeCourses()` ofrece datos y progreso de actualización de la sesión activa. `CourseCache.observe(owner)` conecta ese contrato con Room. El DAO observa ambas tablas con un único `LEFT JOIN`: cursos y fecha pertenecen a la misma instantánea confirmada. Distingue una instantánea vacía de la ausencia de una descarga y filtra por proveedor y propietario.

Inicio y Cursos conservan sus propios ViewModels y scroll, pero reciben los cambios de la misma DB. La primera entrada solicita `refreshCourses(initial = true)`; tras una descarga correcta, abrir el otro destino reutiliza el resultado. Una actualización manual fuerza otra consulta. Las solicitudes simultáneas comparten el trabajo HTTP, su progreso y los fallos. Un intento fallido permite reintentar; no completa la descarga inicial.

El trabajo pertenece a la conexión autenticada, no al ViewModel que lo pidió. Cancelar un consumidor no interrumpe al otro. Logout cancela el trabajo y la observación deja de entregar sus cursos. Un nuevo login, incluso con el mismo ID, obtiene otra coordinación. Se mantienen las comprobaciones de vigencia antes de guardar y el rollback dentro de la transacción.

Un fallo al guardar conserva el resultado remoto en memoria con un aviso. Si falla la consulta observable, se informa del error y una nueva actualización vuelve a abrir la observación. No hay temporizador, sincronización en segundo plano ni cola de escrituras pendientes.

## Operaciones por funcionalidad

El SQL vive en los DAO de `core/database`; los contratos de pantalla en `core/domain`, y la coordinación con proveedores y caché en `core/data`. El ViewModel no ejecuta SQL ni llama al cliente Moodle/Intranet. Los diagnósticos separan `StorageArea` y `StorageAction`: no hay una constante por combinación de operación y funcionalidad.

Calendario tiene su propio `CalendarRepository`, modelos y consultas por cuenta y mes. Combina horario de Intranet, entregas de Moodle y agenda propia. `PersonalAgendaRepository` realiza las operaciones locales de crear, editar, completar y eliminar tareas/eventos/exámenes. Sus escrituras no pasan por `CoursesRepository`. No hay un servidor de sincronización de eventos propios en esta entrega.

La futura sincronización de escrituras con un servidor necesita una cola persistente, identificadores estables, reintentos e idempotencia, además de una política de conflictos. Eso sigue siendo una propuesta. Hoy las consultas académicas actualizan sus instantáneas locales de forma transaccional y la agenda propia se escribe directamente en Room, también sin conexión.

## Esquema actual

| Tabla | Clave primaria | Contenido |
|---|---|---|
| `course_snapshots` | Proveedor + ID externo de usuario | Fecha de la última actualización correcta. |
| `courses` | Proveedor + ID externo de usuario + ID externo de curso | Nombre, nombre corto, orden y fechas opcionales de inicio y fin. |
| `academic_snapshots` | Clave derivada de proveedor y propietario, con índice único de ambos | Créditos y fechas independientes por fuente. |
| `academic_classes`, `academic_attendance`, `academic_teachers`, `academic_tasks` | Propietario + posición o ID externo | Horario, asistencia, docentes y entregas; relaciones con la instantánea de la cuenta. |
| `calendar_months`, `calendar_classes` | Propietario + mes y posición | Descarga de clases por intervalo, incluida una descarga vacía correcta. |
| `personal_agenda` | Proveedor + propietario + UUID | Tareas, eventos y exámenes propios, fecha/hora opcional según tipo y estado de finalización. |

Un resultado correcto sin cursos también tiene una instantánea. Una red fallida sin instantánea se presenta como error. Las fuentes académicas se guardan por separado: una actualización parcial conserva el último resultado correcto de las demás. Ausencia y lista vacía son estados distintos. Las relaciones de Room se consultan en transacciones. Notas detalladas, historial por períodos y archivos adjuntos todavía no tienen persistencia en este esquema.

Las fechas `...AtMillis` son instantes UTC en milisegundos (`Long`/`INTEGER`), no contadores de filas. Las fechas de Moodle `...AtEpochSeconds` usan segundos y lo indican en el nombre. SQLite utiliza enteros con representación de hasta 8 bytes; el número de dígitos no se almacena como texto. Las fechas civiles de agenda usan `AAAA-MM-DD` y horas `HH:mm` en la zona institucional UTC-05. Se comprueban mes, día y hora antes de guardar; no se ordenan fechas con formato dependiente del idioma. [Tipos de SQLite](https://www.sqlite.org/datatype3.html).

Los créditos representan aprobados sobre los cursos listados en la malla, deduplicados por código e intento; no se afirma que sumar alternativas electivas equivalga al requisito oficial de egreso. No se usa el total del mockup como dato institucional. La semana solo se calcula si todos los cursos comparten fechas válidas y el intervalo es razonable; no se inventa una duración de 17 semanas. Las entregas con estado desconocido no cuentan como cero pendientes. Las uniones entre Moodle e Intranet usan código exacto o nombre normalizado inequívoco.

## Retención y cuentas

Se admite una sesión activa y cachés de varias cuentas. El proveedor y el ID externo comprobado identifican cada propietario; el nombre visible o el código introducido en el formulario no conceden acceso a una caché.

| Acción | Acceso protegido | Caché académica |
|---|---|---|
| Cerrar la app | Se conserva en Android. | Se conserva por cuenta. |
| Cerrar sesión | Se elimina el archivo cifrado y su clave; se invalida la conexión. | Se conserva, bloqueada para la cuenta deslogueada. |
| Ingresar con otra cuenta | Se crea una sesión independiente. | Solo se consulta la del nuevo ID. |
| Volver a una cuenta deslogueada | Exige autenticarse nuevamente. | Se reutiliza la instantánea de su ID. |
| Fallo al actualizar | No ejecuta un borrado global. | Conserva el último resultado correcto. |

La UI no recibe una operación para consultar un ID arbitrario. Las respuestas pendientes comprueban que su conexión sigue vigente antes de publicar o escribir; si cambia durante la transacción SQL, se revierte la sustitución. Al salir se retira la navegación privada y se liberan sus ViewModels.

La caché no caduca por tiempo en este primer alcance; conserva la fecha de actualización y permite solicitar otra consulta. El borrado selectivo existe en el adaptador, pero su control en Ajustes y un selector de cuentas siguen pendientes. No se mantienen múltiples sesiones protegidas simultáneas.

## Recuperación y lectura local

Android guarda token y cookies con [Android Keystore](https://developer.android.com/privacy-and-security/keystore), AES-256-GCM, IV nuevo por escritura y escritura atómica en `noBackupFilesDir`. Los detalles del formato y los secretos permanecen en `data`. No se almacenan contraseñas ni se utiliza Room para recuperar sesiones.

1. `restoreSession()` abre la instantánea protegida y reconstruye el cliente con sus cookies.
2. Comprueba identidad Moodle y acceso a Intranet. Un ID diferente, token rechazado o redirección de login exige autenticarse otra vez.
3. Una evaluación pendiente mantiene su barrera de acceso. Solo un acceso completo permite guardar la autorización para lectura local posterior.
4. Si falla la conexión o hay HTTP 5xx, puede abrirse `LOCAL_ONLY` cuando existe acceso protegido previamente validado y una instantánea del mismo propietario. No se afirma que los proveedores hayan validado la sesión durante esa apertura.
5. Una respuesta desconocida, un fallo al abrir el almacén o datos de otra cuenta no habilitan lectura local. Sin caché propia se ofrece reintentar.
6. En `LOCAL_ONLY`, las consultas de cursos no hacen solicitudes HTTP. Ajustes permite comprobar la conexión para recuperar acceso verificado.

Una caché por sí sola nunca recupera la cuenta después de logout. Un fallo temporal conserva el archivo protegido para reintentar; un acceso rechazado elimina ese archivo y conserva los datos académicos bloqueados. Las cookies mantienen sus restricciones y caducidad absoluta: reiniciar no renueva `Max-Age`.

## Manejo de fallos de almacenamiento

Una lectura sin filas devuelve ausencia; una consulta fallida devuelve un error de almacenamiento. El adaptador Room traduce `SQLiteException` a `CourseCacheStorageException`; la cancelación y las excepciones inesperadas se propagan.

| Fallo | Comportamiento |
|---|---|
| Leer cursos guardados | Se informa del fallo. Si hay conexión, se intenta consultar Moodle. |
| Guardar cursos descargados | Se conservan en pantalla con un aviso de que no quedaron disponibles sin conexión. |
| Leer caché durante recuperación local | Se devuelve un fallo de almacenamiento y se conserva el acceso protegido para reintentar. |
| Guardar acceso protegido | El resultado marca que la sesión no quedó recordada y registra el diagnóstico. |
| Cancelación | Se propaga y no se convierte en ausencia, éxito ni aviso de almacenamiento. |

Los diagnósticos de almacenamiento registran únicamente área, acción y tipo de excepción, sin mensajes originales, rutas, identificadores ni secretos. El diagnóstico de autenticación conserva proveedor, etapa, tipo y estado HTTP. Los fallos de los propios callbacks de diagnóstico no se silencian; la recuperación libera el cliente provisional mediante `finally`, incluso si falla ese callback.

Escritorio tiene DB y preferencias persistentes, pero su sesión sigue en memoria: al reabrir exige login. iOS tiene composición de DB y preferencias preparada; Keychain y validación de esa plataforma quedan pendientes. La caché iOS usa el directorio de cachés del sistema y puede ser depurada por este.

## Pruebas y evolución

| Pruebas | Verifican |
|---|---|
| Room JVM | Separación de cuentas/proveedores, reapertura, borrado selectivo, instantánea vacía y rollback al cambiar de cuenta. |
| Repositorios con proveedores controlados | Lectura de la cuenta activa, retención ante fallos, rechazo de respuestas tardías y recuperación local solo con acceso protegido propio. |
| Android 16 | Almacén cifrado, reapertura de Room, separación de cuentas y preferencias de apariencia. |

Quedan fuera de este alcance la sincronización en segundo plano, la conservación de archivos descargados y el historial por períodos en Room. Los nuevos conjuntos académicos tendrán sus propios contratos y claves. El BFF podrá incorporar PostgreSQL cuando necesite datos propios persistentes; no se replica la DB universitaria.
