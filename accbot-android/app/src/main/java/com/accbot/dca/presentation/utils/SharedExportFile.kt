package com.accbot.dca.presentation.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.Duration

internal fun writeSharedExport(context: Context, filename: String, content: String): Uri {
    require(filename == File(filename).name && (filename.endsWith(".csv") || filename.endsWith(".json")))
    val directory = File(context.cacheDir, "exports")
    check(directory.mkdirs() || directory.isDirectory) { "Could not create export directory" }
    val cutoff = System.currentTimeMillis() - Duration.ofDays(1).toMillis()
    directory.listFiles()?.forEach { export ->
        if (export.isFile && export.lastModified() < cutoff) export.delete()
    }
    val file = File(directory, filename)
    file.writeText(content, Charsets.UTF_8)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
