package jp.co.soramitsu.account.impl.presentation

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Shared fixtures for the presentation view-model tests of lane D (d-vmviews). */
object VmTestFixtures {

    fun meta(
        id: Long = 7,
        name: String = "Main wallet",
        chainAccounts: Map<String, MetaAccount.ChainAccount> = emptyMap(),
        substratePublicKey: ByteArray? = ByteArray(32) { 1 },
        substrateAccountId: ByteArray? = ByteArray(32) { 1 },
        ethereumAddress: ByteArray? = ByteArray(20) { 2 },
        ethereumPublicKey: ByteArray? = ByteArray(33) { 3 },
        tonPublicKey: ByteArray? = null,
        isBackedUp: Boolean = true
    ) = MetaAccount(
        id = id,
        chainAccounts = chainAccounts,
        favoriteChains = emptyMap(),
        substratePublicKey = substratePublicKey,
        substrateCryptoType = CryptoType.SR25519,
        substrateAccountId = substrateAccountId,
        ethereumAddress = ethereumAddress,
        ethereumPublicKey = ethereumPublicKey,
        tonPublicKey = tonPublicKey,
        isSelected = true,
        isBackedUp = isBackedUp,
        googleBackupAddress = null,
        name = name,
        initialized = true
    )

    fun chain(
        id: String = "chain-id",
        name: String = id,
        isEthereumBased: Boolean = false,
        isEthereumChain: Boolean = false,
        isTestNet: Boolean = false,
        ecosystem: Ecosystem = if (isEthereumBased) Ecosystem.Ethereum else Ecosystem.Substrate
    ) = Chain(
        id = id,
        paraId = null,
        rank = null,
        name = name,
        minSupportedVersion = null,
        assets = emptyList(),
        nodes = emptyList(),
        explorers = emptyList(),
        externalApi = null,
        icon = "",
        addressPrefix = 0,
        isEthereumBased = isEthereumBased,
        isTestNet = isTestNet,
        hasCrowdloans = false,
        parentId = null,
        supportStakingPool = false,
        isEthereumChain = isEthereumChain,
        chainlinkProvider = false,
        supportNft = false,
        isUsesAppId = false,
        identityChain = null,
        ecosystem = ecosystem,
        remoteAssetsSource = null
    )

    /** Strings come back as "s<id>" (with arguments "s<id>:a,b"), plurals as "q<id>:<quantity>". */
    fun resourceManager(): ResourceManager = mockk(relaxed = true) {
        every { getString(any()) } answers { "s${firstArg<Int>()}" }
        every { getString(any(), *anyVararg()) } answers {
            "s${firstArg<Int>()}:" + args.drop(1).joinToString(",") { if (it is Array<*>) it.joinToString(",") else it.toString() }
        }
        every { getQuantityString(any(), any()) } answers { "q${firstArg<Int>()}:${secondArg<Int>()}" }
        every { getQuantityString(any(), any(), *anyVararg()) } answers { "q${firstArg<Int>()}:${secondArg<Int>()}" }
    }

    /** Waits in real time (flows moved to Dispatchers.Default do not follow virtual time). */
    suspend fun awaitReal(condition: () -> Boolean) {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (!condition()) delay(10) }
        }
    }
}
