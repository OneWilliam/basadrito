# Datos, DB y persistencia de sesión

## Responsabilidades de almacenamiento

![Almacenamiento previsto por responsabilidad](../diagramas/persistencia.svg)

| Necesidad | Almacenamiento objetivo | Razón |
|---|---|---|
| Cursos, notas, asistencia y calendario consultados | DB local SQLite | Consultar sin conexión y observar actualizaciones. |
| Tema, filtros y preferencias pequeñas | Almacenamiento de preferencias de plataforma | Evitar tablas para valores simples. |
| Tokens/cookies recuperables | Almacenamiento protegido específico de plataforma | Mantener acceso entre aperturas sin mezclar secretos con datos académicos. |
| Datos propios compartidos entre dispositivos | DB del BFF cuando el requisito lo exija | Persistir preferencias o recordatorios del servicio. |

La custodia `AcademicSession` actual solo vive en memoria. Una sesión externa no puede garantizar duración indefinida: Moodle o Campus pueden invalidarla. La experiencia buscada es permanecer conectado mientras el acceso sea recuperable, y pedir autenticación cuando ya no lo sea.

## Elección de DB local

**Propuesta:** SQLite con Room para Android primero. Los datos son relacionales por estudiante, período y curso; las transacciones y consultas observables ayudan a construir la consulta sin conexión. Room es un adaptador de persistencia en `data`; sus entidades y anotaciones no llegan a `domain`.

La [guía oficial de Room KMP](https://developer.android.com/kotlin/multiplatform/room) describe su configuración multiplataforma. Se verificará una versión estable compatible con nuestro Kotlin/AGP/KSP antes de añadirla. No copiamos una versión alpha solo porque figure en un ejemplo. Web no se considera cubierta por esa elección: se separan las fuentes nativas/JVM o un módulo de almacenamiento, y web implementa el contrato con su almacenamiento apropiado.

[SQLDelight](https://sqldelight.github.io/sqldelight/2.1.0/) es la alternativa a evaluar si queremos escribir SQL explícito y compartir consultas con otros drivers. Elegimos entre ambas al implementar caché, con un experimento Android y una compilación de los destinos declarados; no instalamos dos ORM para el mismo propósito. La elección y sus versiones se concretarán con ese experimento.

## Primer esquema útil

| Tabla propuesta | Clave / contenido | Regla |
|---|---|---|
| `account` | ID interno, identificadores externos comprobados | No deducir que el nombre identifica de forma única. |
| `academic_period` | Proveedor + ID de período | Los períodos de proveedores distintos necesitan mapeo explícito. |
| `course_snapshot` | Cuenta + proveedor + período + ID externo; título y fecha de actualización | Guardar un modelo normalizado; no el HTML entero por defecto. |
| `sync_metadata` | Cuenta + recurso; última actualización y estado | Permitir mostrar antigüedad y reintentar. |
| Notas/asistencia | Cuenta + curso + identificador del registro | Añadir cuando el requisito y la evidencia del proveedor estén claros. |

Estos nombres son diseño, no un esquema ya creado. Las claves previenen cruces entre cuentas y períodos. No se fuerza la unión de cursos Moodle e Intranet por coincidencia de nombre: se necesita evidencia para un mapeo confiable.

## Consulta y actualización

![Recorrido de una consulta con caché](../diagramas/recorrido-consulta.svg)

1. La UI observa el repositorio; este lee una instantánea local filtrada por cuenta y período.
2. Si hay datos, los emite con fecha de actualización. Si no hay, representa ausencia/carga; no inventa una lista vacía como resultado de red fallida.
3. Al solicitar actualización, consulta al proveedor con la sesión vigente.
4. Valida y mapea la respuesta. Una transacción reemplaza únicamente la instantánea correspondiente y actualiza metadatos.
5. La observación de DB emite el nuevo resultado. Si falla la red, conserva la instantánea anterior y comunica el error y su antigüedad.
6. Una respuesta de una cuenta anterior no puede escribir en la nueva: la operación conserva el identificador/generación de sesión y comprueba vigencia antes de persistir.

Esta es nuestra política propuesta, apoyada por la [guía de aplicaciones offline-first](https://developer.android.com/topic/architecture/data-layer/offline-first). Un login nuevo sigue exigiendo ambos proveedores; consultar caché sin conexión no equivale a verificar autenticación remota. La sincronización automática se añade después de que la actualización manual funcione; Android podrá usar WorkManager si el requisito pide trabajo persistente en segundo plano.

## Sesión protegida

En Android, la propuesta es cifrar el material recuperable con una clave protegida por [Android Keystore](https://developer.android.com/privacy-and-security/keystore). Keystore protege claves: no se usa como una tabla donde introducir cualquier cookie. Hay que definir formato, vencimiento, restauración, fallos de descifrado y borrado. En escritorio se evalúa el almacén de credenciales del sistema; en iOS, Keychain. En web se propone sesión BFF con cookie protegida; no contraseñas o tokens del proveedor en localStorage.

No guardar contraseñas por defecto. Primero comprobar si cookies/tokens bastan para restaurar acceso y cómo expiran. Si el requisito acaba necesitando otra política, se documenta y aprueba explícitamente antes de implementarla. Un `.env` del probe es configuración de desarrollo y no resuelve la persistencia de producción.

Al cerrar sesión se borra el material protegido y, por defecto, los datos académicos de esa cuenta. Si el producto quiere retenerlos para consulta posterior, debe definir esa experiencia y su protección antes de cambiar la política. Una caída temporal de un proveedor no ejecuta un borrado global de sesión.

## DB del servidor y herramientas

PostgreSQL es la propuesta cuando el BFF necesite datos propios persistentes. No recreamos la BD universitaria ni guardamos todos los cursos de todos los usuarios sin requisito. Se definirán propietario, retención, migraciones, restricciones e índices según consultas reales. Sesiones duraderas del BFF requieren también una política de expiración y protección de sus secretos.

El primer BFF de autenticación/consulta no necesita instalar PostgreSQL si no conserva datos propios duraderos. Docker Compose podrá levantar la DB para desarrollo; DBeaver o herramientas SQL sirven para inspección. Redis queda para una necesidad comprobada de caché distribuida, coordinación o sesiones: no es obligatorio por tener un servidor.

## Pruebas y evolución

Las pruebas de persistencia usan una DB aislada y verifican aislamiento por cuenta, actualización transaccional, rollback, observación y migraciones entre versiones de nuestro esquema. Versionamos esquemas y migraciones sin borrar datos del usuario como atajo.

Secuencia de incorporación: consulta remota mediante repositorio → caché SQLite → pruebas de consulta/actualización → recuperación segura de sesión → sincronización automática si el requisito la necesita. La UI puede comenzar antes de la DB.
