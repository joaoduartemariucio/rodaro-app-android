package br.com.joaoduartemariucio.rodaro.data

import br.com.joaoduartemariucio.rodaro.calculations.BackupFixtures
import br.com.joaoduartemariucio.rodaro.calculations.assertDecimal
import br.com.joaoduartemariucio.rodaro.data.backup.serialization.JsonBackupCodec
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.BackupSnapshot
import br.com.joaoduartemariucio.rodaro.domain.backup.entities.PortablePreferences
import br.com.joaoduartemariucio.rodaro.domain.backup.errors.BackupError
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.LanguageOption
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonBackupCodecTests {
    private val codec = JsonBackupCodec(appVersion = "9.9.9")

    private suspend fun encoded(snapshot: BackupSnapshot = BackupFixtures.snapshot()): JsonObject = Json.parseToJsonElement(codec.encode(snapshot)).jsonObject

    private fun JsonObject.first(table: String): JsonObject = getValue(table).jsonArray.first().jsonObject

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

    private fun replacing(
        key: String,
        value: String,
    ): String = """{"format":"rodaro.backup","formatVersion":1,"$key":$value}"""

    @Test
    fun `round trips every table without losing money precision or the route path`() =
        runTest {
            val original = BackupFixtures.snapshot()

            val decoded = codec.decode(codec.encode(original))

            assertEquals(original, decoded)
            assertDecimal("5.899", decoded.fuelEntries.single().pricePerLiter)
            assertDecimal("120.00", decoded.serviceEntries.single().cost)
            assertDecimal("18.40", decoded.savedRoutes.single().tollCost)
        }

    @Test
    fun `money is written as a plain decimal string`() =
        runTest {
            val root = encoded()

            assertEquals(JsonPrimitive("5.899"), root.first("fuelEntries")["pricePerLiter"])
            assertEquals(JsonPrimitive("120.00"), root.first("serviceEntries")["cost"])
            assertEquals(JsonPrimitive("18.40"), root.first("savedRoutes")["tollCost"])
        }

    @Test
    fun `dates are written in UTC with milliseconds`() =
        runTest {
            val root = encoded()

            assertEquals("2025-05-01T18:00:00.000Z", root.text("exportedAt"))
            assertEquals("2025-01-10T12:30:00.123Z", root.first("vehicles").text("createdAt"))
            assertEquals("2025-03-01T10:00:00.000Z", root.first("fuelEntries").text("date"))
        }

    @Test
    fun `enums are written with their raw values`() =
        runTest {
            val root = encoded()

            assertEquals("car", root.first("vehicles").text("kind"))
            assertEquals(
                "motorcycle",
                root
                    .getValue("vehicles")
                    .jsonArray[1]
                    .jsonObject
                    .text("kind"),
            )
            assertEquals("oilChange", root.first("serviceEntries").text("kind"))
            assertEquals("oilChange", root.first("serviceRules").text("kind"))
            val preferences = root.getValue("preferences").jsonObject
            assertEquals("BRL", preferences.text("currency"))
            assertEquals("metric", preferences.text("unitSystem"))
            assertEquals("pt-BR", preferences.text("language"))
        }

    @Test
    fun `the file carries its format, version and the app that wrote it`() =
        runTest {
            val root = encoded()

            assertEquals(JsonBackupCodec.FORMAT, root.text("format"))
            assertEquals("1", root.text("formatVersion"))
            assertEquals("android", root.getValue("app").jsonObject.text("platform"))
            assertEquals("9.9.9", root.getValue("app").jsonObject.text("version"))
        }

    @Test
    fun `the system language is written as system and read back from system or empty`() =
        runTest {
            val snapshot = BackupFixtures.snapshot(preferences = PortablePreferences(language = LanguageOption.SYSTEM))

            assertEquals("system", encoded(snapshot).getValue("preferences").jsonObject.text("language"))
            assertEquals(LanguageOption.SYSTEM, codec.decode(replacing("preferences", """{"language":"system"}""")).preferences?.language)
            assertEquals(LanguageOption.SYSTEM, codec.decode(replacing("preferences", """{"language":""}""")).preferences?.language)
            assertEquals(LanguageOption.SYSTEM, codec.decode(replacing("preferences", """{"language":"klingon"}""")).preferences?.language)
            assertEquals(LanguageOption.ENGLISH, codec.decode(replacing("preferences", """{"language":"en"}""")).preferences?.language)
        }

    @Test
    fun `a file from a newer format version is refused with the versions involved`() =
        runTest {
            val error = assertFailsWith<BackupError.NewerFormat> { codec.decode("""{"format":"rodaro.backup","formatVersion":2}""") }

            assertEquals(2, error.found)
            assertEquals(1, error.supported)
        }

    @Test
    fun `a file without the Rodaro marker is unreadable`() =
        runTest {
            assertFailsWith<BackupError.Unreadable> { codec.decode("""{"formatVersion":1,"vehicles":[]}""") }
            assertFailsWith<BackupError.Unreadable> { codec.decode("""{"format":"other","formatVersion":1}""") }
            assertFailsWith<BackupError.Unreadable> { codec.decode("""{"format":"rodaro.backup"}""") }
            assertFailsWith<BackupError.Unreadable> { codec.decode("""{"format":"rodaro.backup","formatVersion":0}""") }
        }

    @Test
    fun `text that is not JSON is unreadable`() =
        runTest {
            assertFailsWith<BackupError.Unreadable> { codec.decode("Vehicle;Plate\nGol;ABC") }
            assertFailsWith<BackupError.Unreadable> { codec.decode("[1, 2, 3]") }
        }

    @Test
    fun `broken values inside a valid file are unreadable`() =
        runTest {
            val vehicle = """[{"id":"not-a-uuid","name":"Gol","kind":"car","createdAt":"2025-01-01T00:00:00Z"}]"""
            val date = """[{"id":"11111111-1111-4111-8111-111111111111","name":"Gol","kind":"car","createdAt":"yesterday"}]"""
            val missing = """[{"id":"11111111-1111-4111-8111-111111111111"}]"""

            assertIs<BackupError.Unreadable>(assertFailsWith<BackupError> { codec.decode(replacing("vehicles", vehicle)) })
            assertIs<BackupError.Unreadable>(assertFailsWith<BackupError> { codec.decode(replacing("vehicles", date)) })
            assertIs<BackupError.Unreadable>(assertFailsWith<BackupError> { codec.decode(replacing("vehicles", missing)) })
        }

    @Test
    fun `a file over the size limit is too large`() =
        runTest {
            assertFailsWith<BackupError.TooLarge> { codec.decode(" ".repeat(JsonBackupCodec.MAX_CHARACTERS + 1)) }
        }

    @Test
    fun `unknown keys are ignored`() =
        runTest {
            val text = """{"format":"rodaro.backup","formatVersion":1,"fromTheFuture":{"a":1},"vehicles":[]}"""

            assertTrue(codec.decode(text).vehicles.isEmpty())
        }

    @Test
    fun `unknown kinds fall back like the database does`() =
        runTest {
            val text =
                """
                {"format":"rodaro.backup","formatVersion":1,
                 "vehicles":[{"id":"11111111-1111-4111-8111-111111111111","name":"Truck","kind":"truck","createdAt":"2025-01-01T00:00:00Z"}],
                 "serviceEntries":[{"id":"44444444-4444-4444-8444-444444444444","vehicleId":"11111111-1111-4111-8111-111111111111",
                   "kind":"turbo","date":"2025-01-02T00:00:00Z","odometerKm":10.0,"cost":"1"}],
                 "preferences":{"currency":"XYZ","unitSystem":"nautical"}}
                """.trimIndent()

            val decoded = codec.decode(text)

            assertEquals(VehicleKind.CAR, decoded.vehicles.single().kind)
            assertEquals(ServiceKind.OTHER, decoded.serviceEntries.single().kind)
            assertNull(decoded.preferences?.currency)
            assertEquals(PortablePreferences(), decoded.preferences)
        }

    @Test
    fun `a file without preferences restores none`() =
        runTest {
            assertNull(codec.decode("""{"format":"rodaro.backup","formatVersion":1}""").preferences)
        }

    @Test
    fun `uppercase ids are accepted`() =
        runTest {
            val text =
                replacing(
                    "vehicles",
                    """[{"id":"AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA","name":"Gol","kind":"car","createdAt":"2025-01-01T00:00:00Z"}]""",
                )

            assertEquals(
                "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                codec
                    .decode(text)
                    .vehicles
                    .single()
                    .id
                    .toString(),
            )
        }

    @Test
    fun `the version 1 golden file still decodes`() =
        runTest {
            val text = requireNotNull(javaClass.getResource("/backup/v1.json")).readText()

            val decoded = codec.decode(text)

            val expected = BackupFixtures.snapshot()
            assertEquals(expected.vehicles, decoded.vehicles)
            assertEquals(expected.serviceRules, decoded.serviceRules)
            assertEquals(expected.preferences, decoded.preferences)
            assertEquals(Instant.parse("2025-05-01T18:00:00Z"), decoded.exportedAt)
            assertEquals(expected.contents, decoded.contents)
            assertEquals(expected.savedRoutes.single().path, decoded.savedRoutes.single().path)
            assertDecimal("5.899", decoded.fuelEntries.single().pricePerLiter)
            assertDecimal("120.00", decoded.serviceEntries.single().cost)
        }
}
