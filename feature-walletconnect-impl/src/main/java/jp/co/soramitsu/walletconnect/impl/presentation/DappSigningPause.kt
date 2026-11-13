package jp.co.soramitsu.walletconnect.impl.presentation

import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore

/**
 * A remote kill switch paused dApp signing for the request's family: nothing is signed or
 * broadcast for it and the dApp gets an error response (docs/remote-feature-switches.md).
 */
internal class DappSigningPausedException : WalletConnectRequestRejectedException("dApp signing is paused")

/**
 * Whether a remote kill switch pauses [method]: `polkadot_*` requests follow
 * `polkadot_dapp_signing_enabled`, EVM requests `evm_dapp_signing_enabled`. A method the wallet
 * does not support is refused anyway.
 */
internal fun ProductFeatureToggleStore.pausesDappSigning(method: String): Boolean =
    when (WalletConnectMethod.values().firstOrNull { it.method == method }) {
        null -> false
        WalletConnectMethod.PolkadotSignTransaction,
        WalletConnectMethod.PolkadotSignMessage -> !polkadotDappSigningEnabled
        WalletConnectMethod.EthereumSignTransaction,
        WalletConnectMethod.EthereumSendTransaction,
        WalletConnectMethod.EthereumPersonalSign,
        WalletConnectMethod.EthereumSignTypedData,
        WalletConnectMethod.EthereumSign,
        WalletConnectMethod.EthereumSignTypedDataV4 -> !evmDappSigningEnabled
    }
