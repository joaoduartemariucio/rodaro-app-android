package br.com.joaoduartemariucio.rodaro.data

import br.com.joaoduartemariucio.rodaro.calculations.assertDecimal
import br.com.joaoduartemariucio.rodaro.data.fueling.services.CachedRegionalPriceSource
import br.com.joaoduartemariucio.rodaro.data.network.AnpApi
import br.com.joaoduartemariucio.rodaro.data.network.AnpNetwork
import br.com.joaoduartemariucio.rodaro.domain.common.services.DateProvider
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CachedRegionalPriceSourceTests {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: MockWebServer

    private class Clock(
        var current: Instant,
    ) : DateProvider {
        override val now: Instant get() = current
        override val zone: ZoneId = ZoneOffset.UTC
    }

    private val clock = Clock(Instant.parse("2026-11-11T12:00:00Z"))

    private fun body(
        mean: String = "6.360",
        version: Int = 1,
    ) = """
        {"format":"rodaro.anp.precos-uf","formatVersion":$version,"source":"ANP","unit":"BRL/L","updatedAt":"2026-11-10T13:05:12Z","weeks":[
        {"start":"2026-11-01","end":"2026-11-07","states":{"SP":{"gasolinaComum":{"mean":"$mean","sd":"0.380","stations":1286},"gnv":{"mean":"4.5"}},"XX":{"etanol":{"mean":"1"}},"AP":{"etanol":{"mean":"4.930"}}}}
        ]}
        """.trimIndent()

    private val directory: File get() = File(folder.root, "anp")

    private fun source(api: AnpApi = AnpNetwork.api("Rodaro-Android/1.0", server.url("/").toString())) = CachedRegionalPriceSource(api, directory, clock)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a first fetch parses the table, skips unknown products and states and writes the cache`() =
        runTest {
            server.enqueue(MockResponse().setBody(body()))

            val table = assertNotNull(source().table())

            val week = table.weeks.single()
            assertEquals(LocalDate.parse("2026-11-01"), week.start)
            assertEquals(setOf("SP", "AP"), week.prices.keys)
            assertEquals(setOf(FuelType.GASOLINA_COMUM), week.prices.getValue("SP").keys)
            assertDecimal(
                "6.360",
                week.prices
                    .getValue("SP")
                    .getValue(FuelType.GASOLINA_COMUM)
                    .mean,
            )
            assertDecimal(
                "0.380",
                week.prices
                    .getValue("SP")
                    .getValue(FuelType.GASOLINA_COMUM)
                    .standardDeviation,
            )
            assertNull(
                week.prices
                    .getValue("AP")
                    .getValue(FuelType.ETANOL)
                    .standardDeviation,
            )
            assertTrue(File(directory, "precos-uf.json").exists())
            assertEquals("/anp/precos-uf.json", server.takeRequest().path)
        }

    @Test
    fun `a fresh cache makes no request, also after a restart`() =
        runTest {
            server.enqueue(MockResponse().setBody(body()))
            source().table()
            clock.current = clock.current.plus(Duration.ofHours(23))

            assertNotNull(source().table())

            assertEquals(1, server.requestCount)
        }

    @Test
    fun `a stale cache is fetched again and rewritten`() =
        runTest {
            server.enqueue(MockResponse().setBody(body("6.360")))
            server.enqueue(MockResponse().setBody(body("6.500")))
            source().table()
            clock.current = clock.current.plus(Duration.ofHours(25))

            val table = assertNotNull(source().table())

            assertDecimal(
                "6.500",
                table.weeks
                    .single()
                    .prices
                    .getValue("SP")
                    .getValue(FuelType.GASOLINA_COMUM)
                    .mean,
            )
            assertEquals(2, server.requestCount)
            assertTrue(File(directory, "precos-uf.json").readText().contains("6.500"))
        }

    @Test
    fun `a network failure or a wrong version keeps the stale cache`() =
        runTest {
            server.enqueue(MockResponse().setBody(body("6.360")))
            source().table()
            clock.current = clock.current.plus(Duration.ofDays(3))
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody(body("9.999", version = 2)))
            server.enqueue(MockResponse().setBody("{not json"))

            repeat(3) {
                val table = assertNotNull(source().table())
                assertDecimal(
                    "6.360",
                    table.weeks
                        .single()
                        .prices
                        .getValue("SP")
                        .getValue(FuelType.GASOLINA_COMUM)
                        .mean,
                )
            }
            assertEquals(4, server.requestCount)
        }

    @Test
    fun `nothing published and nothing cached gives no table and waits before asking again`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404))
            val source = source()

            assertNull(source.table())
            assertNull(source.table())
            assertEquals(1, server.requestCount)

            server.enqueue(MockResponse().setBody(body()))
            clock.current = clock.current.plus(Duration.ofHours(25))
            assertNotNull(source.table())
        }

    @Test
    fun `cancellation is rethrown`() =
        runTest {
            val cancelling =
                object : AnpApi {
                    override suspend fun prices(): ResponseBody = throw CancellationException("gone")
                }

            assertFailsWith<CancellationException> { source(cancelling).table() }
        }
}
