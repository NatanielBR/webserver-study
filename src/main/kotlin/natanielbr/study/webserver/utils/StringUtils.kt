package natanielbr.study.webserver.utils

object StringUtils {

    fun String.count(target: String): Int {
        var count = 0
        var index = 0
        while (index < this.length) {
            val i = this.indexOf(target, index)
            if (i == -1) {
                break
            }
            count++
            index = i + target.length
        }
        return count
    }

}