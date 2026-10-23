# Apariencia y componentes compartidos

La app ofrece Claro, Oscuro y Usar el sistema. La elección es una preferencia del dispositivo y no pertenece a una cuenta ni a Room. Android utiliza SharedPreferences, escritorio Preferences e iOS NSUserDefaults. La presentación recibe `AppearancePreferences` desde su contenedor.

## Referencia de diseño

Se usan los modos Light/Dark de la colección `Colores` y los mockups móviles del [diseño del producto](https://www.figma.com/design/uzCS9MW44k7UIfwlkIIhI9/Basadrito---Dise%C3%B1o-del-producto?node-id=4-5). Las variantes Neutra/Cálida/Fría de la colección de paletas son una selección diferente y todavía no tienen un control en la app.

| Rol | Claro | Oscuro |
|---|---|---|
| Fondo | `#FFFFFF` | `#111113` |
| Superficie | `#FFFFFF` | `#1E1E25` |
| Superficie elevada | Material claro | `#28282E` |
| Texto principal | `#141416` | `#F7F7F8` |
| Texto secundario | `#55565C` | `#B0B0B8` |

Los colores de texto secundarios se ajustan para legibilidad. El modo oscuro mantiene los inputs claros del mockup de acceso y utiliza roles específicos para texto y placeholder sobre ellos. El fondo blanco literal de la variante oscura de Cursos se sustituye por el rol oscuro de fondo, coherente con los demás destinos.

## Ubicación del código

| Archivo | Responsabilidad |
|---|---|
| [BasadritoTheme](../../app/shared/src/commonMain/kotlin/org/basadrito/app/designsystem/BasadritoTheme.kt) | Resuelve sistema/claro/oscuro y proporciona los roles Material. |
| [AppearancePreferences](../../app/shared/src/commonMain/kotlin/org/basadrito/app/designsystem/AppearancePreferences.kt) | Contrato observable de preferencia. |
| [FeatureHeader](../../app/shared/src/commonMain/kotlin/org/basadrito/app/designsystem/FeatureHeader.kt) | Flecha opcional, título, notificaciones y fondo decorado con formas Compose. |
| [DarkBrandMark](../../app/shared/src/commonMain/kotlin/org/basadrito/app/designsystem/DarkBrandMark.kt) | Geometría y relleno angular del logotipo oscuro dibujados con primitivas nativas. |
| [SettingsScreen](../../app/shared/src/commonMain/kotlin/org/basadrito/app/feature/settings/SettingsScreen.kt) | Identidad, selección de apariencia, estado de acceso y logout. |

Las superficies, etiquetas y barra inferior consumen roles del tema. Los adornos, acentos de curso y paneles de onboarding conservan sus colores de marca. No se utilizan URLs de assets en ejecución. Las ilustraciones existentes de bienvenida/onboarding se conservan; sus variantes ilustradas oscuras siguen pendientes.

Cambiar apariencia actualiza la composición sin recrear la pila ni sus ViewModels. Android adapta también el contraste de las barras del sistema.

## Pantallas conectadas

Inicio presenta nombre, cantidad de cursos, origen y actualización disponibles. Cursos añade búsqueda por nombre o nombre corto y tarjetas reutilizables. Ambos permiten actualizar desde Moodle y conservan la última instantánea ante un fallo. Ajustes permite volver a comprobar la conexión cuando el acceso es exclusivamente local.

La cabecera se comparte entre esos tres destinos. Notificaciones, Herramientas y Calendario permanecen deshabilitados. Los datos de notas, progreso, créditos, horarios y pendientes del mockup requieren sus integraciones; no se muestran cifras inventadas.

La barra lateral y la versión web se posponen. Los contenedores y anchos máximos actuales permiten organizar los destinos sin acoplarlos a una futura presentación lateral.

## Comprobaciones

| Comprobación | Alcance |
|---|---|
| Contraste de roles | Texto oscuro, errores, botones e inputs claros con relación mínima de 4.5:1 en los pares comprobados. |
| Preferencias Android | La elección se recupera desde otra instancia. |
| Navegación y apariencia | Cambiar tema conserva destino, ViewModel y scroll de Cursos. |

La compilación y ejecución de iOS siguen pendientes.
