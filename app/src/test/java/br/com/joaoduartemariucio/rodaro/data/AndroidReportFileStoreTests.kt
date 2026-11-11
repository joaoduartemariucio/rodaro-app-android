package br.com.joaoduartemariucio.rodaro.data

import br.com.joaoduartemariucio.rodaro.data.reports.services.AndroidReportFileStore
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AndroidReportFileStoreTests {
    @get:Rule
    val folder = TemporaryFolder()

    private val outputs = mutableMapOf<String, ByteArrayOutputStream>()
    private val modes = mutableListOf<String>()

    private fun store(directory: File = File(folder.root, "reports")) =
        AndroidReportFileStore(
            directory = directory,
            openOutput = { uri, mode ->
                modes += mode
                ByteArrayOutputStream().also { outputs[uri.toString()] = it }
            },
            uriFor = { "content://files/${it.name}" },
        )

    @Test
    fun `sharing removes older reports and writes the new one`() =
        runTest {
            val directory = File(folder.root, "reports").apply { mkdirs() }
            val old = File(directory, "old.pdf").apply { writeText("old") }

            val uri = store(directory).shareable("../new.pdf", byteArrayOf(1, 2, 3))

            assertEquals("content://files/new.pdf", uri)
            assertFalse(old.exists())
            assertContentEquals(byteArrayOf(1, 2, 3), File(directory, "new.pdf").readBytes())
            assertEquals(listOf("new.pdf"), directory.list()?.toList())
        }

    @Test
    fun `saving writes the bytes to the picked document, truncating it`() =
        runTest {
            store().write("content://docs/report.pdf", byteArrayOf(9, 8))

            assertContentEquals(byteArrayOf(9, 8), outputs.getValue("content://docs/report.pdf").toByteArray())
            assertEquals(listOf("wt"), modes)
        }

    @Test
    fun `storage errors are wrapped and cancellation is rethrown`() =
        runTest {
            val noStream = AndroidReportFileStore(folder.root, { _, _ -> null }, { it.name })
            assertFailsWith<RepositoryError.PersistenceFailed> { noStream.write("content://x", byteArrayOf(1)) }

            val blocked = File(folder.root, "file").apply { writeText("x") }
            assertFailsWith<RepositoryError.PersistenceFailed> { store(File(blocked, "reports")).shareable("a.pdf", byteArrayOf(1)) }

            val cancelling = AndroidReportFileStore(folder.root, { _, _ -> throw CancellationException("gone") }, { it.name })
            assertFailsWith<CancellationException> { cancelling.write("content://x", byteArrayOf(1)) }
        }
}
