package dev.antinode.app.autoeq

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.antinode.core.autoeq.AutoEqCatalog
import dev.antinode.core.autoeq.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Loads the bundled AutoEQ database once and keeps user-imported targets in app storage. */
object AutoEqRepository {
    private val mutex = Mutex()
    @Volatile private var catalog: AutoEqCatalog? = null

    suspend fun catalog(context: Context): AutoEqCatalog = catalog ?: mutex.withLock {
        catalog ?: withContext(Dispatchers.IO) {
            val assets = context.applicationContext.assets
            // The packager may store "x.tsv.gz" as plain "x.tsv"; accept either.
            fun open(name: String) = runCatching { assets.open("autoeq/$name") }.getOrElse { assets.open("autoeq/$name.gz") }
            AutoEqCatalog.read(open("catalog.tsv"), open("targets.tsv"))
        }.also { catalog = it }
    }

    private fun dir(context: Context) = File(context.filesDir, "targets").apply { mkdirs() }

    suspend fun customTargets(context: Context): List<Target> = withContext(Dispatchers.IO) {
        dir(context).listFiles { f -> f.extension == "csv" }.orEmpty().sortedBy { it.name }.mapNotNull { f ->
            AutoEqCatalog.parseCurveCsv(f.readText())?.let { Target(f.nameWithoutExtension, it, custom = true) }
        }
    }

    /** Copies a target CSV into app storage. Returns its name, or null if it isn't a frequency response. */
    suspend fun importTarget(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@withContext null
        AutoEqCatalog.parseCurveCsv(text) ?: return@withContext null
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }?.substringBeforeLast('.')?.replace(Regex("[^A-Za-z0-9 ._()+-]"), "_") ?: "Target"
        File(dir(context), "$name.csv").writeText(text)
        name
    }
}
