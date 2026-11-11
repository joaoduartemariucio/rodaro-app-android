package br.com.joaoduartemariucio.rodaro.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import br.com.joaoduartemariucio.rodaro.data.backup.services.ContentResolverDocumentStore
import br.com.joaoduartemariucio.rodaro.domain.backup.errors.BackupError
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
class ContentResolverDocumentStoreTests {
    @get:Rule
    val folder = TemporaryFolder()

    private val modes = mutableListOf<String>()

    private fun fileStore(limit: Int = 1_024) =
        ContentResolverDocumentStore(
            limit = limit,
            openOutput = { uri, mode ->
                modes += mode
                FileOutputStream(File(requireNotNull(uri.path)))
            },
            openInput = { uri -> File(requireNotNull(uri.path)).inputStream() },
        )

    private fun rootCause(error: Throwable): Throwable = generateSequence(error) { it.cause }.last()

    private fun location(name: String): String = Uri.fromFile(File(folder.root, name)).toString()

    @Test
    fun `writes and reads text as UTF-8`() =
        runTest {
            val store = fileStore()
            val text = "Óleo 5W30 — São Paulo ✓"

            store.write(location("backup.json"), text)

            assertEquals(text, store.read(location("backup.json")))
            assertEquals(text, File(folder.root, "backup.json").readText(Charsets.UTF_8))
            assertEquals(listOf("wt"), modes)
        }

    @Test
    fun `overwriting a longer file leaves no trailing bytes`() =
        runTest {
            val store = ContentResolverDocumentStore(ApplicationProvider.getApplicationContext<Context>().contentResolver, 1_024)
            val target = File(folder.root, "backup.json")
            target.writeText("a much longer previous content")

            store.write(Uri.fromFile(target).toString(), "short")

            assertEquals("short", target.readText())
            assertEquals("short", store.read(Uri.fromFile(target).toString()))
        }

    @Test
    fun `falls back to plain write mode when truncation is not supported`() =
        runTest {
            val written = File(folder.root, "drive.json")
            for (refusal in listOf(IllegalArgumentException("wt"), FileNotFoundException("wt"))) {
                modes.clear()
                val store =
                    ContentResolverDocumentStore(
                        limit = 1_024,
                        openOutput = { _, mode ->
                            modes += mode
                            if (mode == "wt") throw refusal
                            written.outputStream()
                        },
                        openInput = { null },
                    )

                store.write("content://drive/1", "{}")

                assertEquals(listOf("wt", "w"), modes)
                assertEquals("{}", written.readText())
            }
        }

    @Test
    fun `a file over the limit is too large`() =
        runTest {
            val store = fileStore(limit = 10)
            File(folder.root, "big.json").writeText("x".repeat(20_000))

            assertFailsWith<BackupError.TooLarge> { store.read(location("big.json")) }
        }

    @Test
    fun `a missing stream or a provider failure becomes a storage error`() =
        runTest {
            val noStreams = ContentResolverDocumentStore(limit = 10, openOutput = { _, _ -> null }, openInput = { null })
            val failing =
                ContentResolverDocumentStore(
                    limit = 10,
                    openOutput = { _, _ -> throw SecurityException("revoked") },
                    openInput = { throw IOException("gone") },
                )

            assertIs<IOException>(rootCause(assertFailsWith<BackupError.Storage> { noStreams.write("content://x/1", "{}") }))
            assertIs<IOException>(rootCause(assertFailsWith<BackupError.Storage> { noStreams.read("content://x/1") }))
            assertIs<SecurityException>(rootCause(assertFailsWith<BackupError.Storage> { failing.write("content://x/1", "{}") }))
            assertIs<IOException>(rootCause(assertFailsWith<BackupError.Storage> { failing.read("content://x/1") }))
        }

    @Test
    fun `writing to a location no provider can open is a storage error`() =
        runTest {
            val store = ContentResolverDocumentStore(ApplicationProvider.getApplicationContext<Context>().contentResolver, 1_024)

            assertFailsWith<BackupError.Storage> { store.write("content://nobody.home/backup.json", "{}") }
        }

    @Test
    fun `cancellation while opening a document is rethrown as is`() =
        runTest {
            val store =
                ContentResolverDocumentStore(
                    limit = 10,
                    openOutput = { _, _ -> throw CancellationException("left the screen") },
                    openInput = { null },
                )

            assertFailsWith<CancellationException> { store.write("content://x/1", "{}") }
        }
}
