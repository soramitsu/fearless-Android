package jp.co.soramitsu.runtime.ext

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.IrohaAddressCodec
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

fun Chain.isUniversalWalletIroha(): Boolean {
    return id == UniversalWalletRegistry.taira.chainId ||
        id == UniversalWalletRegistry.nexus.chainId
}

fun Chain.hasNonCanonicalUniversalWalletIrohaIdentity(): Boolean {
    if (isUniversalWalletIroha()) return false

    val isReservedIrohaIdentity = id == UniversalWalletRegistry.taira.id ||
        id == UniversalWalletRegistry.nexus.id ||
        id.equals(UniversalWalletRegistry.taira.chainId, ignoreCase = true) ||
        id.equals(UniversalWalletRegistry.nexus.chainId, ignoreCase = true)

    return isReservedIrohaIdentity ||
        externalApi?.history?.type == Chain.ExternalApi.Section.Type.IROHA
}

fun Chain.universalWalletIrohaNetwork(): UniversalWalletRegistry.IrohaNetwork? {
    return when (id) {
        UniversalWalletRegistry.taira.chainId -> UniversalWalletRegistry.taira
        UniversalWalletRegistry.nexus.chainId -> UniversalWalletRegistry.nexus
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
