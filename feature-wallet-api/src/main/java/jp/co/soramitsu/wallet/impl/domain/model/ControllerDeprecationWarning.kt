package jp.co.soramitsu.wallet.impl.domain.model

import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

sealed interface ControllerDeprecationWarning {

    val chainId: ChainId

    class ChangeController(override val chainId: ChainId, val chainName: String) : ControllerDeprecationWarning

    class ImportStash(override val chainId: ChainId, val stashAddress: String) : ControllerDeprecationWarning
}
