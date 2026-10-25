package jp.co.soramitsu.core.network

import kotlinx.serialization.json.Json

object JsonFactory {
    fun create(): Json {
        return Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
