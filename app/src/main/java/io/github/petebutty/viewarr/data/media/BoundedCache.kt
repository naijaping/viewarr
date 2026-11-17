package io.github.petebutty.viewarr.data.media

/**
 * A cache that forgets the least recently used entry once it holds [max] (safe from any thread). The app runs for
 * weeks on a TV that's never switched off, so a plain map of every title ever looked at only grows.
 */
class BoundedCache<K : Any, V : Any>(private val max: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > max
    }

    @Synchronized operator fun get(key: K): V? = map[key]
    @Synchronized operator fun set(key: K, value: V) { map[key] = value }
    @Synchronized fun remove(key: K): V? = map.remove(key)
    @Synchronized fun clear() = map.clear()
}

/** A cached answer and when it stops counting: definite answers keep for long, misses and failures only briefly. */
data class Timed<T>(val value: T, val until: Long) {
    val fresh get() = System.currentTimeMillis() < until

    companion object {
        fun <T> forMs(value: T, ms: Long) = Timed(value, System.currentTimeMillis() + ms)
        /** How long a miss or failure is remembered, so an offline server isn't asked again on every screen. */
        const val MISS_MS = 10 * 60_000L
    }
}
