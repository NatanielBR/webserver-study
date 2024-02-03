import natanielbr.study.webserver.core.*
import natanielbr.study.webserver.core.TestWebServer.useTestServer
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
    fun testSimplePostArray() = useTestServer(server) {
        val res = post("/sum", """[1,2,3]""", mapOf("Content-Type" to "application/json"))

        assertEquals(200, res.status)
        assertEquals("6", res.body)
    }

    @Test
    fun testMiddleware() = useTestServer(server) {
        it.middlewares.addBottom(ProtectRoute())

        val res = get("/protected")

        assertEquals(403, res.status)
        assertEquals("Unauthorized", res.body)
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