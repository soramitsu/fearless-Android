package jp.co.soramitsu.walletconnect.impl.presentation

import com.reown.walletkit.client.Wallet
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.isUniversalWalletBitcoin
import jp.co.soramitsu.runtime.ext.isUniversalWalletIroha
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

/**
 * The pending request with exactly [requestId]. Null when it was answered, expired or the id is
 * ambiguous: a screen bound to one request never falls back to another request of the session.
 */
internal fun List<Wallet.Model.SessionRequest>.pendingRequest(requestId: Long): Wallet.Model.SessionRequest? =
    singleOrNull { it.request.id == requestId }

/** Whether this request still asks for exactly what [displayed] showed the user. */
internal fun Wallet.Model.SessionRequest.isSameRequestAs(displayed: Wallet.Model.SessionRequest): Boolean =
    request.id == displayed.request.id && signedContent() == displayed.signedContent()

private fun Wallet.Model.SessionRequest.signedContent() = listOf(topic, chainId, request.method, request.params)

/**
 * Addresses the user approved for [caip2ChainId] in this session, taken from the CAIP-10
 * `namespace:reference:address` accounts that Reown stores for it.
 */
internal fun Wallet.Model.Session.approvedAddresses(caip2ChainId: String): List<String> {
    val chainPrefix = "$caip2ChainId:"
    return namespaces.values.flatMap { it.accounts }
        .filter { it.startsWith(chainPrefix) }
        .map { it.removePrefix(chainPrefix) }
}

/**
 * Whether the session approved [accountId] on [caip2ChainId]. Addresses are compared as account ids,
 * so EVM hex case and the SS58 format a dApp chose make no difference.
 */
internal fun Wallet.Model.Session.approvesAccount(
    caip2ChainId: String,
    accountId: ByteArray,
    accountIdOf: (String) -> ByteArray
): Boolean = approvedAddresses(caip2ChainId).any { address ->
    runCatching { accountIdOf(address) }.getOrNull()?.contentEquals(accountId) == true
}

/** The wallet whose key signs for [address] on [chain]: matched by account id, as the signer is. */
internal fun List<MetaAccount>.walletFor(chain: Chain, address: String?): MetaAccount? {
    val accountId = address?.let { runCatching { chain.accountIdOf(it) }.getOrNull() } ?: return null
    return firstOrNull { it.accountId(chain)?.contentEquals(accountId) == true }
}

/**
 * Whether [chain] is a network this method may sign for: EVM methods only on eip155 networks,
 * Polkadot messages on Substrate and Ethereum-based parachains, Polkadot transactions only on
 * Substrate ones. On Ethereum-based parachains a dApp-built payload would be signed raw with the
 * EVM key the wallet also uses on Ethereum. Nothing is signed for Bitcoin, Solana or Iroha keys.
 */
internal fun WalletConnectMethod.isAvailableOn(chain: Chain): Boolean {
    val isUniversalWalletChain = chain.isUniversalWalletBitcoin() ||
        chain.isUniversalWalletSolana() ||
        chain.isUniversalWalletIroha()
    val isPolkadotNetwork = !chain.isEthereumChain && !isUniversalWalletChain
    return when (this) {
        WalletConnectMethod.PolkadotSignMessage ->
            isPolkadotNetwork && (chain.ecosystem == Ecosystem.Substrate || chain.ecosystem == Ecosystem.EthereumBased)
        WalletConnectMethod.PolkadotSignTransaction -> isPolkadotNetwork && chain.ecosystem == Ecosystem.Substrate
        WalletConnectMethod.EthereumSignTransaction,
        WalletConnectMethod.EthereumSendTransaction,
        WalletConnectMethod.EthereumPersonalSign,
        WalletConnectMethod.EthereumSignTypedData,
        WalletConnectMethod.EthereumSign,
        WalletConnectMethod.EthereumSignTypedDataV4 -> chain.isEthereumChain
    }
}
