package br.com.joaoduartemariucio.rodaro.domain.fueling.services

import br.com.joaoduartemariucio.rodaro.domain.fueling.entities.RegionalPriceTable

interface RegionalPriceSource {
    suspend fun table(): RegionalPriceTable?
}
