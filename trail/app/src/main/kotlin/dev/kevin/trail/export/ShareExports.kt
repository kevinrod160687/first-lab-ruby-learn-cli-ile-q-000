package dev.kevin.trail.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

fun shareTextFile(context: Context, fileName: String, mimeType: String, contents: String) {
    val directory = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(directory, fileName)
    file.writeText(contents)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}
