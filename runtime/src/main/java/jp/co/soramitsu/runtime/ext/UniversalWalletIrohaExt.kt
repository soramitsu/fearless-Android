package jp.co.soramitsu.runtime.ext

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.IrohaAddressCodec
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

fun Chain.isUniversalWalletIroha(): Boolean {
    return id in IROHA_CHAIN_IDS || externalApi?.history?.type == Chain.ExternalApi.Section.Type.IROHA
}

fun Chain.universalWalletIrohaNetwork(): UniversalWalletRegistry.IrohaNetwork? {
    return when {
        id in TAIRA_CHAIN_IDS -> UniversalWalletRegistry.taira
        id in NEXUS_CHAIN_IDS -> UniversalWalletRegistry.nexus
        externalApi?.history?.type == Chain.ExternalApi.Section.Type.IROHA && isTestNet -> UniversalWalletRegistry.taira
        else -> null
    }
}

fun Chain.irohaAddressFromPublicKey(publicKey: ByteArray): String? {
    val network = universalWalletIrohaNetwork() ?: return null

    return runCatching {
        IrohaAddressCodec.encode(publicKey.toHexString(withPrefix = false), network.chainDiscriminant)
    }.getOrNull()
}

fun Chain.normalizedIrohaAddress(address: String): String? {
    val network = universalWalletIrohaNetwork() ?: return null

    return runCatching {
        IrohaAddressCodec.parse(address, network.chainDiscriminant).i105
    }.getOrNull()
}

private val TAIRA_CHAIN_IDS = setOf(
    UniversalWalletRegistry.taira.id,
    UniversalWalletRegistry.taira.chainId
)

private val NEXUS_CHAIN_IDS = setOf(
    UniversalWalletRegistry.nexus.id,
    UniversalWalletRegistry.nexus.chainId
)

private val IROHA_CHAIN_IDS = TAIRA_CHAIN_IDS + NEXUS_CHAIN_IDS
