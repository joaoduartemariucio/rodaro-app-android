package br.com.joaoduartemariucio.rodaro.data.network

import br.com.joaoduartemariucio.rodaro.domain.common.errors.RemoteDataError
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun <T> remote(call: suspend () -> T): T =
    try {
        call()
    } catch (error: CancellationException) {
        throw error
    } catch (error: RemoteDataError) {
        throw error
    } catch (error: HttpException) {
        throw when (error.code()) {
            HTTP_NOT_FOUND -> RemoteDataError.NotFound
            HTTP_TOO_MANY_REQUESTS -> RemoteDataError.QuotaExceeded
            else -> RemoteDataError.RequestFailed(error)
        }
    } catch (error: IOException) {
        currentCoroutineContext().ensureActive()
        throw RemoteDataError.NetworkUnavailable
    } catch (error: SerializationException) {
        throw RemoteDataError.Unreadable(error)
    } catch (error: IllegalArgumentException) {
        throw RemoteDataError.Unreadable(error)
    } catch (error: Exception) {
        throw RemoteDataError.RequestFailed(error)
    }

private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429
