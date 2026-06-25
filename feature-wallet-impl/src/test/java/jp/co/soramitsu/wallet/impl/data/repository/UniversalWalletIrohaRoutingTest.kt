package jp.co.soramitsu.wallet.impl.data.repository

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.domain.model.chainAddress
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.ext.normalizedIrohaAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UniversalWalletIrohaRoutingTest {

    @Test
    fun `meta account derives taira address from chain account public key`() {
        val chain = irohaChain(UniversalWalletRegistry.taira)
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val expected = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105
        val metaAccount = metaAccount(chain, publicKey = account.publicKey, accountId = account.publicKey)

        assertEquals(expected, metaAccount.address(chain))
        assertEquals(expected, metaAccount.chainAddress(chain))
    }

    @Test
    fun `meta account derives nexus address from the same public key with nexus discriminant`() {
        val chain = irohaChain(UniversalWalletRegistry.nexus)
        val account = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val expected = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.nexus.chainDiscriminant
        ).i105
        val metaAccount = metaAccount(chain, publicKey = account.publicKey, accountId = account.publicKey)

        assertEquals(expected, metaAccount.address(chain))
        assertEquals(expected, metaAccount.chainAddress(chain))
    }

    @Test
    fun `iroha address resolution does not fall back to substrate account id`() {
        val chain = irohaChain(UniversalWalletRegistry.taira)
        val metaAccount = metaAccount(
            chainAccounts = emptyMap(),
            substrateAccountId = ByteArray(32)
        )

        assertNull(metaAccount.address(chain))
        assertNull(metaAccount.chainAddress(chain))
    }

    @Test
    fun `iroha address normalization rejects wrong network discriminant`() {
        val taira = irohaChain(UniversalWalletRegistry.taira)
        val nexusAddress = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.nexus.chainDiscriminant
        ).i105

        assertNull(taira.normalizedIrohaAddress(nexusAddress))
    }

    private companion object {
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        fun metaAccount(
            chain: Chain,
            publicKey: ByteArray,
            accountId: ByteArray
        ): MetaAccount {
            return metaAccount(
                chainAccounts = mapOf(
                    chain.id to MetaAccount.ChainAccount(
                        metaId = 1,
                        chain = chain,
                        publicKey = publicKey,
                        accountId = accountId,
                        cryptoType = CryptoType.ED25519,
                        accountName = "Iroha"
                    )
                )
            )
        }

        fun metaAccount(
            chainAccounts: Map<String, MetaAccount.ChainAccount>,
            substrateAccountId: ByteArray? = null
        ): MetaAccount {
            return MetaAccount(
                id = 1,
                chainAccounts = chainAccounts,
                favoriteChains = emptyMap(),
                substratePublicKey = substrateAccountId,
                substrateCryptoType = CryptoType.SR25519,
                substrateAccountId = substrateAccountId,
                ethereumAddress = null,
                ethereumPublicKey = null,
                tonPublicKey = null,
                isSelected = true,
                isBackedUp = true,
                googleBackupAddress = null,
                name = "Wallet",
                initialized = true
            )
        }

        fun irohaChain(network: UniversalWalletRegistry.IrohaNetwork): Chain {
            return Chain(
                id = network.id,
                paraId = null,
                rank = null,
                name = network.id,
                minSupportedVersion = null,
                assets = listOf(irohaAsset(network.id)),
                nodes = emptyList(),
                explorers = emptyList(),
                externalApi = Chain.ExternalApi(
                    staking = null,
                    history = Chain.ExternalApi.Section(
                        Chain.ExternalApi.Section.Type.IROHA,
                        network.toriiBaseUrl ?: "https://nexus.example.org"
                    ),
                    crowdloans = null
                ),
                icon = "",
                addressPrefix = 0,
                isEthereumBased = false,
                isTestNet = network == UniversalWalletRegistry.taira,
                hasCrowdloans = false,
                parentId = null,
                supportStakingPool = false,
                isEthereumChain = false,
                chainlinkProvider = false,
                supportNft = false,
                isUsesAppId = false,
                identityChain = null,
                ecosystem = Ecosystem.Substrate,
                remoteAssetsSource = null
            )
        }

        fun irohaAsset(chainId: String): Asset {
            return Asset(
                id = "xor#sora",
                name = "XOR",
                symbol = "XOR",
                iconUrl = "",
                chainId = chainId,
                chainName = "Iroha",
                chainIcon = null,
                isTestNet = false,
                priceId = null,
                precision = 18,
                staking = Asset.StakingType.UNSUPPORTED,
                purchaseProviders = null,
                supportStakingPool = false,
                isUtility = true,
                type = ChainAssetType.Normal,
                currencyId = null,
                existentialDeposit = null,
                color = null,
                isNative = true
            )
        }
    }
}
