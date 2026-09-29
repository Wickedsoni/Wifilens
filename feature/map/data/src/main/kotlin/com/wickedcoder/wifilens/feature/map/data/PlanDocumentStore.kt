package com.wickedcoder.wifilens.feature.map.data

import android.content.Context
import androidx.core.net.toUri
import com.wickedcoder.wifilens.core.common.IoDispatcher
import com.wickedcoder.wifilens.feature.map.domain.PlanImportException
import com.wickedcoder.wifilens.feature.map.domain.PlanImportProblem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/** A real plan file is a few hundred KB at most (200 x 200 cells); anything bigger isn't one and isn't read in full. */
private const val MAX_PLAN_FILE_BYTES = 4 * 1024 * 1024

/** Reads and writes plan files picked through the Storage Access Framework, so no storage permission is needed. */
class PlanDocumentStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend fun write(uri: String, text: String) = withContext(ioDispatcher) {
            val stream = context.contentResolver.openOutputStream(uri.toUri(), "wt") ?: throw IOException("Cannot open $uri")
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }

        suspend fun read(uri: String): String = withContext(ioDispatcher) {
            val stream = context.contentResolver.openInputStream(uri.toUri()) ?: throw IOException("Cannot open $uri")
            stream.use { input ->
                val bytes = input.readAtMost(MAX_PLAN_FILE_BYTES + 1)
                if (bytes.size > MAX_PLAN_FILE_BYTES) throw PlanImportException(PlanImportProblem.NotAPlanFile)
                bytes.toString(Charsets.UTF_8)
            }
        }
    }

private fun InputStream.readAtMost(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
