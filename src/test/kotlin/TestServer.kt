import natanielbr.study.webserver.core.*
import natanielbr.study.webserver.tests.TestWebServer.useTestServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test
import kotlin.test.assertEquals

class TestServer {

    @Test
    fun testSimpleGet() = useTestServer(server) {
        val res = get("/")

        assertEquals(200, res.status)
        assertEquals("ola!", res.body)
    }

    @Test
    fun testGetUserObject() = useTestServer(server) {
        val res = get("/userObject")

        assertEquals(200, res.status)
        assertEquals("""{"name":"Natan","age":24}""", res.body)
    }

    @Test
    fun testPathParameter() = useTestServer(server) {
        val res = get("/staticUrl/teste.txt")

        assertEquals(200, res.status)
        assertEquals("teste.txt", res.body)
    }

    @Test
    fun testInnerPath() = useTestServer(server) {
        val res = get("/product/1")

        assertEquals(200, res.status)
        assertEquals("product 1", res.body)
    }

    @Test
    fun testGetWithParameters() = useTestServer(server) {
        val res = get("/ola?name=Natan")

        assertEquals(200, res.status)
        assertEquals("ola Natan!", res.body)
    }

    @Test
    fun testSimplePost() = useTestServer(server) {
        val res = post("/ola", """{"name": "Natan"}""", mapOf("Content-Type" to "application/json"))

        assertEquals(200, res.status)
        assertEquals("ola Natan!", res.body)
    }

    @Test
    fun testPost() = useTestServer(server) {
        val res = post("/whoAre", """{"name": "Natan", "age": 24}""", mapOf("Content-Type" to "application/json"))

        assertEquals(200, res.status)
        assertEquals("ola Natan (24)!", res.body)
    }

    @Test
    fun testSimplePostArray() = useTestServer(server) {
        val res = post("/sum", """[1,2,3]""", mapOf("Content-Type" to "application/json"))

        assertEquals(200, res.status)
        assertEquals("6", res.body)
    }

    @Test
    fun testMiddlewareBefore() = useTestServer(server) {
        it.middlewares.addBottom(ProtectRoute())

        val res = get("/protected")

        assertEquals(403, res.status)
        assertEquals("Unauthorized", res.body)
    }

    @Test
    fun testMiddlewareAfter() = useTestServer(server) {
        it.middlewares.addBottom(AddHeader())

        val res = get("/")

        assertEquals(200, res.status)
        assertEquals("ola!", res.body)
        assertEquals("Kotlin", res.headers["x-powered-by"]!![0])
    }

    companion object {
        lateinit var server: WebServer;
        @JvmStatic
        @BeforeAll
        fun setup(): Unit {
            server = WebServer()
        }

        @JvmStatic
        @AfterAll
        fun close(): Unit {
            server.close()
        }
    }

}

class ProtectRoute: MiddlewareAdapter() {
    override fun before(request: RequestData): RequestData {
        if (request.path == "/protected") {
            throw HttpException("Unauthorized", 403)
        }
        return request
    }
}

class AddHeader: MiddlewareAdapter() {
    override fun after(response: WebResponse): WebResponse {
        response.headers["x-powered-by"] = "Kotlin"
        return response
    }
}