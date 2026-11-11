package com.animehub.data.scraper

/** Cache LRU thread-safe com expiração, usado para não baixar a mesma página várias vezes. */
class TtlCache<K : Any, V : Any>(
    private val maxSize: Int,
    private val ttlMs: Long,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private class Entry<V>(val value: V, val at: Long)

    private val map = object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean = size > maxSize
    }

    @Synchronized
    fun get(key: K): V? {
        val e = map[key] ?: return null
        if (now() - e.at > ttlMs) {
            map.remove(key)
            return null
        }
        return e.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = Entry(value, now())
    }

    @Synchronized
    fun clear() = map.clear()
}
