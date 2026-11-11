package br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail

import java.time.Duration
import java.time.Instant
import java.util.UUID

class RefreshBackoff(
    private val window: Duration = Duration.ofHours(1),
) {
    private val failures = mutableMapOf<UUID, Instant>()

    fun allows(
        key: UUID,
        now: Instant,
    ): Boolean = failures[key]?.let { Duration.between(it, now) >= window } ?: true

    fun failed(
        key: UUID,
        now: Instant,
    ) {
        failures[key] = now
    }

    fun clear(key: UUID) {
        failures.remove(key)
    }
}
