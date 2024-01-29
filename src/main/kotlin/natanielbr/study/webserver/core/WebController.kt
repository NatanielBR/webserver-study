package natanielbr.study.webserver.core

import kotlin.reflect.KCallable
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMembers

open interface WebParameter {
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

open interface WebGetParameter : WebParameter {
    fun isArray(): Boolean
    fun asAsArray(): List<*>
}

open class WebController {
    private val methods = mutableMapOf<String, KCallable<*>>()
    private lateinit var webRequest: WebRequest
    private lateinit var webResponse: WebResponse

    protected val request: WebRequest
        get() = webRequest
    protected val response: WebResponse
        get() = webResponse

    private fun init() {
        val methods = this::class.declaredMembers

        methods.forEach {
            this.methods[it.name] = it
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

    /**
     * Executa o método com o nome [path] e com os parâmetros [parameters]
     * @param path Nome do método a ser executado, sem a barra inicial e de forma relativa
     * @param parameters Parâmetros a serem passados para o método, com & separando os parâmetros
     */
    fun execute(webRequest: WebRequest, parameters: String): WebResponse? {
        if (methods.isEmpty()) {
            init()
        }

        this.webRequest = webRequest
        this.webResponse = WebResponse(
            200, mutableMapOf(
                "content-type" to "text/html",
            ), ""
        )

        val method = methods[webRequest.path] ?: return null

        this.webResponse.body = serializeResponse(method.call(this))

        return this.webResponse
    }

    private fun serializeResponse(response: Any?): String {
        if (response == null) {
            return ""
        }

        return when (response) {
            is String -> {
                response
            }

            else -> {
                response.toString()
            }
        }
    }
}

data class WebRequest(
    val path: String,
    val method: String,
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
