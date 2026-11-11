package br.com.joaoduartemariucio.rodaro.data

import br.com.joaoduartemariucio.rodaro.fakes.TestLocalizer
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals

class BackupRulesTests {
    private val expected =
        setOf(
            "database:rodaro.db",
            "sharedpref:preferences.xml",
            "file:androidx.appcompat.app.AppCompatDelegate.application_locales_record_file",
        )

    private fun includes(
        file: String,
        section: String,
    ): Set<String> {
        val document =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(TestLocalizer.resourceRoot(), "xml/$file"))
        val parent = document.getElementsByTagName(section).item(0) as Element
        val nodes = parent.getElementsByTagName("include")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .map { "${it.getAttribute("domain")}:${it.getAttribute("path")}" }
            .toSet()
    }

    @Test
    fun `cloud backup and device transfer keep only the database, the preferences and the app language`() {
        assertEquals(expected, includes("data_extraction_rules.xml", "cloud-backup"))
        assertEquals(expected, includes("data_extraction_rules.xml", "device-transfer"))
    }

    @Test
    fun `the rules for Android 11 and older keep the same files`() {
        assertEquals(expected, includes("backup_rules.xml", "full-backup-content"))
    }
}
