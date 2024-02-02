import natanielbr.study.webserver.core.TestWebServer.useTestServer
import natanielbr.study.webserver.core.WebServer
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