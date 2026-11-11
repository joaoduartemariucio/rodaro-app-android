package br.com.joaoduartemariucio.rodaro.domain.costs.entities

enum class ExpenseCategory(
    val rawValue: String,
    val isFixed: Boolean,
    val countsInCostPerKm: Boolean,
) {
    IPVA("ipva", true, true),
    LICENCIAMENTO("licensing", true, true),
    SEGURO("insurance", true, true),
    PEDAGIO("toll", false, true),
    ESTACIONAMENTO("parking", false, true),
    LAVAGEM("wash", false, true),
    MULTA("fine", false, false),
    FINANCIAMENTO("financing", false, false),
    ACESSORIOS("accessories", false, false),
    OUTROS("other", false, true),
    ;

    companion object {
        fun of(raw: String?): ExpenseCategory = entries.firstOrNull { it.rawValue == raw } ?: OUTROS
    }
}
