import natanielbr.study.webserver.core.TestWebServer.useTestServer
import natanielbr.study.webserver.core.WebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class TestServer {

    @Test
    fun testSimpleGet() = useTestServer {
        val res = get("/")

        assertEquals(200, res.status)
        assertEquals("ola!", res.body)
    }

    @Test
    fun testGetWithParameters() = useTestServer {
        val res = get("/ola?name=Natan")

        assertEquals(200, res.status)
        assertEquals("ola Natan!", res.body)
    }

    @Test
    fun testSimplePost() = useTestServer {
        val res = post("/ola", """{"name": "Natan"}""", mapOf("Content-Type" to "application/json"))

        assertEquals(200, res.status)
        assertEquals("ola Natan!", res.body)
    }

}