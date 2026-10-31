package jp.co.soramitsu.account.api.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.common.utils.IrohaAddressCodec
import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.common.utils.TonKeyDerivation
import jp.co.soramitsu.common.utils.UniversalWalletPassphrase
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import kotlinx.coroutines.CancellationException

/** A wallet's universal BIP39 root: its words and the passphrase its derivation paths assign. */
class UniversalWalletRoot(val mnemonic: String, val passphrase: String) {
    val hasPassword: Boolean get() = passphrase.isNotEmpty()

    /** The password first, then the empty passphrase every earlier version derived with. */
    val passphraseCandidates: List<String> get() = UniversalWalletPassphrase.candidates(passphrase)

    override fun toString(): String = "UniversalWalletRoot(redacted)"
}

/**
 * The root that the account upgrade and the Bitcoin, Solana and Iroha senders derive from, in
 * their priority: Substrate entropy, then Ethereum entropy, then the stored TON phrase. Seed-only,
 * JSON-only and watch-only wallets have none.
 */
suspend fun AccountRepository.universalWalletRoot(metaId: Long): UniversalWalletRoot? {
    val substrate = getSubstrateSecrets(metaId)
    substrate?.get(SubstrateSecrets.Entropy)?.let { entropy ->
        val passphrase = UniversalWalletPassphrase.substratePassword(substrate[SubstrateSecrets.SubstrateDerivationPath])
            .ifEmpty {
                // Only an Ethereum secret of the same words may lend its password.
                val ethereum = readOptionalSecret { getEthereumSecrets(metaId) }
                if (ethereum?.get(EthereumSecrets.Entropy)?.contentEquals(entropy) == true) {
                    UniversalWalletPassphrase.ethereumPassword(ethereum[EthereumSecrets.EthereumDerivationPath])
                } else {
                    ""
                }
            }
        return UniversalWalletRoot(MnemonicCreator.fromEntropy(entropy.clone()).words, passphrase)
    }
    getEthereumSecrets(metaId)?.let { ethereum ->
        ethereum[EthereumSecrets.Entropy]?.let { entropy ->
            return UniversalWalletRoot(
                MnemonicCreator.fromEntropy(entropy.clone()).words,
                UniversalWalletPassphrase.ethereumPassword(ethereum[EthereumSecrets.EthereumDerivationPath])
            )
        }
    }
    getTonSecrets(metaId)?.get(TonSecrets.Seed)?.decodeToString()?.trim()?.takeIf(String::isNotEmpty)?.let { text ->
        return UniversalWalletRoot(text.split(Regex("\\s+")).joinToString(" "), "")
    }
    return null
}

private inline fun <T> readOptionalSecret(read: () -> T?): T? = try {
    read()
} catch (failure: CancellationException) {
    throw failure
} catch (_: Exception) {
    null
}

/** One universal network of a wallet. Each slot holds one account, with or without the password. */
enum class UniversalWalletSlot(val networkName: String, val chainIds: Set<String>) {
    BITCOIN_MAINNET(UniversalWalletRegistry.bitcoinMainnet.name, UniversalWalletRegistry.bitcoinMainnet.aliases()),
    BITCOIN_TESTNET(UniversalWalletRegistry.bitcoinTestnet.name, UniversalWalletRegistry.bitcoinTestnet.aliases()),
    SOLANA(UniversalWalletRegistry.solanaMainnet.name, UniversalWalletRegistry.solanaMainnet.aliases()),
    TAIRA("Taira", setOf(UniversalWalletRegistry.taira.id, UniversalWalletRegistry.taira.chainId)),

    /** Stored as the wallet's TON root, not as a chain row. */
    TON(
        UniversalWalletRegistry.tonMainnetRegistryEntry.displayName,
        setOf(UniversalWalletRegistry.tonMainnetRegistryEntry.id, UniversalWalletRegistry.tonMainnetRegistryEntry.chainId, "-239", "-3")
    );

    /** The public key the wallet stores for this slot when it derives with [passphrase]. */
    fun publicKey(mnemonic: String, passphrase: String): ByteArray = when (this) {
        BITCOIN_MAINNET, BITCOIN_TESTNET -> BitcoinKeyDerivation.deriveAccount(mnemonic, passphrase, bitcoinNetwork()).let {
            it.privateKey.fill(0)
            it.chainCode.fill(0)
            it.publicKey
        }
        SOLANA -> SolanaKeyDerivation.deriveAccount(mnemonic, passphrase).let {
            it.privateKey.fill(0)
            it.chainCode.fill(0)
            it.publicKey
        }
        TAIRA -> IrohaKeyDerivation.deriveAccount(mnemonic, passphrase).let {
            it.privateKey.fill(0)
            it.chainCode.fill(0)
            it.publicKey
        }
        TON -> TonKeyDerivation.deriveAccount(mnemonic, passphrase).let {
            it.privateKey.fill(0)
            it.chainCode.fill(0)
            it.publicKey
        }
    }

