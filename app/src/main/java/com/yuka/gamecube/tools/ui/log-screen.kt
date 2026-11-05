package com.yuka.gamecube.tools.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.yuka.gamecube.tools.BuildConfig
import com.yuka.gamecube.tools.diagnostics.AppDiagnostics
import com.yuka.gamecube.tools.diagnostics.HookLogReader
import com.yuka.gamecube.tools.diagnostics.logBufferSnapshot
import com.yuka.gamecube.tools.diagnostics.diagnosticHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.time.Instant

@Composable
internal fun LogScreen(clearRequest: Int = 0) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager by AppDiagnostics.lines.collectAsState()
    var hook by remember { mutableStateOf("") }
    var root by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val snapshot = remember(manager, hook) { logBufferSnapshot(manager, hook) }
    LaunchedEffect(clearRequest) {
        if (clearRequest > 0) {
            root = false
            hook = ""
            failure = null
            AppDiagnostics.clear()
        }
    }
    LaunchedEffect(root, clearRequest) {
        if (root) while (true) {
            reading = true
            try {
                hook = HookLogReader.read()
                failure = if(hook.contains(HookLogReader.READER_WARNING)) "Capture incomplete; see root reader warnings below." else null
                if(failure != null) root = false
            }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                failure = error.message ?: error.javaClass.simpleName
                root = false
                AppDiagnostics.log("hook_log", "read_failed=$failure")
                AppDiagnostics.error("hook_log", error)
                break
            } finally { reading = false }
            delay(2500)
        }
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(inset = false) {
            ToggleRow("Read hook logs with root", "Read only; updates while this screen is open.", root) { root = it }
            if (reading) Caption("Reading logs…", Modifier.padding(horizontal = 20.dp))
            failure?.let { Caption("Unable to read hook logs: $it", Modifier.padding(horizontal = 20.dp)) }
            TextButton("Share logs", {
                val header = "GameCube Unlock ${BuildConfig.VERSION_NAME}/${BuildConfig.VERSION_CODE}\n" +
                    "Captured: ${Instant.now()}\n" +
                    "\nRoot read status: ${failure ?: if (hook.isEmpty()) "not captured" else "captured"}" +
                    "\nHook source: read-only main/system/crash; module and OEM tails captured separately. Read in progress: $reading\n"
                scope.launch {
                    try {
                        val text = header + diagnosticHeader(context, hook) + "\n" + snapshot
                        val uri = withContext(Dispatchers.IO) {
                            val folder = File(context.cacheDir, "diagnostics").apply { mkdirs() }
                            val file = File.createTempFile("gamecube-debug-", ".txt", folder).apply { writeText(text) }
                            folder.listFiles()?.filter { it.isFile && it.name.startsWith("gamecube-debug-") }
                                ?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
                            FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", file)
                        }
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_STREAM, uri).apply {
                                clipData = ClipData.newRawUri("log", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, null))
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        AppDiagnostics.error("share", error)
                        failure = error.message ?: error.javaClass.simpleName
                    }
                }
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp))
        }
        key(clearRequest) { LogBufferView(snapshot, Modifier.weight(1f).fillMaxWidth()) }
    }
}

@Composable
internal fun LogBufferView(text: String, modifier: Modifier = Modifier) {
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val neutral = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val lines by produceState<List<AnnotatedString>>(emptyList(), text, dark, neutral) {
        value = withContext(Dispatchers.Default) { coloredLogLines(text, dark, neutral) }
    }
    var widest by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    SelectionContainer(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Keep the shared horizontal range from shrinking as shorter rows enter the viewport.
            val minimum = maxOf(maxWidth, with(density) { widest.toDp() })
            LazyColumn(Modifier.fillMaxHeight().horizontalScroll(rememberScrollState())
                .widthIn(min = minimum).onSizeChanged { if (it.width > widest) widest = it.width }) {
                items(lines) { line ->
                    Text(line, softWrap = false, fontSize = 11.sp, lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
