package br.com.joaoduartemariucio.rodaro.data

import br.com.joaoduartemariucio.rodaro.calculations.assertDecimal
import br.com.joaoduartemariucio.rodaro.data.network.FipeApi
import br.com.joaoduartemariucio.rodaro.data.network.FipeDetailDto
import br.com.joaoduartemariucio.rodaro.data.network.FipeNetwork
import br.com.joaoduartemariucio.rodaro.data.network.FipeOptionDto
import br.com.joaoduartemariucio.rodaro.data.valuation.services.FipeHttpCatalog
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RemoteDataError
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeOption
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class FipeHttpCatalogTests {
    private lateinit var server: MockWebServer

    private val catalog by lazy { FipeHttpCatalog(FipeNetwork.api("Rodaro-Android/1.0", server.url("/api/v2/").toString())) }

    private val detailBody =
        """
        {"vehicleType":1,"price":"R$ 85.386,00","brand":"VW - VolksWagen","model":"AMAROK CD2.0 16V/S CD2.0 16V TDI 4x4 Die","modelYear":2014,
         "fuel":"Diesel","codeFipe":"005340-6","referenceMonth":"novembro de 2026","fuelAcronym":"D"}
        """.trimIndent()

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
    fun `brands, models and years are decoded from the car and motorcycle paths`() =
        runTest {
            server.enqueue(MockResponse().setBody("""[{"code":"59","name":"VW - VolksWagen"},{"code":"21","name":"Fiat"}]"""))
            server.enqueue(MockResponse().setBody("""[{"code":"5585","name":"AMAROK CD2.0","extra":1}]"""))
            server.enqueue(MockResponse().setBody("""[{"code":"2014-3","name":"2014 Diesel"},{"code":"32000-1","name":"32000 Gasolina"}]"""))

            assertEquals(listOf(FipeOption("59", "VW - VolksWagen"), FipeOption("21", "Fiat")), catalog.brands(VehicleKind.CAR))
            assertEquals(listOf(FipeOption("5585", "AMAROK CD2.0")), catalog.models(VehicleKind.MOTORCYCLE, "77"))
            assertEquals("2014-3", catalog.years(VehicleKind.CAR, "59", "5585").first().code)

            val first = server.takeRequest()
            assertEquals("/api/v2/cars/brands", first.path)
            assertEquals("Rodaro-Android/1.0", first.getHeader("User-Agent"))
            assertEquals("/api/v2/motorcycles/brands/77/models", server.takeRequest().path)
            assertEquals("/api/v2/cars/brands/59/models/5585/years", server.takeRequest().path)
        }

    @Test
    fun `detail builds the selection from the FIPE code and the chosen year and parses the price`() =
        runTest {
            server.enqueue(MockResponse().setBody(detailBody))

            val detail = catalog.detail(VehicleKind.CAR, "59", "5585", FipeOption("2014-3", "2014 Diesel"))

            assertEquals("005340-6", detail.selection.fipeCode)
            assertEquals("2014-3", detail.selection.yearCode)
            assertEquals("2014 Diesel", detail.selection.yearName)
            assertEquals("VW - VolksWagen", detail.selection.brandName)
            assertEquals(YearMonth.of(2026, 11), detail.price.month)
            assertDecimal("85386.00", detail.price.price)
            val request = server.takeRequest()
            assertEquals("/api/v2/cars/brands/59/models/5585/years/2014-3", request.path)
            assertEquals("no-cache", request.getHeader("Cache-Control"))
        }

    @Test
    fun `the latest price is asked by FIPE code without the cache`() =
        runTest {
            server.enqueue(MockResponse().setBody(detailBody))

            val price = catalog.latestPrice(VehicleKind.MOTORCYCLE, "005340-6", "2014-3")

            assertDecimal("85386.00", price.price)
            val request = server.takeRequest()
            assertEquals("/api/v2/motorcycles/005340-6/years/2014-3", request.path)
            assertEquals("no-cache", request.getHeader("Cache-Control"))
        }

    @Test
    fun `history gives the recent months in order and skips unreadable items`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """
                    {"price":"R$ 85.386,00","brand":"VW","model":"Amarok","codeFipe":"005340-6","referenceMonth":"novembro de 2026",
                     "priceHistory":[{"price":"R$ 85.386,00","month":"novembro de 2026","reference":"339"},
                                     {"price":"R$ 86.010,00","month":"outubro de 2026","reference":"338"},
                                     {"price":"R$ 87.100,00","month":"setembro de 2026","reference":"337"},
                                     {"price":"—","month":"agosto de 2026"},
                                     {"price":"R$ 1,00","month":"smarch 2026"}]}
                    """.trimIndent(),
                ),
            )

            val prices = catalog.recentPrices(VehicleKind.CAR, "005340-6", "2014-3")

            assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), prices.map { it.month })
            assertDecimal("87100.00", prices.first().price)
            assertEquals("/api/v2/cars/005340-6/years/2014-3/history", server.takeRequest().path)
        }

    @Test
    fun `a missing price or month is unreadable`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"brand":"VW","model":"Amarok","codeFipe":"1","referenceMonth":"novembro de 2026"}"""))
            server.enqueue(MockResponse().setBody("""{"price":"R$ 1,00","brand":"VW","model":"Amarok","codeFipe":"1"}"""))

            assertIs<RemoteDataError.Unreadable>(assertFailsWith<RemoteDataError> { catalog.latestPrice(VehicleKind.CAR, "1", "2014-1") })
            assertIs<RemoteDataError.Unreadable>(assertFailsWith<RemoteDataError> { catalog.latestPrice(VehicleKind.CAR, "1", "2014-1") })
        }

    @Test
    fun `HTTP errors, a dropped connection and bad JSON map to remote errors`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"veículo não encontrado"}"""))
            server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"limite"}"""))
            server.enqueue(MockResponse().setBody("not json"))

            assertSame(RemoteDataError.NotFound, assertFailsWith<RemoteDataError> { catalog.brands(VehicleKind.CAR) })
            assertSame(RemoteDataError.QuotaExceeded, assertFailsWith<RemoteDataError> { catalog.brands(VehicleKind.CAR) })
            assertIs<RemoteDataError.Unreadable>(assertFailsWith<RemoteDataError> { catalog.brands(VehicleKind.CAR) })
        }

    @Test
    fun `a server that is gone means the network is unavailable`() =
        runTest {
            val unreachable = catalog
            server.shutdown()

            assertSame(RemoteDataError.NetworkUnavailable, assertFailsWith<RemoteDataError> { unreachable.brands(VehicleKind.CAR) })
        }

    @Test
    fun `cancellation is rethrown`() =
        runTest {
            val cancelling =
                object : FipeApi {
                    override suspend fun brands(type: String): List<FipeOptionDto> = throw CancellationException("gone")

                    override suspend fun models(
                        type: String,
                        brand: String,
                    ): List<FipeOptionDto> = emptyList()

                    override suspend fun years(
                        type: String,
                        brand: String,
                        model: String,
                    ): List<FipeOptionDto> = emptyList()

                    override suspend fun detail(
                        type: String,
                        brand: String,
                        model: String,
                        year: String,
                    ): FipeDetailDto = throw CancellationException("gone")

                    override suspend fun price(
                        type: String,
                        fipeCode: String,
                        year: String,
                    ): FipeDetailDto = throw CancellationException("gone")

                    override suspend fun history(
                        type: String,
                        fipeCode: String,
                        year: String,
                    ): FipeDetailDto = throw CancellationException("gone")
                }

            assertFailsWith<CancellationException> { FipeHttpCatalog(cancelling).brands(VehicleKind.CAR) }
        }
}
