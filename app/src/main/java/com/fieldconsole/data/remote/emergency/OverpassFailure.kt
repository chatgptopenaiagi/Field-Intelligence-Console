package com.fieldconsole.data.remote.emergency

import com.google.gson.JsonParseException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/** Safe UI errors: never display untrusted server bodies, URLs or credentials. */
class OverpassFailure(message: String) : Exception(message) {
    companion object {
        fun from(error: Exception): OverpassFailure = when (error) {
            is OverpassFailure -> error
            is HttpException -> OverpassFailure(when (error.code()) {
                400 -> "Emergency service query was rejected (HTTP 400)."
                406 -> "Emergency service request was rejected by the server (HTTP 406)."
                429 -> "Emergency service request limit reached. Try again later (HTTP 429)."
                in 500..599 -> "Emergency service server is unavailable (HTTP ${error.code()})."
                else -> "Emergency service request failed (HTTP ${error.code()})."
            })
            is SocketTimeoutException -> OverpassFailure("Emergency service request timed out. Try again.")
            is JsonParseException -> OverpassFailure("Emergency service response could not be read.")
            is IOException -> OverpassFailure("Cannot reach emergency services. Check your connection.")
            else -> OverpassFailure("Emergency services could not be loaded.")
        }
    }
}
