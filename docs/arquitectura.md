# Arquitectura de Basadrito

## Objetivo y decisiones

Basadrito se organiza en módulos KMP, con límites de Clean Architecture, contratos de dominio y MVVM en presentación. Las funcionalidades se agrupan por feature dentro de esas capas.

Clean Architecture gobierna la dirección de dependencias. MVVM organiza pantalla, estado y coordinación. Los contratos de dominio y sus implementaciones externas siguen la idea de puertos y adaptadores.

## Vista de dependencias objetivo

![Dependencias de módulos](diagramas/dependencias.svg)

Las flechas significan «conoce y depende de». Las entradas de plataforma construyen implementaciones de `data` y las entregan a `shared` mediante contratos de `domain`. `shared/commonMain` no importa DTOs, parsers, motores HTTP ni repositorios concretos. El servidor y el probe ensamblan sus propias dependencias.

| Módulo | Responsabilidad | Límite |
|---|---|---|
| `core/domain` | Modelos propios, contratos, estados y reglas de negocio | No depende de Compose, Ktor, SQL ni formatos universitarios. |
| `core/data` | Adaptadores Moodle/Intranet/BFF, repositorios, transporte, sesión y persistencia | Implementa contratos; convierte DTOs, HTML y entidades a modelos propios. |
| `app/shared` | Pantallas, ViewModels, UiState, navegación y sistema de diseño | Consume contratos de dominio. |
| Entradas de `app` | Arranque, permisos, ciclo de vida y composición específica de plataforma | Eligen motor HTTP, almacenamiento y repositorios. |
| `server` | API propia de Basadrito, sesiones web y composición del BFF | No contiene lógica de presentación ni expone indiscriminadamente los portales. |
| `tools/integration-probe` | Experimentos manuales de integración | No es una dependencia de producción de las apps. |

Los casos de uso se introducen cuando coordinan una operación con reglas: por ejemplo, refrescar datos de varios proveedores y actualizar caché. No añadimos una clase por cada función que solo reenvía una llamada. Cada repositorio representa una capacidad: autenticación, cursos, notas, asistencia; no un repositorio gigante que absorba todo.

## Vista de las partes

![Capas de la aplicación en vista axonométrica](diagramas/capas.svg)

La altura expresa su organización conceptual, no el orden de ejecución ni dependencias. El dominio concentra el vocabulario y las reglas que sobreviven a cambios de UI, HTTP o DB.

## Recorrido de una interacción

![Consulta, caché y estado de pantalla](diagramas/recorrido-consulta.svg)

Esta vista representa el flujo previsto de una consulta con caché. Las llamadas salen hacia el proveedor; los datos regresan normalizados y actualizan el estado de pantalla. No son flechas de dependencias de compilación.

1. La entrada Android crea una sesión por cuenta, clientes HTTP y repositorios. Construye el contenedor que entrega contratos al ViewModel.
2. Una pantalla Compose representa `UiState` y envía eventos, sin llamar directamente a HTTP ni SQL.
3. El ViewModel valida datos de entrada, inicia una coroutine vinculada a su ciclo de vida y publica carga. Invoca un contrato o caso de uso.
4. El repositorio aplica la política de datos. Para el acceso inicial, exige Moodle e Intranet. Para consultas posteriores, usa la sesión activa y conserva acceso parcial ante una caída temporal.
5. El adaptador convierte el protocolo externo a modelos propios. Si hay caché, actualiza la DB dentro de una transacción.
6. El repositorio devuelve un resultado o emite datos observables; el ViewModel publica el nuevo estado y Compose vuelve a representar la pantalla.
7. Cerrar sesión cancela trabajo ligado a la cuenta, invalida la sesión y elimina acceso protegido y datos personales conforme a la política de persistencia.

El ViewModel se obtiene con integración de lifecycle; no se crea un cliente o una nueva sesión en cada recomposición. Se empieza con inyección por constructor y composición manual. Koin u otro contenedor solo se incorporará si reduce una complejidad comprobada.

## Plataformas y comunicación

![Comunicación objetivo y ubicación de datos](diagramas/ejecucion.svg)

Android y escritorio acceden a los proveedores mediante adaptadores directos. La web usa una API BFF porque el navegador impone condiciones de origen, cookies y acceso diferentes. El BFF comparte contratos y adaptadores que sean adecuados para JVM; mantiene sesiones aisladas por usuario. No se comparte una única sesión global entre visitantes.

El navegador recibe una sesión de Basadrito, no las cookies o tokens de los portales. La propuesta para web es cookie de sesión `HttpOnly`, `Secure`, política `SameSite` justificada y protección CSRF cuando corresponda. Esta política se concretará al implementar el BFF. Los endpoints propios de login, logout y consulta tendrán DTOs y errores públicos documentados.

Las URLs son configuración de los adaptadores/entradas; `App` no recibe una URL BFF por defecto para todas las plataformas. HTTP local de desarrollo y HTTPS de producción se documentan por entorno. No se fija localhost como destino de Android: en el emulador tampoco representa el servidor del equipo.

## DB, almacenamiento y actualización

![Mapa de persistencia previsto](diagramas/persistencia.svg)

La caché, la recuperación de sesión y las preferencias tienen adaptadores separados. Android ya ensambla `AndroidSessionStore` para recuperar el token/cookies con cifrado y validación de proveedores; las otras plataformas siguen pendientes. La UI conoce `AuthRepository.restoreSession()`, pero no el archivo ni las cookies. La DB del servidor se incorpora cuando los datos propios requieran persistencia.

La DB local se incorpora al primer requisito de consulta sin conexión. Proponemos SQLite con Room para Android y, tras verificar compatibilidad, destinos nativos/JVM; el código Room se coloca en conjuntos de fuentes compatibles y no se fuerza dentro de `commonMain` compartido con JS/Wasm. Los contratos de dominio conservan la misma forma. Web tendrá su propio adaptador de almacenamiento cuando necesite caché persistente.

Los datos recuperados incluyen cuenta, proveedor, período y fecha de actualización. La UI distinguirá información actualizada, almacenada y no disponible. El almacenamiento seguro de sesión es una responsabilidad separada de la caché académica. La DB del BFF se propone relacional —PostgreSQL— cuando existan datos propios persistentes: preferencias, recordatorios o sesiones según su política. El primer BFF puede funcionar sin una DB académica central.

Consulta [datos y persistencia](diseno/datos-y-persistencia.md) para esquema inicial, algoritmo de actualización, límites y pruebas.

## Navegación y diseño

La navegación se centraliza en `shared/navigation`: destinos y grafo, retorno y barrera de autenticación. Cada feature contiene su pantalla, ViewModel y estado; los componentes reutilizables pertenecen a `designsystem`. Se implementa primero login → inicio → logout; luego se conectan cursos, notas, asistencia y calendario según requisitos y Figma.

El grafo OOHDM describe nodos, contextos y navegación por perfil. El grafo de la aplicación implementa rutas y estado. Se trazan entre sí, pero no son el mismo artefacto. Un estado temporal de error no obliga a inventar una nueva pantalla en Figma.

## Referencias de aprendizaje

- [KMP: conjuntos de fuentes](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-discover-project.html).
- [Arquitectura Android](https://developer.android.com/topic/architecture): guía para contrastar separación de responsabilidades y flujo de estado.
Los motivos de diseño y sus alternativas están descritos en este documento y en [datos y persistencia](diseno/datos-y-persistencia.md).
