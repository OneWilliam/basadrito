# Estructura objetivo y conjuntos de fuentes

```text
app/
  androidApp/                 entrada Android y composición
  desktopApp/                 entrada JVM y composición
  webApp/                     entrada navegador y configuración BFF
  iosApp/                     entrada iOS / Xcode
  shared/src/commonMain/kotlin/org/basadrito/app/
    feature/auth/             Screen, ViewModel, UiState
    feature/dashboard/
    feature/courses/           y después grades, attendance, calendar
    designsystem/              tema, tokens y componentes compartidos
    navigation/                destinos y grafo
    di/                        contenedor de contratos para presentación
core/
  domain/src/commonMain/kotlin/org/basadrito/app/domain/
    auth/                     contratos y modelos de autenticación
    courses/                  contratos y modelos propios de cursos
  data/src/commonMain/kotlin/org/basadrito/app/data/
    http/                     configuración y soporte de transporte
    moodle/                   DTOs y cliente del proveedor
    intranet/                 formularios, parsers y cliente
    repository/               implementaciones por capacidad
    session/                  custodia y recuperación de sesión
    di/                       ensamblaje de clientes de integración
    bff/                      futuro adaptador para el navegador
    local/                    futuros adaptadores de caché y almacenamiento
server/                       futura API propia y composición BFF
tools/integration-probe/       verificación manual sin UI
docs/                         guías, decisiones, diseño y evidencia
```

El árbol expresa el destino; no obliga a crear carpetas vacías ni archivos ficticios. `core` agrupa módulos, no es una biblioteca que debas añadir como `implementation(project(":core"))`.

## Módulos

Cada módulo Gradle tiene sus fuentes: `core/domain/src` y `core/data/src` contienen responsabilidades distintas. Dentro de cada módulo KMP, `commonMain` es código compartido; `androidMain`, `jvmMain`, `iosMain`, `jsMain` o `wasmJsMain` contienen implementaciones para sus destinos. `commonTest` guarda pruebas compartibles; pruebas que necesitan un entorno específico pertenecen al conjunto de pruebas de ese destino.

`expect` declara una capacidad específica de plataforma; `actual` la resuelve para cada destino compilado. Tener la dependencia OkHttp declarada no reemplaza un `actual platformHttpEngine` ausente.

La persistencia Room se implementa en `core/database`, con destinos Android, JVM e iOS. `core/data` define el puerto de caché sin depender de Room. Las entradas nativas ensamblan ambos módulos; el almacenamiento no se introduce en el conjunto compartido que compila a JS/Wasm. Separar otro módulo se justifica por aislamiento de dependencias o compilación, no por cada carpeta funcional.

## Dónde poner una pieza nueva

| Pieza | Ubicación |
|---|---|
| Regla de cálculo de nota, contrato de consulta | `domain/grades` |
| JSON Moodle, parser HTML Intranet | Adaptador del proveedor en `data` |
| Entidad SQL y DAO | Persistencia de `data`, en fuentes compatibles |
| Estado de pantalla y reacción a un evento | `shared/feature/<feature>` |
| Botón reutilizable y tokens visuales | `shared/designsystem` |
| Selección de repositorios concretos y ruta de DB | Composición de plataforma |
| Endpoint HTTP de Basadrito | `server` |
| Experimento para comprobar un endpoint externo | `tools`, sin dependencia desde la app |

Lee [arquitectura](arquitectura.md) para entender las conexiones antes de añadir código.
