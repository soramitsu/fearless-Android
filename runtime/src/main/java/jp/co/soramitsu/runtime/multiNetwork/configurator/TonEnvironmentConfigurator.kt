package jp.co.soramitsu.runtime.multiNetwork.configurator

import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

class TonEnvironmentConfigurator() : ChainEnvironmentConfigurator {
    override suspend fun configure(chain: Chain) {
        require(chain.ecosystem == Ecosystem.Ton)

    }
}