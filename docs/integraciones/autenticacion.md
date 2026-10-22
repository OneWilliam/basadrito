# Autenticación en Moodle e Intranet

## Alcance y regla de acceso

La autenticación está verificada en JVM. El contrato `AuthRepository.signIn()` recibe el usuario y las contraseñas de Moodle e Intranet. El acceso inicial se acepta únicamente si ambos proveedores autentican correctamente y la conexión provisional sigue siendo la actual.

El resultado de dominio contiene `StudentIdentity`, `intranetAvailable`, un posible requisito de evaluación docente y el indicador `sessionRemembered`; no contiene tokens ni cookies. La identidad devuelta por el repositorio procede de Moodle. La consulta del perfil de Intranet se usa para verificar acceso y extraer el nombre de esa integración, no para unir automáticamente las identidades. Cuando el perfil exige evaluación docente, se conserva la sesión y la UI espera confirmación antes de mostrar cursos.

## Recorrido visual

![Secuencia de autenticación académica](diagramas/autenticacion-secuencia.svg)


El diagrama muestra el camino de éxito y agrupa algunas respuestas HTTP. La tabla siguiente conserva los detalles necesarios para entender y reproducir la integración. Si una etapa falla, no se continúa hasta la activación.

## Responsabilidades y archivos

| Componente | Responsabilidad |
|---|---|
| [AuthRepository](../../core/domain/src/commonMain/kotlin/org/basadrito/app/domain/auth/AuthRepository.kt) | Contrato de dominio de inicio y cierre de sesión. |
| [AuthRepositoryImpl](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/repository/AuthRepositoryImpl.kt) | Coordina Moodle e Intranet, activa la sesión o descarta la conexión provisional. |
| [AcademicSession](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/session/AcademicSession.kt) | Custodia la conexión actual y el token Moodle en memoria; rechaza candidatos antiguos. |
| [IntegrationClients](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/di/IntegrationClients.kt) | Crea las fuentes que comparten un cliente HTTP aislado por conexión. |
| [MoodleDataSource](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/moodle/MoodleDataSource.kt) | Obtiene el token, consulta la identidad y valida el identificador del usuario. |
| [IntranetLoginClient](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/intranet/IntranetLoginClient.kt) | Ejecuta formularios, redirecciones, callback y comprobación de perfil. |
| [IntranetSessionParser](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/intranet/parser/IntranetSessionParser.kt) | Reconoce el perfil observado y rechaza un formulario de credenciales. |
| [Probe](../../tools/integration-probe/src/main/kotlin/org/basadrito/tools/Probe.kt) | Comprueba las integraciones por consola; no activa `AcademicSession`. |

## Solicitudes y respuestas

### Moodle

Base: `https://aula.unjbg.edu.pe`.

| Paso | Solicitud | Respuesta y comprobación |
|---|---|---|
| Token | `POST /login/token.php`; campos `username`, `password`, `service=moodle_mobile_app`. | JSON con `token`; debe estar presente y no vacío. Un HTTP 200 no garantiza que Moodle acepte las credenciales. |
| Identidad | `POST /webservice/rest/server.php`; campos `wstoken`, `wsfunction=core_webservice_get_site_info`, `moodlewsrestformat=json`. | JSON con `userid`, `username`, `fullname` y otros datos. Se rechazan respuestas de error y un `userid` no positivo. |

Los DTO representan la respuesta externa. La fuente construye `MoodleSession`; el repositorio convierte la identidad a modelos de dominio. Si falta `fullname`, se usan `firstname` y `lastname` como alternativa. El token permanece en `data`.

### Recuperar acceso y obtener otro token Moodle

Recuperar significa reutilizar material guardado y comprobar que el proveedor todavía lo acepta. Renovar significa obtener material válido cuando el anterior ha caducado. La recuperación Android está implementada; la reautenticación automática después de caducar sigue pendiente.

| Integración | Material recuperado | Comprobación al reabrir | Continuidad actual |
|---|---|---|---|
| Moodle | Token de servicios web e identidad. | `core_webservice_get_site_info` y comparación del ID guardado. | Reutilizar el token mientras Moodle lo acepte; solicitar login si lo rechaza. No depende de la cookie web `MoodleSession`. |
| Campus/Intranet | Cookies con origen, dominio, path y caducidad. | `GET /perfil`; reconocer perfil, evaluación pendiente o regreso a login. | Incorporar y guardar los `Set-Cookie` recibidos. No se ha implementado un nuevo recorrido OIDC automático a partir de una sesión Campus todavía válida. |

