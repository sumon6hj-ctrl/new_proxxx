package com.khanproxy

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

object TokenStore {
    const val MAX_BUFFER = 200
    private val buffer = ConcurrentLinkedQueue<TokenEntry>()
    private val idGen = AtomicLong(1)

    private val _tokens = MutableLiveData<List<TokenEntry>>(emptyList())
    val tokens: LiveData<List<TokenEntry>> = _tokens

    private val _count = MutableLiveData(0)
    val count: LiveData<Int> = _count

    fun nextId(): Long = idGen.getAndIncrement()

    @Synchronized
    fun add(e: TokenEntry) {
        try {
            buffer.add(e)
            while (buffer.size > MAX_BUFFER) buffer.poll()
            publish()
        } catch (_: Exception) {}
    }

    @Synchronized
    fun clear() {
        try { buffer.clear(); publish() } catch (_: Exception) {}
    }

    fun snapshot(): List<TokenEntry> = buffer.toList()

    private fun publish() {
        try {
            _tokens.postValue(buffer.toList().reversed())
            _count.postValue(buffer.size)
        } catch (_: Exception) {}
    }
}
