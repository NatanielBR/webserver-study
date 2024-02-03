package natanielbr.study.webserver.core

import com.fasterxml.jackson.databind.ObjectMapper
import natanielbr.study.webserver.core.WebServer.Companion.serializeParameter
import natanielbr.study.webserver.core.WebServer.Companion.serializeResponse
import kotlin.reflect.KCallable
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.javaType
import kotlin.reflect.jvm.jvmErasure

interface WebParameter {
    fun isText(): Boolean
    fun asText(): String
    fun isInt(): Boolean
    fun asInt(): String
    fun isLong(): Boolean
    fun asLong(): String
    fun isDouble(): Boolean
    fun asDouble(): String
    fun isBoolean(): Boolean
    fun asBoolean(): String
    fun isNull(): Boolean
//    fun isArray(): Boolean
}

interface WebGetParameter : WebParameter {
    fun isArray(): Boolean
    fun asAsArray(): List<*>
}

open class WebController : HttpErrorHandlers {
    private val methods = mutableMapOf<String, KCallable<*>>()
    private lateinit var webRequest: WebRequest
    private lateinit var webResponse: WebResponse

    protected val request: WebRequest
        get() = webRequest
    protected val response: WebResponse
        get() = webResponse

    fun initialize() {
        val methods = this::class.declaredMembers

        methods.forEach {
            val postAnnotation = it.annotations.find { it is Post } as? Post
            var method = if (postAnnotation != null) {
                "post"
            } else {
                "get"
            }

            this.methods["${method}_${it.name}"] = it

            // has Get annotation
            if (it.annotations.find { it is Get } != null) {
                this.methods["get_${it.name}"] = it
            }
        }
    }

    private fun parallelizeParam(node: WebParameter, methodParam: KType): Any? {
        // check if T is String
        if (node.isText()) {
            return node.asText()
        }

        // check if T is Int
        if (node.isInt()) {
            return node.asInt()
        } else if (node.isLong()) {
            return node.asLong()
        }

        // check if T is Float
        if (node.isDouble()) {
            return node.asDouble()
        }

        // check if T is Boolean
        if (node.isBoolean()) {
            return node.asBoolean()
        }

//        if (node.isObject) {
//            val obj = Utils.GLOBAL_OBJ_MAPPER.treeToValue(node, methodParam.jvmErasure.java)
//            return obj!!
//        }

//        if (node.isArray) {
//            val list = mutableListOf<Any?>()
//            node.forEach {
//                val arrType = methodParam.arguments[0]
//                list.add(parallelizeParam(it, arrType.type!!))
//            }
//            return list
//        }

        if (node.isNull()) {
            return null
        }

        throw NotImplementedError()
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun runEndpoint(method: KCallable<*>, httpParameters: Map<String, Any?>): Any? {
        val methodParams = mutableListOf<Any>()

        method.parameters.forEach {
            if (it.type.jvmErasure.isInstance(this)) {
                // first parameter is instance
                methodParams.add(this)
            } else {

                if (
                    (it.type.jvmErasure.java == List::class.java || it.type.jvmErasure.java == Array::class.java)
                    && httpParameters.containsKey("_")
                ) {
                    // parametro é um array e a raiz do json é um array
                    val serialized = serializeParameter(httpParameters["_"]!!, it.type.javaType)
                    methodParams.add(serialized)

                } else {
                    // any parameter
                    val paramValue = httpParameters[it.name] ?: throw Exception("Parameter ${it.name} not found")
                    val serialized = serializeParameter(paramValue, it.type.javaType)

                    methodParams.add(serialized)
                }

            }
        }

        return method.call(*methodParams.toTypedArray())
    }

    /**
     * Executa o método com o nome [path] e com os parâmetros [parameters]
     * @param path Nome do método a ser executado, sem a barra inicial e de forma relativa
     * @param parameters Parâmetros a serem passados para o método, com & separando os parâmetros
     */
    fun execute(webRequest: WebRequest): WebResponse {
        this.webRequest = webRequest
        webResponse = WebResponse(
            200, mutableMapOf(
                "content-type" to "text/html",
            ), ""
        )

        val method = methods["${request.method.lowercase()}_${webRequest.path}"]

        if (method == null) {
            webResponse = error404(webResponse)
            return webResponse
        }

        this.webResponse.body = serializeResponse(runEndpoint(method, request.body))

        return this.webResponse
    }

}

data class WebRequest(
    val path: String,
    val method: String,
    val headers: Map<String, String>,
    val body: Map<String, Any?>,
    val absolutePath: String
)

class WebResponse(
    var status: Int,
    val headers: MutableMap<String, String>,
    var body: String
) {
    fun serialize(): String {
        val builder = StringBuilder()

        builder.append("HTTP/1.1 $status\r\n")

        headers.forEach { (key, value) ->
            builder.append("$key: $value\r\n")
        }

        builder.append("\r\n")
        builder.append(body)

        return builder.toString()
    }
}

annotation class Post(val contentType: String = "application/json")
annotation class Get()
