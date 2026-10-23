package org.basadrito.app.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import basadrito.app.shared.generated.resources.Res
import basadrito.app.shared.generated.resources.icon_eye
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun BasadritoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: DrawableResource,
    enabled: Boolean,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onSubmit: () -> Unit = {},
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focus = LocalFocusManager.current
    val colors = BasadritoColors
    val shape = RoundedCornerShape(BasadritoTokens.fieldRadius)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        BasicTextField(
            value = value, onValueChange = onValueChange, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = BasadritoTokens.fieldMinHeight)
                .semantics { contentDescription = label }
                .background(LoginColors.fieldSurface, shape)
                .border(if (focused) 2.dp else 1.dp, if (focused) colors.coral else colors.inputBorder, shape),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp, color = LoginColors.fieldText),
            singleLine = true, interactionSource = interaction,
            cursorBrush = SolidColor(colors.coral),
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                imeAction = imeAction),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }, onDone = { onSubmit() }),
            decorationBox = { input ->
                Row(Modifier.heightIn(min = BasadritoTokens.fieldMinHeight).padding(start = 16.dp, end = if (isPassword) 4.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(icon), null, Modifier.size(20.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), color = LoginColors.fieldHint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        input()
                    }
                    if (isPassword) IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = enabled,
                        modifier = Modifier.size(44.dp)) {
                        Image(painterResource(Res.drawable.icon_eye),
                            if (passwordVisible) "Ocultar $label" else "Mostrar $label", Modifier.size(20.dp))
                    }
                }
            },
        )
    }
}
