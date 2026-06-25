package jp.co.soramitsu.xnetworking.lib.datasources.chainsconfig.impl.data

import jp.co.soramitsu.xnetworking.lib.datasources.chainsconfig.api.data.ConfigParser
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient

class RemoteConfigParserImpl(
    val restClient: RestClient,
    val chainsRequestUrl: String
) : ConfigParser
