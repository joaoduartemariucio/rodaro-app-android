package br.com.joaoduartemariucio.rodaro.data.reports.services

import android.net.Uri
import androidx.core.net.toUri
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import br.com.joaoduartemariucio.rodaro.domain.reports.services.ReportFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException

class AndroidReportFileStore(
    private val directory: File,
    private val openOutput: (Uri, String) -> OutputStream?,
    private val uriFor: (File) -> String,
) : ReportFileStore {
    override suspend fun write(
        location: String,
        bytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        storing {
            val stream = openOutput(location.toUri(), TRUNCATE) ?: throw IOException("No output stream")
            stream.use { it.write(bytes) }
        }
    }

    override suspend fun shareable(
        fileName: String,
        bytes: ByteArray,
    ): String =
        withContext(Dispatchers.IO) {
            storing {
                if (!directory.exists() && !directory.mkdirs()) throw IOException("Could not create ${directory.name}")
                directory.listFiles()?.forEach { it.delete() }
                val file = File(directory, File(fileName).name)
                file.writeBytes(bytes)
                uriFor(file)
            }
        }

    private inline fun <T> storing(block: () -> T): T =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw RepositoryError.PersistenceFailed(error)
        }

    private companion object {
        const val TRUNCATE = "wt"
    }
}
