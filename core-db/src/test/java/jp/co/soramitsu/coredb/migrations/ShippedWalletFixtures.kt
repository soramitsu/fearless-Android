package jp.co.soramitsu.coredb.migrations

import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.migrations.shipped.ShippedSecretWriters

/**
 * Wallets as shipped builds stored them, for the JVM tests of the legacy
 * conversion, the orphan inventory and the startup revalidation. ED25519 and
 * ECDSA run on the host; SR25519 is covered by the device tests.
 */
internal object ShippedWalletFixtures {

    const val SEED_HEX = "0x9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"
    const val OTHER_SEED_HEX = "0x4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb"
    const val ETH_SEED_HEX = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"

    /** 3.7.4 raw-seed import with the Ethereum step declined (AND-V2). */
    fun rawSeedDeclinedEthereum(seed: String = SEED_HEX) =
        ShippedSecretWriters.importFromSeed374(
            seed = seed,
            derivationPath = "",
            selectedEncryptionType = CryptoType.ED25519,
            ethSeed = null
        )

    /** 3.7.4 raw-seed import with an Ethereum seed supplied. */
    fun rawSeedWithEthereum(seed: String = SEED_HEX) =
        ShippedSecretWriters.importFromSeed374(
            seed = seed,
            derivationPath = "",
            selectedEncryptionType = CryptoType.ED25519,
            ethSeed = ETH_SEED_HEX
        )

    fun identity(
        metaId: Long,
        wallet: ShippedSecretWriters.LegacyWallet,
        ethereumPublicKey: ByteArray? = wallet.ethereumPublicKey,
        ethereumAddress: ByteArray? = wallet.ethereumAddress
    ) = WalletPublicIdentity(
        metaId = metaId,
        substratePublicKey = wallet.substratePublicKey,
        substrateCryptoType = wallet.substrateCryptoType,
        substrateCryptoTypeWasPresent = true,
        substrateAccountId = wallet.substrateAccountId,
        ethereumPublicKey = ethereumPublicKey,
        ethereumAddress = ethereumAddress,
        tonPublicKey = null
    )

    fun metaRow(identity: WalletPublicIdentity): Map<String, Any?> = mapOf(
        "id" to identity.metaId,
        "substratePublicKey" to identity.substratePublicKey,
        "substrateCryptoType" to identity.substrateCryptoType?.name,
        "substrateAccountId" to identity.substrateAccountId,
        "ethereumPublicKey" to identity.ethereumPublicKey,
        "ethereumAddress" to identity.ethereumAddress,
        "tonPublicKey" to identity.tonPublicKey
    )

    fun database(
        vararg identities: WalletPublicIdentity,
        chainAccounts: List<Map<String, Any?>> = emptyList(),
        tonConnections: List<Map<String, Any?>> = emptyList()
    ) = FakeWalletDatabase(
        mapOf(
            "meta_accounts" to identities.map(::metaRow),
            "chain_accounts" to chainAccounts,
            "ton_connection" to tonConnections
        )
    ).database
}
