package natanielbr.study.webserver.utils

class MiddlewareList<T> {
    private val map: MutableMap<Int, T> = mutableMapOf()
    val indexedMiddleware: Map<Int, T>
        get() = map

    private val middleIndex: Int = Int.MAX_VALUE / 2

    /**
     * O topo da lista, começa no meio e vai para cima (ou seja topIndex--).
     */
    private var topIndex: Int = middleIndex

    /**
     * O fundo da lista, começa no meio e vai para baixo (ou seja bottomIndex++).
     * Começa com o valor de middleIndex + 1, pois o meio já foi usado.
     */
    private var bottomIndex: Int = middleIndex + 1

    fun addTop(value: T) {
        map[topIndex] = value
        topIndex--
    }

    fun addBottom(value: T) {
        map[bottomIndex] = value
        bottomIndex++
    }

    fun getOrdered(): List<T> {
        return map.entries.sortedBy { it.key }.map { it.value }
    }

    /*
     * Acredito que seria muito custosso fazer isso, pois teria que reorganizar a lista inteira.
     */
//    fun addMiddle(value: T) {
//
//    }
}