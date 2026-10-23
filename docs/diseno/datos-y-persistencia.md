# Datos, DB y persistencia de sesión

## Almacenamiento implementado

| Contenido | Adaptador | Alcance |
|---|---|---|
| Cursos de Moodle y fecha de actualización | Room 2.8.5 / SQLite 2.7.1, módulo `core/database` | Android y JVM; entrada iOS preparada. |
| Apariencia claro/oscuro/sistema | Preferencias de plataforma | Independiente de las cuentas y de Room. |
| Token, identidad y cookies | `AndroidSessionStore` | Android: AES-256-GCM y Android Keystore. |
| Sesiones web y datos propios del servidor | Pendiente | BFF y almacenamiento web pospuestos. |

Room no almacena contraseñas, tokens ni cookies. Su archivo Android reside en el directorio privado `noBackupFilesDir`, excluido del respaldo y transferencia. La DB académica no está cifrada por Room: su aislamiento frente a otras aplicaciones depende del almacenamiento privado del sistema. El cifrado de la sesión es una responsabilidad distinta.

La [guía de Room KMP](https://developer.android.com/kotlin/multiplatform/room) orienta la configuración. `core/database` declara Android, JVM e iOS; no se introduce Room en los fuentes compartidos que compilan a JS/Wasm. El esquema JSON se versiona y no se utiliza una migración destructiva. Las migraciones se añadirán al evolucionar el esquema inicial.

## Archivos y recorrido

| Archivo | Responsabilidad |
|---|---|
| [CoursesRepository](../../core/domain/src/commonMain/kotlin/org/basadrito/app/domain/courses/CoursesRepository.kt) | Modelo propio de cursos, origen y actualización, sin SQL. |
| [CourseCache](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/cache/CourseCache.kt) | Puerto de almacenamiento e identidad del propietario. |
| [CoursesRepositoryImpl](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/repository/CoursesRepositoryImpl.kt) | Obtiene el propietario de la sesión activa, consulta caché y Moodle, normaliza y guarda. |
| [AcademicDatabase](../../core/database/src/commonMain/kotlin/org/basadrito/app/database/AcademicDatabase.kt) | Entidades, consultas por propietario y transacciones. |
| [RoomCourseCache](../../core/database/src/commonMain/kotlin/org/basadrito/app/database/RoomCourseCache.kt) | Implementa el puerto y serializa operaciones. |
| [DashboardViewModel](../../app/shared/src/commonMain/kotlin/org/basadrito/app/feature/dashboard/DashboardViewModel.kt) | Publica primero la instantánea disponible y luego el resultado de actualizar. |
| [AuthRepositoryImpl](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/repository/AuthRepositoryImpl.kt) | Recupera acceso protegido, valida proveedores y decide si permite lectura local. |

La UI llama al ViewModel; este consume el contrato de dominio. El repositorio obtiene el ID de Moodle desde `AcademicSession`, lee la instantánea de esa cuenta y consulta el proveedor si está habilitado. Room reemplaza solo esa instantánea en una transacción. El resultado llega a `UiState` con origen, fecha y fallo de actualización; Compose lo representa sin conocer SQL ni tokens.

## Esquema actual

| Tabla | Clave primaria | Contenido |
|---|---|---|
| `course_snapshots` | Proveedor + ID externo de usuario | Fecha de la última actualización correcta. |
| `courses` | Proveedor + ID externo de usuario + ID externo de curso | Nombre, nombre corto y orden. |

Un resultado correcto sin cursos también tiene una instantánea. Una red fallida sin instantánea se presenta como error, no como una sincronización vacía. Todavía no se guardan notas, asistencia, períodos, tareas ni archivos adjuntos. Los cursos se conservan como la lista devuelta por Moodle, sin inventar una clasificación por período.

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

La caché no caduca por tiempo en este primer alcance; su antigüedad se muestra y se puede solicitar actualización. El borrado selectivo existe en el adaptador, pero su control en Ajustes y un selector de cuentas siguen pendientes. No se mantienen múltiples sesiones protegidas simultáneas.

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

Los diagnósticos de almacenamiento registran únicamente operación y tipo de excepción, sin mensajes originales, rutas, identificadores ni secretos. El diagnóstico de autenticación conserva proveedor, etapa, tipo y estado HTTP. Los fallos de los propios callbacks de diagnóstico no se silencian; la recuperación libera el cliente provisional mediante `finally`, incluso si falla ese callback.

Escritorio tiene DB y preferencias persistentes, pero su sesión sigue en memoria: al reabrir exige login. iOS tiene composición de DB y preferencias preparada; Keychain y validación de esa plataforma quedan pendientes. La caché iOS usa el directorio de cachés del sistema y puede ser depurada por este.

## Pruebas y evolución

| Pruebas | Verifican |
|---|---|
| Room JVM | Separación de cuentas/proveedores, reapertura, borrado selectivo, instantánea vacía y rollback al cambiar de cuenta. |
| Repositorios con proveedores controlados | Lectura de la cuenta activa, retención ante fallos, rechazo de respuestas tardías y recuperación local solo con acceso protegido propio. |
| Android 16 | Almacén cifrado, reapertura de Room, separación de cuentas y preferencias de apariencia. |

Quedan fuera de este alcance la sincronización en segundo plano, la observación continua de tablas, archivos descargados, períodos y migraciones entre versiones de esquema. Los nuevos conjuntos académicos tendrán sus propios contratos y claves. El BFF podrá incorporar PostgreSQL cuando necesite datos propios persistentes; no se replica la DB universitaria.
