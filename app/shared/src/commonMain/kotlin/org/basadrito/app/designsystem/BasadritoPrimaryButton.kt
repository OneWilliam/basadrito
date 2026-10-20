package org.basadrito.app.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import basadrito.app.shared.generated.resources.Res
import basadrito.app.shared.generated.resources.icon_arrow_right
import org.jetbrains.compose.resources.painterResource

@Composable
fun BasadritoPrimaryButton(enabled: Boolean, loading: Boolean, loadingMessage: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(BasadritoTokens.buttonRadius)
    Button(
        onClick = onClick, enabled = enabled && !loading,
        modifier = Modifier.fillMaxWidth().heightIn(min = BasadritoTokens.buttonMinHeight)
            .shadow(if (enabled) 12.dp else 0.dp, shape, ambientColor = BasadritoColors.coral, spotColor = BasadritoColors.coral),
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = BasadritoColors.ink,
            disabledContainerColor = BasadritoColors.ink.copy(alpha = 0.45f), disabledContentColor = BasadritoColors.white),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = BasadritoColors.white, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(loadingMessage.ifBlank { "Ingresando…" })
        } else {
            Text("Ingresar", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            Image(painterResource(Res.drawable.icon_arrow_right), null, Modifier.size(20.dp))
        }
    }
}
