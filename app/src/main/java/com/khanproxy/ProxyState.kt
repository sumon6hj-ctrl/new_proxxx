package com.khanproxy

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

enum class Status { STOPPED, STARTING, RUNNING, STOPPING, ERROR }

object ProxyState {
    const val PORT = 8080
    const val HOST = "127.0.0.1"

    val REGIONS = linkedMapOf(
        "1" to (Pair("BD",     "clientbp.ggpolarbear.com")),
        "2" to (Pair("ME",     "clientbp.ggpolarbear.com")),
        "3" to (Pair("IND",    "client.ind.freefiremobile.com")),
        "4" to (Pair("BR",     "client.us.freefiremobile.com")),
        "5" to (Pair("SG",     "client.sg.freefiremobile.com")),
        "6" to (Pair("Others", "clientbp.ggpolarbear.com"))
    )

    @Volatile var region: String = "BD"
    @Volatile var targetHost: String = "clientbp.ggpolarbear.com"
    @Volatile var mode: Int = 1

    private val _status = MutableLiveData(Status.STOPPED)
    val status: LiveData<Status> = _status

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    fun set(s: Status, err: String? = null) {
        try {
            _status.postValue(s)
            _error.postValue(err)
        } catch (_: Exception) {}
    }
}
