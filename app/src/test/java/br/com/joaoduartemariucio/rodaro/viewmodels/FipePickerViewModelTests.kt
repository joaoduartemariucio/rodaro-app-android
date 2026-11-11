package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.domain.common.errors.RemoteDataError
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeDetail
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipeOption
import br.com.joaoduartemariucio.rodaro.domain.valuation.entities.FipePrice
import br.com.joaoduartemariucio.rodaro.domain.valuation.services.FipeCatalog
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.VehicleKind
import br.com.joaoduartemariucio.rodaro.fakes.FakeAnalytics
import br.com.joaoduartemariucio.rodaro.fakes.FakeFipeCatalog
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.form.fipe.FipePickerViewModel
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.form.fipe.FipePickerViewModel.Step
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FipePickerViewModelTests {
    private val fake = FakeFipeCatalog()
    private val analytics = FakeAnalytics()

    private fun makeViewModel(
        kind: VehicleKind = VehicleKind.CAR,
        catalog: FipeCatalog = fake.catalog,
    ) = FipePickerViewModel(kind, catalog, analytics)

    @Test
    fun `brand, model and year advance step by step and the year returns the detail`() =
        runTest {
            val viewModel = makeViewModel(VehicleKind.MOTORCYCLE)
            viewModel.start()
            assertEquals(Step.BRAND, viewModel.step)
            assertEquals(fake.brands, viewModel.visibleOptions)

            assertNull(viewModel.select(fake.brands.first()))
            assertEquals(Step.MODEL, viewModel.step)
            assertEquals(fake.models, viewModel.options)

            assertNull(viewModel.select(fake.models.first()))
            assertEquals(Step.YEAR, viewModel.step)
            assertEquals(fake.years, viewModel.options)

            val detail = assertNotNull(viewModel.select(FipeOption("2014-3", "2014 Diesel")))
            assertEquals("005340-6", detail.selection.fipeCode)
            assertEquals("2014 Diesel", detail.selection.yearName)
            assertTrue(fake.kinds.all { it == VehicleKind.MOTORCYCLE })
            assertEquals(mapOf("kind" to "MOTORCYCLE"), analytics.parameters("fipe_vehicle_selected"))
        }

    @Test
    fun `the search ignores accents, case and word order`() =
        runTest {
            fake.brands = listOf(FipeOption("1", "Citroën"), FipeOption("2", "Chevrolet"), FipeOption("3", "Mercedes-Benz Sprinter Furgão"))
            val viewModel = makeViewModel()
            viewModel.start()

            viewModel.query = "citroen"
            assertEquals(listOf("1"), viewModel.visibleOptions.map { it.code })
            viewModel.query = "  FURGAO  mercedes "
            assertEquals(listOf("3"), viewModel.visibleOptions.map { it.code })
            viewModel.query = ""
            assertEquals(3, viewModel.visibleOptions.size)
        }

    @Test
    fun `going back keeps the previous list without asking again`() =
        runTest {
            val viewModel = makeViewModel()
            viewModel.start()
            viewModel.select(fake.brands.first())
            viewModel.query = "gol"

            assertTrue(viewModel.back())
            assertEquals(Step.BRAND, viewModel.step)
            assertEquals("", viewModel.query)
            assertFalse(viewModel.back())

            viewModel.start()
            viewModel.select(fake.brands.first())
            viewModel.select(fake.models.first())
            assertTrue(viewModel.back())
            assertEquals(Step.MODEL, viewModel.step)
            viewModel.select(fake.models.first())

            assertEquals(1, fake.calls["brands"])
            assertEquals(1, fake.calls["models"])
            assertEquals(1, fake.calls["years"])
        }

    @Test
    fun `picking another brand asks for its models`() =
        runTest {
            val viewModel = makeViewModel()
            viewModel.start()
            viewModel.select(fake.brands[0])
            viewModel.back()
            viewModel.select(fake.brands[1])

            assertEquals(2, fake.calls["models"])
            assertEquals(fake.brands[1], viewModel.brand)
        }

    @Test
    fun `a failure shows the error and retry asks again on the same step`() =
        runTest {
            val viewModel = makeViewModel()
            fake.failure = RemoteDataError.NetworkUnavailable
            viewModel.start()
            assertSame(RemoteDataError.NetworkUnavailable, viewModel.error)
            assertFalse(viewModel.isLoading)

            fake.failure = null
            viewModel.retry()
            assertNull(viewModel.error)
            assertEquals(fake.brands, viewModel.options)

            fake.failure = RemoteDataError.QuotaExceeded
            viewModel.select(fake.brands.first())
            assertSame(RemoteDataError.QuotaExceeded, viewModel.error)
            fake.failure = null
            viewModel.retry()
            assertEquals(fake.models, viewModel.options)

            fake.failure = RemoteDataError.QuotaExceeded
            viewModel.select(fake.models.first())
            fake.failure = null
            viewModel.retry()
            assertEquals(fake.years, viewModel.options)

            fake.failure = RemoteDataError.NotFound
            assertNull(viewModel.select(fake.years.first()))
            assertSame(RemoteDataError.NotFound, viewModel.error)
            assertNull(analytics.parameters("fipe_vehicle_selected"))
            assertTrue(viewModel.back())
            assertNull(viewModel.error)
        }

    @Test
    fun `cancellation is rethrown`() =
        runTest {
            val cancelling =
                object : FipeCatalog by fake.catalog {
                    override suspend fun brands(kind: VehicleKind): List<FipeOption> = throw CancellationException("gone")

                    override suspend fun detail(
                        kind: VehicleKind,
                        brandCode: String,
                        modelCode: String,
                        year: FipeOption,
                    ): FipeDetail = throw CancellationException("gone")

                    override suspend fun latestPrice(
                        kind: VehicleKind,
                        fipeCode: String,
                        yearCode: String,
                    ): FipePrice = throw CancellationException("gone")
                }
            val viewModel = makeViewModel(catalog = cancelling)

            assertFailsWith<CancellationException> { viewModel.start() }
            assertFalse(viewModel.isLoading)
        }
}
