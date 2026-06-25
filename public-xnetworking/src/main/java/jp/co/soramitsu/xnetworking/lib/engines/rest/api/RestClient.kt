package jp.co.soramitsu.xnetworking.lib.engines.rest.api

import jp.co.soramitsu.xnetworking.lib.engines.rest.api.models.AbstractRestClientConfig

interface RestClient {
    val config: AbstractRestClientConfig
}
