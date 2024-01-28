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

open interface WebGetParameter: WebParameter {
    fun isArray(): Boolean
    fun asAsArray(): List<*>
}

open class WebController {
    private val methods = mutableMapOf<String, KCallable<*>>()

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
    fun execute(path: String, parameters: String): Any? {
        if (methods.isEmpty()) {
            init()
        }

        val method = methods[path] ?: return null

        return method.call(this)
    }

//    fun execute(message: String) {
//        if (methods.isEmpty()) {
//            init()
//        }
//
//        // message = {"call": "hello", "args": []}
//        val callObj = Utils.GLOBAL_OBJ_MAPPER.readTree(message)
//        if (!callObj.has("call") || !callObj.has("args")) {
//            return
//        }
//
//        val call = callObj["call"].asText()
//        val args = callObj["args"]
//
//        val method = methods[call] ?: return
//
//        // +1 por que o primeiro parametro é o this
//        if (method.parameters.size != args.size() + 1) {
//            return
//        }
//
//        if (method.parameters.size == 1) {
//            method.call(this)
//            return
//        } else {
//            val params = mutableListOf<Any?>()
//            params.add(this)
//
//            args.forEachIndexed { index, it ->
//                val param = method.parameters[index + 1]
//                params.add(parallelizeParam(it, param.type))
//            }
//
//            method.call(*params.toTypedArray())
//        }
//    }

}
