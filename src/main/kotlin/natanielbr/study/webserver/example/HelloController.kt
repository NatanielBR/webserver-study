package natanielbr.study.webserver.example

import natanielbr.study.webserver.core.Path
import natanielbr.study.webserver.core.WebController

@Path("/")
class HelloController: WebController() {

    fun index(): String {
        return "ola"
    }

    fun noFoundTest(): String {
        response.status = 404
        return "not found"
    }
}