package info.benjaminhill.util

fun <T : Any> MutableList<T>.removeOrNull(): T? {
    return if (this.isNotEmpty()) {
        this.removeAt(0)
    } else {
        null
    }
}