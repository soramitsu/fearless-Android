package jp.co.soramitsu.runtime.multiNetwork.configurator

import jp.co.soramitsu.runtime.ext.isUniversalWalletBitcoin
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

/** Native balance and transfer services own their HTTP clients; no runtime is needed. */
class NativeWalletEnvironmentConfigurator : ChainEnvironmentConfigurator {
    override suspend fun configure(chain: Chain) {
        require(chain.isUniversalWalletBitcoin() || chain.isUniversalWalletSolana())
    }
}