    /** The receive address people see for this slot's [publicKey]. */
    fun address(publicKey: ByteArray): String = when (this) {
        BITCOIN_MAINNET, BITCOIN_TESTNET -> BitcoinKeyDerivation.addressFromPublicKey(publicKey, bitcoinNetwork())
        SOLANA -> SolanaKeyDerivation.addressFromPublicKey(publicKey)
        TAIRA -> IrohaAddressCodec.encode(publicKey.toHex(), UniversalWalletRegistry.taira.chainDiscriminant)
        TON -> TonKeyDerivation.addressFromPublicKey(publicKey)
    }

    fun bitcoinNetwork(): BitcoinKeyDerivation.Network =
        if (this == BITCOIN_TESTNET) BitcoinKeyDerivation.Network.Testnet else BitcoinKeyDerivation.Network.Mainnet

    fun matches(chainId: String): Boolean = chainId.lowercase() in chainIds

    companion object {
        val chainRowSlots = listOf(BITCOIN_MAINNET, BITCOIN_TESTNET, SOLANA, TAIRA)
    }
}

/** The stored chain id and public key of [slot], or null when the wallet has no such account. */
fun MetaAccount.universalWalletSlotAccount(slot: UniversalWalletSlot): Pair<String?, ByteArray>? {
    if (slot == UniversalWalletSlot.TON) {
        // A dedicated TON account shadows the root; it is an independent key, never a slot.
        if (chainAccounts.keys.any(slot::matches)) return null
        return tonPublicKey?.let { null to it }
    }
    return chainAccounts.entries.firstOrNull { slot.matches(it.key) }?.let { it.key to it.value.publicKey }
}

/** Whether the account a slot of a password wallet holds was derived without the password. */
fun UniversalWalletRoot.holdsLegacy(slot: UniversalWalletSlot, storedPublicKey: ByteArray): Boolean =
    hasPassword && slot.publicKey(mnemonic, "").contentEquals(storedPublicKey)

/**
 * Whether one account of a slot holds anything (native coin or any token the app shows).
 * Timeouts and every failure are [Funds.UNKNOWN], which never moves a slot.
 */
fun interface UniversalWalletFundsProbe {
    enum class Funds { FUNDED, EMPTY, UNKNOWN }

    /** What a probe saw; [balance] is the native amount when the network reported one, such as "0.5 SOL". */
    class Reading(val funds: Funds, val balance: String? = null)

    /** [mnemonic] with [passphrase] derives [publicKey] for [slot]. */
    suspend fun probe(slot: UniversalWalletSlot, mnemonic: String, passphrase: String, publicKey: ByteArray): Funds

    /** Like [probe], with the balance when the network reported one. */
    suspend fun read(slot: UniversalWalletSlot, mnemonic: String, passphrase: String, publicKey: ByteArray): Reading =
        Reading(probe(slot, mnemonic, passphrase, publicKey))
}

/** The one notice about slots of a password wallet that still hold their no-password account. */
interface UniversalWalletPasswordNotice {
    /** [IN_USE]: TON Connect apps still use the old TON address. */
    enum class Outcome { SWITCHED, STILL_FUNDED, UNKNOWN, IN_USE }

    class Row(
        val slot: UniversalWalletSlot,
        val oldAddress: String,
        val newAddress: String,
        val outcome: Outcome? = null,
        /** Funds seen at the address this slot does not use, after it changed address. */
        val arrival: Arrival? = null
    ) {
        /** Whether the slot still uses the old address, so Switch applies to it. */
        val switchable: Boolean get() = arrival?.kind != Arrival.Kind.OLD_ADDRESS
    }

    class Arrival(val kind: Kind, val balance: String?) {
        enum class Kind {
            /** The old address received funds after the slot left it; the slot keeps the new one. */
            OLD_ADDRESS,

            /** The old address received funds while the new one was empty, so the slot uses the old one again. */
            OLD_ADDRESS_USED_AGAIN,

            /** The new address received funds while the slot uses the old one again. */
            NEW_ADDRESS
        }
    }

    /** Rows for every old account of [metaId] and every address that received funds the wallet does not show. */
    suspend fun rows(metaId: Long): List<Row>

    /** Replaces each old account that is confirmed empty with its password-protected one. */
    suspend fun switch(metaId: Long): List<Row>

    /**
     * Probes every address a slot of [metaId] left. When only that address holds funds the slot
     * moves back to it; when both do, or the other cannot be read, a row reports the arrival.
     */
    suspend fun refresh(metaId: Long)

    fun isDismissed(metaId: Long): Boolean

    fun dismiss(metaId: Long)

    companion object {
        /** For screens built without the notice, such as previews: never any rows. */
        val NONE: UniversalWalletPasswordNotice = object : UniversalWalletPasswordNotice {
            override suspend fun rows(metaId: Long): List<Row> = emptyList()
            override suspend fun switch(metaId: Long): List<Row> = emptyList()
            override suspend fun refresh(metaId: Long) = Unit
            override fun isDismissed(metaId: Long): Boolean = true
            override fun dismiss(metaId: Long) = Unit
        }
    }
}

private fun UniversalWalletRegistry.BitcoinNetwork.aliases() = setOf(id, chainId)

private fun UniversalWalletRegistry.SolanaNetwork.aliases() = setOf(id, chainId)

private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
