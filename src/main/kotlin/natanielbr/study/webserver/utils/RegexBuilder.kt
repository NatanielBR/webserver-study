package natanielbr.study.webserver.utils

class RegexBuilder {
    val stringBuilder = StringBuilder()

    fun matchText(text: String) {
        stringBuilder.append(text)
    }

    fun matchAnyText(sequence: Boolean = false) {
        if (sequence) {
            stringBuilder.append("([a-zA-Z]+)")
        } else {
            stringBuilder.append("([a-zA-Z]+)?")
        }
    }

    fun matchAnyNumber(sequence: Boolean = false) {
        if (sequence) {
            stringBuilder.append("([0-9]+)")
        } else {
            stringBuilder.append("([0-9]+)?")
        }
    }

    fun matchBoolean() {
        stringBuilder.append("(true|false)")
    }

    fun matchFloatNumber() {
        stringBuilder.append("([0-9]+.[0-9]+)")
    }

    fun validate(target: String): Boolean {
        return target.matches(Regex(stringBuilder.toString()))
    }
}