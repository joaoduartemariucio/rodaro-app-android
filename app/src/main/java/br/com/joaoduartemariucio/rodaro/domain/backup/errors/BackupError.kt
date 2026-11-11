package br.com.joaoduartemariucio.rodaro.domain.backup.errors

import br.com.joaoduartemariucio.rodaro.domain.backup.calculations.BackupProblem

sealed class BackupError(
    cause: Throwable? = null,
) : Exception(cause) {
    class Unreadable(
        underlying: Throwable? = null,
    ) : BackupError(underlying)

    data class NewerFormat(
        val found: Int,
        val supported: Int,
    ) : BackupError()

    data class Invalid(
        val problems: List<BackupProblem>,
    ) : BackupError()

    data object TooLarge : BackupError()

    class Storage(
        underlying: Throwable,
    ) : BackupError(underlying)
}
