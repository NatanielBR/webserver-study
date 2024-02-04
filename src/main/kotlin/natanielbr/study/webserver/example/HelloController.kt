package natanielbr.study.webserver.example

import natanielbr.study.webserver.core.Get
import natanielbr.study.webserver.core.Path
import natanielbr.study.webserver.core.Post
import natanielbr.study.webserver.core.WebController

@Path("/")
class HelloController : WebController() {

    @Get
    fun index(): String {
        return "ola!"
    }

    @Get
    @Post
    fun ola(name: String): String {
        return "ola $name!"
    }

    @Get("/staticUrl/:file")
    fun staticUrl(file: String): String {
        return file
    }

    @Post
    fun whoAre(user: User): String {
        return "ola ${user.name} (${user.age})!"
    }

    @Post
    fun sum(numeros: List<Int>): String {
        return "${numeros.sum()}"
    }

    @Get
    fun protected(): String {
        return "protected"
    }

    fun noFoundTest(): String {
        response.status = 404
        return "not found"
    }

    fun throw500Error(): String {
        throw Exception("500 error")
    }
}

data class User(val name: String, val age: Int)