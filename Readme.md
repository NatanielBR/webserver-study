# This framework is for study, dont use it in development or production.

# Motivation
I have already tried to use Spring Boot, Quarkus, Django, FastAPI... But i liked the most the Django.
Django has a web framework, ORM, CLI, Admin, Auth, and a lot of other things.

Spring has a same things too, but is not too simple like Django.

Thinking about it, for study and fun (i'm strange, yes), i decided to create a framework like Django, but in Kotlin.

# Features
- [x] Web Framework
- [ ] ORM
- [ ] Schema Migration
- and other things that i will think.

# Web Framework features
- [x] Router
- [x] Request serializer
- [x] Response serializer
- [x] Middleware
- [x] Error Handler
- [x] Url parameters
- [x] Get Query parameters

# Guide
## Quick start
This a quick start with principal features of the web framework.

```kotlin

@Path("/")
class QuickStart : WebController() {

    @Get
    fun index(): String {
        return "hello"
    }

    /**
     * Get parameters will convert to the method parameters
     */
    @Get("/whoAre")
    fun whoAre(user: String): String {
        return "hello ${user}!"
    }

    /**
     * Path parameters will convert to the method parameters too
     */
    @Get("/product/:id")
    fun product(id: Int): String {
        return "product $id"
    }

    @Get("/product/:id/edit")
    fun productEdit(id: Int): String {
        return "done"
    }

    @Get("/product/promotion")
    fun productPromotion(): Product {
        // need to set the content type to application/json
        // to return a json object, otherwise it will return a string (product.toString())
        response.contentType = "application/json"
        return Product(1, "product 1")
    }
}

class SimpleMiddleware : MiddlewareAdapter() {
    override fun before(request: RequestData): RequestData {
        // here you can change the request data

        if (request.path.startsWith("product") && request.path.endsWith("/edit")) {
            // not allow edit, return 403
            throw HttpException(
                "Not allowed", 403, mapOf() // headers (optional)
            )
        }

        return request
    }

    override fun after(response: WebResponse): WebResponse {
        // here you can change the response data
        // I will add a header to all responses

        response.headers["x-powered-by"] = "WebServer"

        return response
    }
}

class HttpErrorHandler : HttpErrorHandlers {
    override fun error404(webResponse: WebResponse): WebResponse {
        // here you can change the response data when the path is not found
        webResponse.body = "<h1>Error 404 Not found</h1>"

        return webResponse
    }

    // has error 500 too
}

data class Product(val id: Int, val name: String)

fun main() {
    val server = WebServer(8080)

    // change the default error handler
    server.globalErrorHandler = HttpErrorHandler()

    // server.middlewares.addTop() // we can add middlewares to the top of the list
    // but i'm going to add to the bottom
    server.middlewares.addBottom(SimpleMiddleware())

    server.start() // not blocking

    // server.close() // stop server

    // http://localhost:8080 -> hello
    // http://localhost:8080/whoAre?user=Natan -> hello Natan!
    // http://localhost:8080/product/1 -> product 1
    // http://localhost:8080/product/1/edit -> Error 403 Not allowed
    // http://localhost:8080/aaa -> Error 403 <h1>Error 404 Not found</h1>
}

````