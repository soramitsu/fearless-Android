package jp.co.soramitsu.xnetworking.lib.engines.rest.api.models

import kotlinx.serialization.json.Json

abstract class AbstractRestClientConfig {
    abstract fun getConnectTimeoutMillis(): Long
    abstract fun getOrCreateJsonConfig(): Json
    abstract fun getRequestTimeoutMillis(): Long
    abstract fun getSocketTimeoutMillis(): Long
    abstract fun isLoggingEnabled(): Boolean
}
