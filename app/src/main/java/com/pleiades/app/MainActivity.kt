package com.pleiades.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pleiades.app.core.Export
import com.pleiades.app.core.Project
import com.pleiades.app.core.Sprite
import com.pleiades.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PleiadesTheme { PleiadesApp() } }
    }
}

private fun Context.displayName(uri: Uri): String =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "sprite"

@Composable
fun PleiadesApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("pleiades", Context.MODE_PRIVATE) }
    val app = remember {
        AppState().also { a ->
            prefs.getString("export_dir", null)?.let { Uri.parse(it) }?.let { u ->
                if (ctx.contentResolver.persistedUriPermissions.any { it.uri == u && it.isWritePermission }) a.lastExportDir = u
            }
        }
    }

    fun runExport(sp: Sprite, dir: Uri) {
        val o = app.pendingExport ?: return
        app.say("Exporting…")
        scope.launch {
            val msg = withContext(Dispatchers.IO) { try { Export.run(ctx, sp, dir, o) } catch (e: Throwable) { "Export failed: ${e.message}" } }
            app.say(msg)
        }
    }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val sp = app.sprite
        if (uri != null && sp != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                prefs.edit().putString("export_dir", uri.toString()).apply()
            } catch (_: Throwable) {}
            app.lastExportDir = uri
            runExport(sp, uri)
        }
    }

    fun write(sp: Sprite, uri: Uri, onFail: () -> Unit) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { Project.save(sp, it) }; true } catch (_: Throwable) { false }
            }
            if (ok) { sp.fileUri = uri; sp.savedRev = sp.rev; app.say("Saved ${sp.name}") } else onFail()
        }
    }

    val createPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val sp = app.sprite
        if (uri != null && sp != null) write(sp, uri) { app.say("Couldn't save to that location") }
    }

    val openPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = ctx.displayName(uri)
            val res = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openInputStream(uri)!!.use { inp ->
                        if (name.endsWith(".pls", true)) Project.load(inp).also { it.fileUri = uri }
                        else Project.importImage(name.substringBeforeLast('.'), inp)
                    }
                } catch (e: Throwable) { e.message ?: "Could not open file" }
            }
            if (res is Sprite) {
                try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Throwable) {}
                app.open(res)
            } else app.say(res.toString())
        }
    }

    fun saveAs() { app.sprite?.let { createPicker.launch("${it.name}.pls") } }
    fun save() {
        val sp = app.sprite ?: return
        val u = sp.fileUri
        if (u == null) saveAs() else write(sp, u) { saveAs() }
    }

    Box(Modifier.fillMaxSize().background(P.bg).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(app, ::save, ::saveAs) { openPicker.launch(arrayOf("*/*")) }
            val sp = app.sprite
            if (sp == null) {
                Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Pleiades", color = P.text, fontSize = 34.sp)
                    Text("Asset creation for 2D games", color = P.dim, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Chip("New sprite", true, { app.dlg = Dlg.New })
                        Chip("Open…", false, { openPicker.launch(arrayOf("*/*")) })
                    }
                }
            } else {
                Row(Modifier.weight(1f)) {
                    ToolBar(app, sp)
                    Box(Modifier.weight(1f).fillMaxSize()) { CanvasView(app, sp) }
                    SidePanel(app, sp)
                }
                Timeline(app, sp)
            }
        }

        when (val d = app.dlg) {
            is Dlg.New -> NewDialog({ app.dlg = null }) { app.open(it); app.dlg = null }
            is Dlg.Export -> app.sprite?.let { sp ->
                ExportDialog(sp, app.lastExportDir != null, { app.dlg = null }) { opts, useLast ->
                    app.dlg = null; app.pendingExport = opts
                    val last = app.lastExportDir
                    if (useLast && last != null) runExport(sp, last) else treePicker.launch(null)
                }
            }
            is Dlg.Close -> app.tabs.getOrNull(d.index)?.let { s -> CloseDialog(s.name, { app.dlg = null }) { app.close(d.index); app.dlg = null } }
            is Dlg.RenameAnim -> app.sprite?.let { sp -> RenameDialog(sp.anim.name, { app.dlg = null }) { sp.renameAnim(it); app.dlg = null } }
            is Dlg.Rename -> app.tabs.getOrNull(d.index)?.let { s -> RenameDialog(s.name, { app.dlg = null }) { s.name = it; app.dlg = null; s.touch() } }
            null -> {}
        }

        app.message?.let { m ->
            LaunchedEffect(m) { delay(3500); if (app.message == m) app.message = null }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 130.dp).background(P.panel2, RoundedCornerShape(12.dp)).padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text(m, color = P.text, fontSize = 14.sp)
            }
        }
    }
}
