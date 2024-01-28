package natanielbr.study.webserver.example

import natanielbr.study.webserver.core.WebController

class HelloController: WebController() {

    fun index(): String {
        return "ola"
    }
}