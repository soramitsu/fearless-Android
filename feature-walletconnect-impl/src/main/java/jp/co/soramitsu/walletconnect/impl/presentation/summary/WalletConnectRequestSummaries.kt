package jp.co.soramitsu.walletconnect.impl.presentation.summary

import com.reown.walletkit.client.Wallet
import jp.co.soramitsu.common.compose.component.SigningSummaryViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.walletconnect.impl.presentation.chainRegistryRuntimeSource
import javax.inject.Inject

/** What a WalletConnect request asks the user to sign, for the approval screens. */
class WalletConnectRequestSummaries internal constructor(
    private val decoder: WalletConnectRequestDecoder,
    private val presenter: WalletConnectSummaryPresenter
) {

    @Inject
    constructor(chainRegistry: ChainRegistry, resourceManager: ResourceManager) : this(
        // The signer reads the same runtime. A chain that is not connected has none yet: its calls are then
        // shown as raw data and signing waits until the runtime is loaded.
        WalletConnectRequestDecoder(chainsOf = {
            chainRegistry.getChains()
        }, runtimeOf = chainRegistryRuntimeSource(chainRegistry)),
        WalletConnectSummaryPresenter(resourceManager)
    )

    suspend fun summary(chain: Chain, request: Wallet.Model.SessionRequest): SigningSummaryViewState =
        presenter.present(chain, decoder.summary(chain, request))
}
