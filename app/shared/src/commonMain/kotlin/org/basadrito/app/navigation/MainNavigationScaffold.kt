package org.basadrito.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import basadrito.app.shared.generated.resources.*
import org.basadrito.app.designsystem.BasadritoColors
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Navigation presentation and system insets are independent of routes and stack policy. */
@Composable
internal fun MainNavigationScaffold(selected: AppDestination, onSelect: (MainSection) -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        bottomBar = { MainBottomBar(selected, onSelect) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() }
    }
}

/** Figma 445:1182. Real system insets replace the mockup's home indicator. */
@Composable
private fun MainBottomBar(selected: AppDestination, onSelect: (MainSection) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                MainSection.entries.forEach { section ->
                    val enabled = section.destination != null
                    val isSelected = section.destination == selected
                    Column(
                        Modifier.weight(1f).heightIn(min = 64.dp)
                            .selectable(isSelected, enabled = enabled, role = Role.Tab, onClick = { onSelect(section) })
                            .semantics { contentDescription = if (enabled) section.label else "${section.label}, próximamente" }
                            .padding(vertical = 8.dp).alpha(if (enabled) 1f else 0.45f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (section == MainSection.TOOLS) ToolsGridIcon(isSelected) else {
                            val selectedColor = if (isSelected) BasadritoColors.coral else MaterialTheme.colorScheme.onSurface
                            Icon(painterResource(section.icon()), null, Modifier.size(15.dp), tint = selectedColor)
                            Spacer(Modifier.height(3.dp))
                            Text(section.label, fontSize = 11.sp, lineHeight = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = selectedColor, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Native Compose shapes: 44 px circle and four 8 px squares from Figma. */
@Composable
private fun ToolsGridIcon(selected: Boolean) {
    Column(Modifier.size(44.dp).background(if (selected) BasadritoColors.coral else BasadritoColors.navigationButton, CircleShape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) { Box(Modifier.size(8.dp).background(BasadritoColors.white, RoundedCornerShape(1.5.dp))) }
            }
        }
    }
}

private fun MainSection.icon(): DrawableResource = when (this) {
    MainSection.HOME -> Res.drawable.nav_home
    MainSection.COURSES -> Res.drawable.nav_courses
    MainSection.CALENDAR -> Res.drawable.nav_calendar
    MainSection.SETTINGS -> Res.drawable.nav_settings
    MainSection.TOOLS -> error("Tools uses native grid shapes")
}