Se consultó el código abierto oficial de Moodle 4.5 como referencia del protocolo, sin deducir que sea la versión instalada por la universidad:

- [`login/token.php`](https://github.com/moodle/moodle/blob/MOODLE_405_STABLE/login/token.php) autentica con usuario/contraseña, declara `NO_MOODLE_COOKIES` y llama a `generate_token_for_current_user()`.
- [`core_external/util.php`](https://github.com/moodle/moodle/blob/MOODLE_405_STABLE/lib/external/classes/util.php) reutiliza un token válido o crea otro con la duración configurada por el administrador. Consultar datos con un token no implica ampliar su caducidad.
- [`privatetoken`](https://moodledev.io/general/app/development/link-handling/deep-linking#token-and-private-token) permite autologin de la app al navegador; no es un `refresh_token`. El modelo lo reconoce en la respuesta, pero la sesión actual no lo conserva ni utiliza.
- La [duración del token](https://docs.moodle.org/405/en/Moodle_app_guide_for_admins#Web_services_token_duration) depende de la configuración del sitio.

El endpoint actual devuelve `token` y `privatetoken`, sin un contrato de refresh token. Obtener otro mediante ese endpoint exige autenticar de nuevo. Basadrito no guarda contraseñas; por eso hoy pide al estudiante volver a introducirlas. Una futura opción de recordar credenciales necesita autorización explícita, protección por plataforma y manejo de cambio de contraseña, revocación y verificación adicional. Los mecanismos OAuth disponibles en otras versiones/configuraciones requieren comprobar soporte y configuración del sitio antes de cambiar de protocolo.

La sesión del navegador tiene un [timeout de inactividad](https://docs.moodle.org/405/en/Session_handling#Timeout) independiente de la duración del token de servicios web. Por ello la web puede pedir login después de una hora mientras una app continúa consultando con su token. No se ha comprobado la configuración exacta del portal; ese tiempo es una observación del usuario, no una duración garantizada por Basadrito.

También se revisó la app oficial: [`CoreAuthenticatedSite`](https://github.com/moodlehq/moodleapp/blob/main/src/core/classes/sites/authenticated-site.ts) envía su token en las consultas y contempla errores de expiración; [`CoreLoginHelper.sessionExpired()`](https://github.com/moodlehq/moodleapp/blob/main/src/core/features/login/services/login-helper.ts) conduce al recorrido `login/reconnect`. Que la app permanezca conectada durante mucho tiempo no demuestra una renovación indefinida ni que deba repetir un formulario cada hora. Estas referencias documentan el comportamiento consultado, no una prueba de la cuenta universitaria.

### Archivos de recuperación y límites

| Archivo | Función en la recuperación |
|---|---|
| [AuthViewModel](../../app/shared/src/commonMain/kotlin/org/basadrito/app/feature/auth/AuthViewModel.kt) | Solicita recuperación al iniciar y publica carga, reintento, login o acceso. |
| [AuthRepository](../../core/domain/src/commonMain/kotlin/org/basadrito/app/domain/auth/AuthRepository.kt) | Expone `restoreSession()` y fallos seguros de caducidad, red/servicio o almacenamiento; no expone secretos. |
| [AuthRepositoryImpl](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/repository/AuthRepositoryImpl.kt) | Valida Moodle e Intranet antes de activar acceso y decide conservar o descartar lo guardado. |
| [AcademicSession](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/session/AcademicSession.kt) | Crea los clientes de la cuenta, activa la conexión y rechaza escrituras de una conexión anterior. |
| [SessionCookieStorage](../../core/data/src/commonMain/kotlin/org/basadrito/app/data/session/SessionCookieStorage.kt) | Reconstruye el almacén de cookies y notifica sus cambios, manteniendo el alcance y la caducidad. |
| [AndroidSessionStore](../../core/data/src/androidMain/kotlin/org/basadrito/app/data/session/AndroidSessionStore.kt) | Lee y escribe la instantánea cifrada fuera del backup; elimina archivo y clave al cerrar sesión. |
| [BasadritoApplication](../../app/androidApp/src/main/kotlin/org/basadrito/app/BasadritoApplication.kt) | Inyecta el almacén Android en la sesión compartida por los repositorios. |

La recuperación actual trata el acceso como una instantánea conjunta: un rechazo confirmado de un proveedor descarta la instantánea y pide el login completo. No conserva todavía el acceso válido de la otra integración para reautenticar solo la que falló. Una caída temporal mantiene lo guardado y permite reintentar.

La caducidad detectada durante una consulta de cursos todavía se presenta como error de carga; no inicia un recorrido de reautenticación. Quedan pendientes ese recorrido, el acceso parcial y la renovación por integración. Estas limitaciones impiden considerar completada la continuidad de sesión solo porque funcione cerrar y reabrir la app.

### Intranet y Campus

Campus (`https://campus.unjbg.edu.pe`) recibe las credenciales. Intranet (`https://intranet.unjbg.edu.pe`) entrega los datos académicos. Ambos participan en el intercambio de acceso.

| Etapa del cliente | Solicitud | Respuesta y comprobación |
|---|---|---|
| `LOGIN_PAGE` | `GET` a Campus `/login`, siguiendo las redirecciones permitidas. | HTML: extraer `__RequestVerificationToken`, que no debe estar vacío. |
| `CREDENTIALS` | `POST` a Campus `/login`: `UserName`, `Password`, `System=CAMPUS`, `SystemLogin=0` y token antiforgery. | Se permite HTTP 2xx/3xx; el cliente conserva las cookies. Esta respuesta aún no acredita acceso a Intranet. |
| `AUTHORIZATION` | `GET` a la raíz de Intranet y redirecciones de autorización. | HTML del formulario oculto: extraer `code`, `state`, `session_state`. El código de autorización debe existir y no estar vacío. |
| `CALLBACK` | `POST` a Intranet `/signin-oidc` con esos parámetros y `scope=openid profile roles`. | Se permite HTTP 2xx/3xx y se conservan las cookies emitidas. |
| `PROFILE` | `GET` a Intranet `/perfil`, con las cookies del mismo cliente. | Exigir HTTP 200 y perfil reconocido; rechazar un formulario con `UserName` y `Password`. |

Las redirecciones se manejan explícitamente; HTTPS y los hosts Campus/Intranet delimitan los destinos aceptados. Las URLs de autorización y sus parámetros temporales se obtienen en cada ejecución; no se copia una URL de login del navegador como configuración fija.

`isAuthenticated()` repite esa comprobación de `/perfil` sin pedir credenciales. Las cookies del navegador no se comparten con el cliente Kotlin.

## Activación, fallo y cierre de sesión

1. `begin()` descarta la conexión anterior y crea una nueva, inicialmente provisional.
2. El repositorio autentica Moodle y después Intranet, en ese orden. Si Moodle falla, no intenta Intranet.
3. `activate(candidate, moodle)` solo acepta el candidato vigente. Un logout o un nuevo login pueden invalidarlo mientras llegan respuestas.
4. Ante fallo se descarta el candidato y se devuelve un error sin secretos. Ante cancelación se descarta y se propaga `CancellationException`.
5. `signOut()` elimina la referencia de sesión y del token Moodle, y cierra el cliente que custodia las cookies. Esto es cierre local; no implementa revocación del token o logout remoto en los portales.

`AcademicSession` mantiene la conexión activa en memoria. En Android se añade recuperación entre ejecuciones: `SessionStore` conserva una instantánea cifrada y `restoreSession()` comprueba identidad Moodle y acceso al perfil Intranet sin reenviar contraseñas. Solo publica acceso si ambas comprobaciones permiten continuar; la evaluación pendiente sigue siendo una barrera de navegación. La red caída o una respuesta no reconocida permiten reintentar sin borrar el archivo. Un rechazo confirmado del token/cookies o una identidad distinta solicita nueva autenticación. Logout elimina el archivo protegido y la clave; una respuesta tardía no puede volver a guardarlos.

La recuperación protegida no implica una sesión indefinida ni renovación automática mediante contraseñas. Escritorio, iOS y web todavía necesitan sus adaptadores de persistencia. Los detalles del archivo, protección, caducidad y límites están en [datos y persistencia](../diseno/datos-y-persistencia.md#sesión-protegida). Tampoco se promete borrado criptográfico de las cadenas de credenciales de la memoria JVM.

Si la contraseña Intranet se pasa vacía al repositorio, el código actual utiliza la de Moodle como alternativa. El probe exige ambas contraseñas no vacías; si son iguales, deben configurarse en los dos campos.

## Reproducción

Desde la raíz del repositorio, en PowerShell:

~~~powershell
.\gradlew.bat :core:data:jvmTest :tools:integration-probe:classes
~~~

Este comando ejecuta pruebas con HTTP simulado y compila la herramienta. No carga `.env` ni consulta las cuentas reales.

Para comprobar una cuenta autorizada, configurar localmente `BASADRITO_USERNAME`, `BASADRITO_MOODLE_PASSWORD` y `BASADRITO_INTRANET_PASSWORD` en `.env` y ejecutar:

~~~powershell
.\gradlew.bat :tools:integration-probe:run
~~~
