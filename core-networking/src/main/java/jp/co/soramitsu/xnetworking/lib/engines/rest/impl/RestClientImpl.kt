package jp.co.soramitsu.xnetworking.lib.engines.rest.impl

import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.models.AbstractRestClientConfig

class RestClientImpl(
    restClientConfig: AbstractRestClientConfig
) : RestClient {
    override val config: AbstractRestClientConfig = restClientConfig
}
