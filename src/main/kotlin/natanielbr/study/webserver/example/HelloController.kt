package natanielbr.study.webserver.example

import natanielbr.study.webserver.core.Path
import natanielbr.study.webserver.core.WebController

@Path("/")
class HelloController: WebController() {

    fun index(name: String): String {
        return "ola $name!"
    }

    fun noFoundTest(): String {
        response.status = 404
        return "not found"
    }

    fun throw500Error(): String {
        throw Exception("500 error")
    }
}