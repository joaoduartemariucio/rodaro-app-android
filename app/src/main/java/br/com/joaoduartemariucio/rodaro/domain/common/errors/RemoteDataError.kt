package br.com.joaoduartemariucio.rodaro.domain.common.errors

sealed class RemoteDataError(
    cause: Throwable? = null,
) : Exception(cause) {
    data object NetworkUnavailable : RemoteDataError()

    data object QuotaExceeded : RemoteDataError()

    data object NotFound : RemoteDataError()

    class Unreadable(
        underlying: Throwable? = null,
    ) : RemoteDataError(underlying)

    class RequestFailed(
        underlying: Throwable,
    ) : RemoteDataError(underlying)
}
