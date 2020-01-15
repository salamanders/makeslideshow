package info.benjaminhill.util

/** Pop off the front of a list without exceptions */
fun <T : Any> MutableList<T>.removeOrNull(): T? {
    return if (this.isNotEmpty()) {
        this.removeAt(0)
    } else {
        null
    }
}