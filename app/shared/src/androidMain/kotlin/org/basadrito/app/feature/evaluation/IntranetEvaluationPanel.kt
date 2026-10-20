package org.basadrito.app.feature.evaluation

import android.annotation.SuppressLint
import android.net.Uri
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.json.JSONObject
import org.json.JSONTokener
import org.basadrito.app.domain.auth.PortalAccess

private const val EVALUATION_URL = "https://intranet.unjbg.edu.pe/alumno/evaluacion-docente"

@Composable
fun IntranetEvaluationPanel(
    prepareBrowser: suspend () -> Result<Unit>,
    confirmAccess: suspend () -> Result<PortalAccess>,
    clearBrowser: () -> Unit,
    onConfirmed: (PortalAccess) -> Unit,
    onSignOut: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var rating by rememberSaveable { mutableIntStateOf(3) }
    var comment by rememberSaveable { mutableStateOf("Sin comentarios") }
    var assisted by rememberSaveable { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun checkAccess() {
        if (checking) return
        checking = true
        message = null
        scope.launch {
            try {
                confirmAccess().fold(onSuccess = onConfirmed,
                    onFailure = { message = "La Intranet aún no permite abrir el perfil. Comprueba las evaluaciones pendientes y tu conexión." })
            } finally { checking = false }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Evaluación docente pendiente", style = MaterialTheme.typography.headlineSmall)
            Text("La Intranet solicita completar una evaluación antes de continuar. La sesión de la app se conserva mientras lo haces.")
            Button(onClick = { uriHandler.openUri(EVALUATION_URL) }, enabled = !checking) { Text("Abrir en navegador") }
            Text("El navegador externo usa su propia sesión; puede pedirte ingresar de nuevo.", style = MaterialTheme.typography.bodySmall)
            Text("Valor para las respuestas sin marcar")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (value in 1..5) {
                    FilterChip(selected = value == rating, onClick = { rating = value }, label = { Text("$value") }, enabled = !checking)
                }
            }
            OutlinedTextField(
                value = comment,
                onValueChange = { if (it.length <= 500) comment = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Comentario para todas las evaluaciones") },
                supportingText = { Text(if (comment.isBlank()) "El formulario requiere un comentario." else "${comment.length}/500 caracteres") },
                isError = comment.isBlank(),
                minLines = 2,
                maxLines = 4,
                enabled = !checking,
            )
            Text("El proceso abrirá cada «Calificar», rellenará las respuestas vacías y enviará la evaluación con este comentario. Podrás verlo y pausarlo en el panel.")
            Button(onClick = { assisted = true }, enabled = !checking && comment.isNotBlank()) { Text("Completar y enviar automáticamente") }
            OutlinedButton(onClick = ::checkAccess, enabled = !checking) {
                Text(if (checking) "Comprobando…" else "Ya completé la evaluación: continuar")
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onSignOut, enabled = !checking) { Text("Cerrar sesión") }
        }
    }

    if (assisted) {
        AssistedEvaluationBrowser(rating, comment, prepareBrowser, clearBrowser,
            onClose = { assisted = false },
            onCheck = { assisted = false; checkAccess() })
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AssistedEvaluationBrowser(
    rating: Int,
    comment: String,
    prepareBrowser: suspend () -> Result<Unit>,
    clearBrowser: () -> Unit,
    onClose: () -> Unit,
    onCheck: () -> Unit,
) {
    val context = LocalContext.current
    var prepared by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var filledMessage by remember { mutableStateOf("Preparando la sesión…") }
    var running by remember { mutableStateOf(true) }
    var pageLoading by remember { mutableStateOf(true) }
    var submittedCount by remember { mutableIntStateOf(0) }
    var awaitingSubmission by remember { mutableStateOf(false) }
    val submittedUrls = remember { mutableSetOf<String>() }
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            webChromeClient = WebChromeClient()
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        }
    }
    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val blocked = !allowedEvaluationNavigation(uri)
                if (blocked && request.isForMainFrame) {
                    running = false
                    filledMessage = "El portal abrió un destino que requiere intervención. El proceso está pausado."
                }
                return blocked
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                pageLoading = true
            }

            override fun onPageFinished(view: WebView, url: String) {
                pageLoading = false
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    running = false
                    filledMessage = "No se pudo cargar la página. El proceso está pausado."
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame) {
                    running = false
                    filledMessage = "El portal respondió con HTTP ${errorResponse.statusCode}. El proceso está pausado."
                }
            }
        }
        onDispose {
            webView.stopLoading()
            webView.clearHistory()
            webView.clearCache(true)
            webView.destroy()
            android.webkit.WebStorage.getInstance().deleteAllData()
            clearBrowser()
        }
    }
    LaunchedEffect(webView) {
        prepareBrowser().fold(
            onSuccess = { prepared = true; webView.loadUrl(EVALUATION_URL) },
            onFailure = { failed = true },
        )
    }
    LaunchedEffect(webView, prepared, running) {
        if (!prepared || !running) return@LaunchedEffect
        var lastProgress = SystemClock.elapsedRealtime()
        while (isActive) {
            delay(1_500)
            if (SystemClock.elapsedRealtime() - lastProgress > 30_000) {
                running = false
                filledMessage = "El portal no confirmó el siguiente paso. Revisa la página; no se repetirá el envío automáticamente."
                break
            }
            if (pageLoading) continue
            val currentUrl = webView.url.orEmpty().substringBefore('#')
            val script = evaluationAutomationScript(rating, comment, currentUrl !in submittedUrls, awaitingSubmission)
            val encodedResult = suspendCancellableCoroutine<String?> { continuation ->
                webView.evaluateJavascript(script) { if (continuation.isActive) continuation.resume(it) }
            }
            val result = runCatching {
                val json = JSONTokener(encodedResult ?: "null").nextValue() as? String
                JSONObject(json ?: error("Respuesta no reconocida"))
            }.getOrNull()
            val action = result?.optString("action")
            if (action != "WAIT") lastProgress = SystemClock.elapsedRealtime()
            when (action) {
                "OPEN" -> {
                    awaitingSubmission = false
                    filledMessage = "Abriendo la siguiente evaluación…"
                }
                "PAGE" -> filledMessage = "Revisando la siguiente página de evaluaciones…"
                "PREPARED" -> filledMessage = "Respuestas preparadas con $rating/5. Enviando en el siguiente paso…"
                "SUBMITTED" -> {
                    submittedUrls += currentUrl
                    submittedCount++
                    awaitingSubmission = true
                    filledMessage = "Envío iniciado. Esperando la respuesta de la Intranet…"
                }
                "CONTINUE" -> filledMessage = "Continuando al siguiente paso del portal…"
                "WAIT" -> Unit
                "VERIFY" -> {
                    running = false
                    filledMessage = "No hay más evaluaciones visibles. Comprobando el acceso al perfil…"
                    onCheck()
                    break
                }
                else -> {
                    running = false
                    filledMessage = when (result?.optString("reason")) {
                        "duplicate" -> "Esta evaluación ya tuvo un intento de envío. Revisa el resultado antes de continuar."
                        "validation" -> "El formulario tiene una validación pendiente. Revísala en la página."
                        "scale", "structure" -> "El formulario cambió. El proceso se pausó para que puedas revisarlo."
                        "session" -> "El portal requiere iniciar sesión o intervención manual."
                        else -> "El portal muestra una página no reconocida. El proceso está pausado."
                    }
                    break
                }
            }
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding()) {
                Text("Evaluación · $rating de 5", Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
                Text("Envíos iniciados: $submittedCount · ${if (running) "En curso" else "Pausado"}", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
                Text(filledMessage, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = onClose) { Text("Cerrar") }
                    TextButton(onClick = { running = !running }, enabled = prepared) { Text(if (running) "Pausar" else "Reanudar") }
                    TextButton(onClick = onCheck, enabled = prepared && !running) { Text("Comprobar acceso") }
                }
                if (failed) Text("No se pudo preparar la sesión. Cierra este panel e intenta nuevamente.", Modifier.padding(16.dp))
                else if (!prepared) CircularProgressIndicator(Modifier.padding(16.dp))
                else AndroidView(factory = { webView }, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

internal fun allowedEvaluationNavigation(uri: Uri): Boolean =
    uri.scheme == "https" && (uri.port == -1 || uri.port == 443) && uri.userInfo == null &&
        uri.host in setOf("intranet.unjbg.edu.pe", "campus.unjbg.edu.pe")
