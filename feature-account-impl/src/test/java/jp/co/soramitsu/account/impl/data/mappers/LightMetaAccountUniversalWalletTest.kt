package jp.co.soramitsu.account.impl.data.mappers

import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.domain.model.chainAddress
import jp.co.soramitsu.account.api.domain.model.cryptoType
import jp.co.soramitsu.account.api.domain.model.supportedEcosystemWithIconAddress
import jp.co.soramitsu.account.api.domain.model.supportedEcosystems
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.RelationJoinedMetaAccountInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LightMetaAccountUniversalWalletTest {

    @Test
    fun `joined light account keeps universal wallet chain account ecosystems and icon addresses`() {
        val bitcoin = BitcoinKeyDerivation.deriveAccount(
            mnemonic = MNEMONIC,
            network = BitcoinKeyDerivation.Network.Mainnet
        )
        val solana = SolanaKeyDerivation.deriveAccount(MNEMONIC)
        val iroha = IrohaKeyDerivation.deriveAccount(MNEMONIC)

        val lightAccount = mapJoinedMetaAccountInfoToLightMetaAccount(
            RelationJoinedMetaAccountInfo(
                metaAccount = metaAccountLocal(),
                chainAccounts = listOf(
                    chainAccount(UniversalWalletRegistry.bitcoinMainnet.id, bitcoin.publicKey),
                    chainAccount(UniversalWalletRegistry.solanaMainnet.id, solana.publicKey),
                    chainAccount(UniversalWalletRegistry.taira.id, iroha.publicKey)
                ),
                favoriteChains = emptyList()
            )
        )

        assertEquals(
            setOf(WalletEcosystem.Bitcoin, WalletEcosystem.Solana, WalletEcosystem.Iroha),
            lightAccount.supportedEcosystems()
        )

        assertEquals(
            BitcoinKeyDerivation.addressFromPublicKey(bitcoin.publicKey, BitcoinKeyDerivation.Network.Mainnet),
            lightAccount.supportedEcosystemWithIconAddress()[WalletEcosystem.Bitcoin]
        )
        assertEquals(
            SolanaKeyDerivation.addressFromPublicKey(solana.publicKey),
            lightAccount.supportedEcosystemWithIconAddress()[WalletEcosystem.Solana]
        )
        assertEquals(
            IrohaKeyDerivation.deriveAddress(
                mnemonic = MNEMONIC,
                chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
            ).i105,
            lightAccount.supportedEcosystemWithIconAddress()[WalletEcosystem.Iroha]
        )
    }

    @Test
    fun `malformed universal wallet public keys do not produce icon addresses`() {
        val lightAccount = LightMetaAccount(
            id = 1,
            substratePublicKey = null,
            substrateCryptoType = null,
            substrateAccountId = null,
            ethereumAddress = null,
            ethereumPublicKey = null,
            tonPublicKey = null,
            universalWalletChainAccounts = mapOf(
                UniversalWalletRegistry.bitcoinMainnet.id to LightMetaAccount.UniversalWalletChainAccount(
                    publicKey = ByteArray(32),
                    accountId = ByteArray(32),
                    cryptoType = CryptoType.ED25519
                )
            ),
            isSelected = true,
            name = "Wallet",
            isBackedUp = true,
            initialized = true
        )

        assertEquals(setOf(WalletEcosystem.Bitcoin), lightAccount.supportedEcosystems())
        assertFalse(lightAccount.supportedEcosystemWithIconAddress().containsKey(WalletEcosystem.Bitcoin))
    }

    @Test
    fun `account address lookups match canonical universal wallet accounts from registry chain ids`() {
        val solana = SolanaKeyDerivation.deriveAccount(MNEMONIC)
        val iroha = IrohaKeyDerivation.deriveAccount(MNEMONIC)
        val lightAccount = LightMetaAccount(
            id = 1,
            substratePublicKey = null,
            substrateCryptoType = null,
            substrateAccountId = null,
            ethereumAddress = null,
            ethereumPublicKey = null,
            tonPublicKey = null,
            universalWalletChainAccounts = mapOf(
                UniversalWalletRegistry.solanaMainnet.chainId to LightMetaAccount.UniversalWalletChainAccount(
                    publicKey = solana.publicKey,
                    accountId = solana.publicKey,
                    cryptoType = CryptoType.ED25519
                )
            ),
            isSelected = true,
            name = "Wallet",
            isBackedUp = true,
            initialized = true
        )
        val metaAccount = metaAccount(
            chainAccounts = mapOf(
                UniversalWalletRegistry.nexus.chainId to MetaAccount.ChainAccount(
                    metaId = 1,
                    chain = null,
                    publicKey = iroha.publicKey,
                    accountId = iroha.publicKey,
                    cryptoType = CryptoType.ED25519,
                    accountName = "Nexus"
                )
            )
        )
        val solanaRegistryChain = universalWalletChain(UniversalWalletRegistry.solanaMainnet.id)
        val nexusRegistryChain = universalWalletChain(UniversalWalletRegistry.nexus.id)
        val nexusAddress = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.nexus.chainDiscriminant
        ).i105

        assertEquals(solana.address, lightAccount.address(solanaRegistryChain))
        assertEquals(nexusAddress, metaAccount.address(nexusRegistryChain))
        assertEquals(nexusAddress, metaAccount.chainAddress(nexusRegistryChain))
        assertArrayEquals(iroha.publicKey, metaAccount.accountId(nexusRegistryChain))
        assertEquals(CryptoType.ED25519, metaAccount.cryptoType(nexusRegistryChain))
    }

    @Test
    fun `missing universal wallet chain accounts do not fall back to substrate account id`() {
        val metaAccount = metaAccount(
            chainAccounts = emptyMap(),
            substratePublicKey = ByteArray(32) { 1 },
            substrateAccountId = ByteArray(32) { 2 },
            substrateCryptoType = CryptoType.ED25519
        )
        val solanaRegistryChain = universalWalletChain(UniversalWalletRegistry.solanaMainnet.id)

        assertNull(metaAccount.address(solanaRegistryChain))
        assertNull(metaAccount.chainAddress(solanaRegistryChain))
        assertNull(metaAccount.accountId(solanaRegistryChain))
        assertNull(metaAccount.cryptoType(solanaRegistryChain))
    }

    private fun metaAccountLocal(): MetaAccountLocal {
        return MetaAccountLocal(
            substratePublicKey = null,
            substrateCryptoType = null,
            substrateAccountId = null,
            ethereumPublicKey = null,
            ethereumAddress = null,
            tonPublicKey = null,
            name = "Wallet",
            isSelected = true,
            position = 0,
            isBackedUp = true,
            googleBackupAddress = null,
            initialized = true
        ).also {
            it.id = 1
        }
    }

    private fun chainAccount(chainId: String, publicKey: ByteArray): ChainAccountLocal {
        return ChainAccountLocal(
            metaId = 1,
            chainId = chainId,
            publicKey = publicKey,
            accountId = publicKey,
            cryptoType = CryptoType.ED25519,
            name = chainId,
            initialized = true
        )
    }

    private fun metaAccount(
        chainAccounts: Map<String, MetaAccount.ChainAccount>,
        substratePublicKey: ByteArray? = null,
        substrateAccountId: ByteArray? = null,
        substrateCryptoType: CryptoType? = null
    ): MetaAccount {
        return MetaAccount(
            id = 1,
            chainAccounts = chainAccounts,
            favoriteChains = emptyMap(),
            substratePublicKey = substratePublicKey,
            substrateCryptoType = substrateCryptoType,
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

    private fun universalWalletChain(id: String, isTestNet: Boolean = false): Chain {
        return Chain(
            id = id,
            paraId = null,
            rank = null,
            name = id,
            minSupportedVersion = null,
            assets = emptyList(),
            nodes = emptyList(),
            explorers = emptyList(),
            externalApi = null,
            icon = "",
            addressPrefix = 0,
            isEthereumBased = false,
            isTestNet = isTestNet,
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

    private companion object {
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    }
}
