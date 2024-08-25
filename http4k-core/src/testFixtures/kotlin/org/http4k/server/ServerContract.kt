package org.http4k.server

import com.natpryce.hamkrest.allOf
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.anyOf
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase
import org.apache.hc.client5.http.impl.classic.HttpClients.createDefault
import org.apache.hc.core5.http.ClassicHttpResponse
import org.http4k.core.Body
import org.http4k.core.ContentType.Companion.TEXT_PLAIN
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.ACCEPTED
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.OK
import org.http4k.core.StreamBody
import org.http4k.core.with
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasHeader
import org.http4k.hamkrest.hasStatus
import org.http4k.lens.binary
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.URI.create

abstract class ServerContract(
    private val serverConfig: (Int) -> ServerConfig, protected val client: HttpHandler,
    private val requiredMethods: Array<Method> = Method.entries.toTypedArray()
) {
    private lateinit var server: Http4kServer

    protected val baseUrl by lazy { "http://localhost:${server.port()}" }

    private val size = 1000 * 1024
    private val random = (0 until size).map { '.' }.joinToString("")

    private val routes =
        requiredMethods.map { m ->
            "/" + m.name bind m to { Response(OK).body(m.name) }
        } + listOf(
            "/headers" bind GET to {
                Response(ACCEPTED).header("content-type", "text/plain")
            },
            "/large" bind GET to { Response(OK).body((0..size).map { '.' }.joinToString("")) },
            "/large" bind POST to { Response(OK).body((0..size).map { '.' }.joinToString("")) },
            "/stream" bind GET to { Response(OK).with(Body.binary(TEXT_PLAIN).toLens() of "hello".byteInputStream()) },
            "/presetlength" bind GET to { Response(OK).header("Content-Length", "0") },
            "/echo" bind POST to { Response(OK).body(it.bodyString()) },
            "/request-headers" bind GET to { request: Request ->
                Response(OK).body(
                    request.headerValues("foo").joinToString("\n") { "foo: $it" }
                )
            },
            "/length" bind { req: Request ->
                when (req.body) {
                    is StreamBody -> Response(OK).body(req.body.length.toString())
                    else -> Response(INTERNAL_SERVER_ERROR).body("Expected stream body")
                }
            },
            "/uri" bind GET to { Response(OK).body(it.uri.toString()) },
            "/version" bind GET to { Response(OK).body(it.version) },
            "/multiple-headers" bind GET to { Response(OK).header("foo", "value1").header("foo", "value2") },
            "/boom" bind GET to { throw IllegalArgumentException("BOOM!") },
            "/request-source" bind GET to { request ->
                Response(OK)
                    .header("x-address", request.source?.address ?: "")
                    .header("x-port", (request.source?.port ?: 0).toString())
                    .header("x-scheme", (request.source?.scheme ?: "unsupported").toString())
            },
            "/status-with-foobar-description" bind GET to {
                Response(Status(201, "FooBar"))
            },
            "/null-header-value" bind GET to {
                Response(OK).header("header", null)
            }
        )

    @BeforeEach
    fun before() {
        server = routes(*routes.toTypedArray()).asServer(serverConfig(0)).start()
    }

    @Test
    fun `can call an endpoint with all supported Methods`() {
        for (method in requiredMethods) {

            val response = client(Request(method, baseUrl + "/" + method.name))

            assertThat(response.status, equalTo(OK))
            if (method == Method.HEAD) assertThat(response.body, equalTo(Body.EMPTY))
            else assertThat(response.bodyString(), equalTo(method.name))
        }
    }

    @Test
    fun `null header`() {
        val response = client(Request(GET, "$baseUrl/null-header-value"))
        assertThat(response.status, equalTo(OK))
    }

    @Test
    fun `http version`() {
        val response = client(Request(GET, "$baseUrl/version"))
        assertThat(response.bodyString(), equalTo("HTTP/1.1"))
    }

    @Test
    open fun `can return a large body - GET`() {
        val response = client(Request(GET, "$baseUrl/large").body("hello mum"))

        assertThat(response.status, equalTo(OK))
        assertThat(response.bodyString().length, equalTo(random.length + 1))
    }

    @Test
    open fun `can return a large body - POST`() {
        val response = client(Request(POST, "$baseUrl/large").body("hello mum"))

        assertThat(response.status, equalTo(OK))
        assertThat(response.bodyString().length, equalTo(random.length + 1))
    }

    @Test
    fun `gets the body from the request`() {
        val response = client(Request(POST, "$baseUrl/echo").body("hello mum"))

        assertThat(response.status, equalTo(OK))
        assertThat(response.bodyString(), equalTo("hello mum"))
    }

    @Test
    fun `can set multiple headers with the same name`() {
        val response = client(Request(GET, "$baseUrl/multiple-headers"))

        assertThat(response.status, equalTo(OK))
        assertThat(response, hasHeader("foo", listOf("value1", "value2")))
    }

    @Test
    fun `returns headers`() {
        val response = client(Request(GET, "$baseUrl/headers"))
        assertThat(response.status, equalTo(ACCEPTED))
        assertThat(response.headerValues("content-type").size, equalTo(1))
        assertThat(response.header("content-type"), equalTo("text/plain"))
    }

    @Test
    fun `can handle quotes in request headers`() {
        val response = client(
            Request(GET, "$baseUrl/request-headers")
                .header("foo", """"my header with quotes"""")
                .header("foo", """cookie="value"""")
                .header("foo", """use "cookie=\"value\"" instead.""")
        )
        assertThat(response.status, equalTo(OK))
        assertThat(
            response.bodyString(), equalTo(
                """foo: "my header with quotes"
              |foo: cookie="value"
              |foo: use "cookie=\"value\"" instead.""".trimMargin()
            )
        )
    }

    @Test
    fun `length is set on body if it is sent`() {
        val response = client(
            Request(POST, "$baseUrl/length")
                .body("12345").header("Content-Length", "5")
        )
        assertThat(response, hasStatus(OK).and(hasBody("5")))
    }

    @Test
    fun `length is ignored on body if it not well formed`() {
        val response = client(Request(POST, "$baseUrl/length").header("Content-Length", "nonsense").body("12345"))
        assertThat(response, hasStatus(OK).and(hasBody("5")))
    }

    @Test
    fun `gets the uri from the request`() {
        val response = client(Request(GET, "$baseUrl/uri?bob=bill"))

        assertThat(response.status, equalTo(OK))
        assertThat(response.bodyString(), equalTo("/uri?bob=bill"))
    }

    @Test
    fun `endpoint that blows up results in 500`() {
        val response = client(Request(GET, "$baseUrl/boom"))

        assertThat(response.status, equalTo(INTERNAL_SERVER_ERROR))
    }

    @Test
    fun `can handle multiple request headers`() {
        val response = client(
            Request(GET, "$baseUrl/request-headers").header("foo", "one").header("foo", "two").header("foo", "three")
        )

        assertThat(response.status, equalTo(OK))
        assertThat(
            response.bodyString(), equalTo(
                """foo: one
              |foo: two
              |foo: three""".trimMargin()
            )
        )
    }

    @Test
    open fun `treats multiple request headers as single item comma-separated list`() {
        val response = client(
            Request(GET, "$baseUrl/request-headers").header("foo", "one,two,three")
        )

        assertThat(response.status, equalTo(OK))
        assertThat(response.bodyString(), equalTo("foo: one,two,three"))
    }

    @Test
    fun `deals with streaming response`() {
        val response = client(Request(GET, "$baseUrl/stream"))

        assertThat(response.status, equalTo(OK))
        assertThat(response.bodyString(), equalTo("hello"))
    }

    @Test
    open fun `ok when length already set`() {
        val response = client(Request(GET, "$baseUrl/presetlength"))
        assertThat(response.status, equalTo(OK))
        assertThat(response.header("content-length"), equalTo("0"))
    }

    @Test
    open fun `can start on port zero and then get the port`() {
        routes(*routes.toTypedArray()).asServer(serverConfig(0)).start().use {
            assertThat(client(Request(GET, "http://localhost:${it.port()}/uri")).status, equalTo(OK))
        }
    }

    @Test
    fun `returns 501 on illegal request`() {
        val client = createDefault()
        client.use {
            assertThat(
                client.execute(HttpUriRequestBase("FOOBAR", create(baseUrl)), ClassicHttpResponse::getCode),
                equalTo(501)
            )
        }
    }

    @Test
    fun `can resolve request source`() {
        assertThat(
            client(Request(GET, "$baseUrl/request-source")),
            allOf(
                hasStatus(OK),
                hasHeader("x-address", clientAddress()),
                hasHeader("x-port", present()),
                hasHeader("x-scheme", requestScheme())
            )
        )
    }

    @Test
    open fun `illegal url doesn't expose stacktrace`() {
        assertThat(
            client(Request(GET, "$baseUrl/v1/foo//bar")).bodyString().lowercase(),
            !containsSubstring("exception")
        )
    }

    open fun clientAddress() = anyOf(
        equalTo(InetAddress.getLoopbackAddress().hostAddress),
        equalTo(InetAddress.getLocalHost().hostAddress),
    )

    open fun requestScheme() = equalTo("unsupported")

    @AfterEach
    fun after() {
        server.stop()
    }
}
