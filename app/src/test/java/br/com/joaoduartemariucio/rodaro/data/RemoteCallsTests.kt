package br.com.joaoduartemariucio.rodaro.data

import br.com.joaoduartemariucio.rodaro.data.network.PublicHttp
import br.com.joaoduartemariucio.rodaro.data.network.remote
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RemoteDataError
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class RemoteCallsTests {
    private fun http(code: Int) = HttpException(Response.error<Unit>(code, "{}".toResponseBody()))

    private suspend fun failure(error: Throwable): Throwable = assertFailsWith<Throwable> { remote<Unit> { throw error } }

    @Test
    fun `a successful call returns its value`() =
        runTest {
            assertEquals(42, remote { 42 })
        }

    @Test
    fun `HTTP 404 means not found and 429 means quota exceeded`() =
        runTest {
            assertSame(RemoteDataError.NotFound, failure(http(404)))
            assertSame(RemoteDataError.QuotaExceeded, failure(http(429)))
        }

    @Test
    fun `other HTTP codes are a failed request`() =
        runTest {
            assertIs<RemoteDataError.RequestFailed>(failure(http(500)))
            assertIs<RemoteDataError.RequestFailed>(failure(http(403)))
        }

    @Test
    fun `connection problems mean the network is unavailable`() =
        runTest {
            assertSame(RemoteDataError.NetworkUnavailable, failure(IOException()))
            assertSame(RemoteDataError.NetworkUnavailable, failure(UnknownHostException()))
            assertSame(RemoteDataError.NetworkUnavailable, failure(SocketTimeoutException()))
        }

    @Test
    fun `a body that does not parse is unreadable`() =
        runTest {
            assertIs<RemoteDataError.Unreadable>(failure(SerializationException("bad")))
            assertIs<RemoteDataError.Unreadable>(failure(IllegalArgumentException("bad")))
        }

    @Test
    fun `anything else is a failed request and remote errors pass through`() =
        runTest {
            assertIs<RemoteDataError.RequestFailed>(failure(IllegalStateException()))
            assertSame(RemoteDataError.NotFound, failure(RemoteDataError.NotFound))
        }

    @Test
    fun `cancellation is rethrown`() =
        runTest {
            assertFailsWith<CancellationException> { remote<Unit> { throw CancellationException("stop") } }
        }

    @Test
    fun `the public client sends the user agent and nothing personal`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("ok"))
            val lines = mutableListOf<String>()
            val client = PublicHttp.client(PublicHttp.userAgent("1.2.3"), log = { lines += it })

            client.newCall(Request.Builder().url(server.url("/x")).build()).execute().close()

            val request = server.takeRequest()
            assertEquals("Rodaro-Android/1.2.3", request.getHeader("User-Agent"))
            assertEquals(null, request.getHeader("Authorization"))
            assertEquals(2, lines.size)
        } finally {
            server.shutdown()
        }
    }
}
