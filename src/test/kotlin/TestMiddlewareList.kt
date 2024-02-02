import natanielbr.study.webserver.utils.MiddlewareList
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMiddlewareList {

    @Test
    fun testSimpleList() {
        val list = MiddlewareList<String>()
        list.addTop("1")
        list.addBottom("3")
        list.addTop("2")
        list.addBottom("4")

        val ordered = list.getOrdered()
        assertEquals(4, ordered.size)
        assertEquals("2", ordered[0]) // 2 fica acima do 1, pq foi adicionado depois
        assertEquals("1", ordered[1]) // 1 fica abaixo do 2, pq foi adicionado antes
        assertEquals("3", ordered[2]) // 3 fica abaixo do 1, pq foi add no fundo
        assertEquals("4", ordered[3]) // 4 fica abaixo do 3, pq foi add no fundo e depois do 3

    }
}