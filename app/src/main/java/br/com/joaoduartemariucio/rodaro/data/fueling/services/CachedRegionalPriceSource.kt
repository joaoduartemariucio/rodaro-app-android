package br.com.joaoduartemariucio.rodaro.data.fueling.services

import br.com.joaoduartemariucio.rodaro.data.network.AnpApi
import br.com.joaoduartemariucio.rodaro.data.network.AnpPricesDto
import br.com.joaoduartemariucio.rodaro.data.network.remote
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RemoteDataError
import br.com.joaoduartemariucio.rodaro.domain.common.services.DateProvider
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.FuelType
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.RegionalPrice
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.RegionalPriceTable
import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.RegionalWeek
import br.com.joaoduartemariucio.rodaro.domain.fueling.services.RegionalPriceSource
import br.com.joaoduartemariucio.rodaro.domain.vehicles.entities.Vehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class CachedRegionalPriceSource(
    private val api: AnpApi,
    private val directory: File,
    private val dateProvider: DateProvider,
    private val maxAge: Duration = Duration.ofHours(24),
) : RegionalPriceSource {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var memory: RegionalPriceTable? = null
    private var lastAttempt: Instant? = null

    private val cacheFile get() = File(directory, "precos-uf.json")
    private val fetchedFile get() = File(directory, "precos-uf.fetched")

    override suspend fun table(): RegionalPriceTable? =
        mutex.withLock {
            withContext(Dispatchers.IO) { load() }
        }

    private suspend fun load(): RegionalPriceTable? {
        val now = dateProvider.now
        val cached = memory ?: readCache()?.also { memory = it }
        val fetchedAt = readFetchedAt()
        if (cached != null && fetchedAt != null && isFresh(fetchedAt, now)) return cached
        if (lastAttempt?.let { isFresh(it, now) } == true) return cached

        lastAttempt = now
        val body =
            try {
                remote { api.prices().string() }
            } catch (error: RemoteDataError) {
                return cached
            }
        val table = parse(body) ?: return cached
        write(body, now)
        memory = table
        return table
    }

    private fun isFresh(
        since: Instant,
        now: Instant,
    ): Boolean = Duration.between(since, now) < maxAge && !since.isAfter(now)

    private fun readCache(): RegionalPriceTable? =
        try {
            if (cacheFile.exists()) parse(cacheFile.readText()) else null
        } catch (error: IOException) {
            null
        }

    private fun readFetchedAt(): Instant? =
        try {
            if (fetchedFile.exists()) {
                fetchedFile
                    .readText()
                    .trim()
                    .toLongOrNull()
                    ?.let(Instant::ofEpochMilli)
            } else {
                null
            }
        } catch (error: IOException) {
            null
        }

    private fun write(
        body: String,
        now: Instant,
    ) {
        try {
            directory.mkdirs()
            replace(cacheFile, body)
            replace(fetchedFile, now.toEpochMilli().toString())
        } catch (error: IOException) {
            Unit
        }
    }

    private fun replace(
        file: File,
        text: String,
    ) {
        val temporary = File(directory, "${file.name}.tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(file)) {
            file.delete()
            if (!temporary.renameTo(file)) throw IOException("Could not replace ${file.name}")
        }
    }

    private fun parse(text: String): RegionalPriceTable? =
        try {
            val dto = json.decodeFromString(AnpPricesDto.serializer(), text)
            if (dto.format != FORMAT || dto.formatVersion != FORMAT_VERSION) {
                null
            } else {
                RegionalPriceTable(
                    updatedAt = Instant.parse(dto.updatedAt),
                    weeks =
                        dto.weeks.map { week ->
                            RegionalWeek(
                                start = LocalDate.parse(week.start),
                                end = LocalDate.parse(week.end),
                                prices =
                                    week.states
                                        .filterKeys { it in Vehicle.UF_CODES }
                                        .mapValues { (_, products) ->
                                            products
                                                .mapNotNull { (key, price) ->
                                                    val type = FuelType.of(key) ?: return@mapNotNull null
                                                    type to
                                                        RegionalPrice(
                                                            price.mean.toBigDecimal(),
                                                            price.sd?.toBigDecimalOrNull(),
                                                            price.stations,
                                                        )
                                                }.toMap()
                                        },
                            )
                        },
                )
            }
        } catch (error: SerializationException) {
            null
        } catch (error: IllegalArgumentException) {
            null
        } catch (error: DateTimeException) {
            null
        }

    private companion object {
        const val FORMAT = "rodaro.anp.precos-uf"
        const val FORMAT_VERSION = 1
    }
}
