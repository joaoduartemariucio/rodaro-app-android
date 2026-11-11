package br.com.joaoduartemariucio.rodaro.data.backup.services

import android.content.ContentResolver
import android.net.Uri
import androidx.core.net.toUri
import br.com.joaoduartemariucio.rodaro.domain.backup.errors.BackupError
import br.com.joaoduartemariucio.rodaro.domain.backup.services.DocumentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException

class ContentResolverDocumentStore(
    private val limit: Int,
    private val openOutput: (Uri, String) -> OutputStream?,
    private val openInput: (Uri) -> InputStream?,
) : DocumentStore {
    constructor(resolver: ContentResolver, limit: Int) : this(
        limit = limit,
        openOutput = { uri, mode -> resolver.openOutputStream(uri, mode) },
        openInput = { uri -> resolver.openInputStream(uri) },
    )

    override suspend fun write(
        location: String,
        text: String,
    ) = withContext(Dispatchers.IO) {
        storing {
            val uri = location.toUri()
            val stream = output(uri) ?: throw IOException("No output stream")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        }
    }

    override suspend fun read(location: String): String =
        withContext(Dispatchers.IO) {
            storing {
                val stream = openInput(location.toUri()) ?: throw IOException("No input stream")
                stream.reader(Charsets.UTF_8).use { reader ->
                    val text = StringBuilder()
                    val buffer = CharArray(BUFFER_SIZE)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        text.append(buffer, 0, count)
                        if (text.length > limit) throw BackupError.TooLarge
                    }
                    text.toString()
                }
            }
        }

    private fun output(uri: Uri): OutputStream? =
        try {
            openOutput(uri, TRUNCATE)
        } catch (error: IllegalArgumentException) {
            openOutput(uri, WRITE)
        } catch (error: FileNotFoundException) {
            openOutput(uri, WRITE)
        }

    private inline fun <T> storing(block: () -> T): T =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: BackupError) {
            throw error
        } catch (error: Exception) {
            throw BackupError.Storage(error)
        }

    private companion object {
        const val TRUNCATE = "wt"
        const val WRITE = "w"
        const val BUFFER_SIZE = 8 * 1024
    }
}
