package jp.co.soramitsu.account.impl.data.repository

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import jp.co.soramitsu.common.data.secrets.WalletSecretScalePreflight
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId

/**
 * Android-only plaintext draft. This is deliberately not the cross-platform backup wire format and
 * must not be uploaded until iOS can parse, validate, install and prove every represented key.
 */
internal object PortableWalletMaterialDraft {
    private const val MAGIC = "FPWMDT01"
    private const val VERSION = 2
    private const val MAX_BYTES = 256 * 1024 - 44 // FPBKAEAD v1 plaintext ceiling.
    private const val MAX_WALLETS = 128
    private const val MAX_CHAINS = 128
    private const val MAX_FAVORITES = 128
    private const val MAX_TEXT_BYTES = 2_048
    private const val MAX_SECRET_BYTES = 32 * 1024
    private const val MAX_PUBLIC_ID_BYTES = 128

    internal class Snapshot(val wallets: List<Wallet>) {
        fun clearSecrets() = wallets.forEach(Wallet::clearSecrets)

        override fun toString(): String = "PortableWalletMaterialDraft.Snapshot(redacted)"
    }

    internal class Wallet(
        val identity: WalletIdentity,
        val substrateSecret: ByteArray?,
        val ethereumSecret: ByteArray?,
        val tonSecret: ByteArray?,
        val chainSecrets: List<ByteArray>,
        val legacySubstrateSource: LegacySubstrateSource? = null
    ) {
        fun clearSecrets() {
            substrateSecret?.fill(0)
            ethereumSecret?.fill(0)
            tonSecret?.fill(0)
            chainSecrets.forEach { it.fill(0) }
            legacySubstrateSource?.clearSecrets()
        }

        override fun toString(): String = "PortableWalletMaterialDraft.Wallet(redacted)"
    }

    internal data class WalletIdentity(
        val id: Long,
        val name: String,
        val isSelected: Boolean,
        val position: Int,
        val initialized: Boolean,
        val substratePublicKey: String?,
        val substrateCryptoType: String?,
        val substrateAccountId: String?,
        val ethereumPublicKey: String?,
        val ethereumAddress: String?,
        val tonPublicKey: String?,
        val chainAccounts: List<ChainIdentity>,
        val favoriteChains: List<FavoriteIdentity>
    ) {
        fun hasMaterialIdentity(): Boolean = substratePublicKey != null ||
            ethereumPublicKey != null || tonPublicKey != null || chainAccounts.isNotEmpty()
    }

    internal data class ChainIdentity(
        val chainId: String,
        val publicKey: String,
        val accountId: String,
        val cryptoType: String,
        val name: String,
        val initialized: Boolean
    )

    internal data class FavoriteIdentity(val chainId: String, val isFavorite: Boolean)

    /** A distinct V1 source, retained even when a V3 root also exists. Never substitute it for V3. */
    internal class LegacySubstrateSource(
        val accountAddress: String,
        val sourceType: SourceType,
        val publicKey: ByteArray,
        val privateKey: ByteArray,
        val nonce: ByteArray?,
        val entropy: ByteArray?,
        val seed: ByteArray?,
        val mnemonic: String?,
        val derivationPath: String?
    ) {
        fun clearSecrets() {
            privateKey.fill(0)
            nonce?.fill(0)
            entropy?.fill(0)
            seed?.fill(0)
        }

        override fun toString(): String = "PortableWalletMaterialDraft.LegacySubstrateSource(redacted)"
    }

    internal enum class SourceType { CREATE, SEED, JSON, MNEMONIC, UNSPECIFIED }

    fun encode(snapshot: Snapshot): ByteArray {
        requireValid(snapshot)
        val output = BoundedOutputStream()
        try {
            DataOutputStream(output).use { writer ->
                writer.write(MAGIC.toByteArray(Charsets.US_ASCII))
                writer.writeInt(VERSION)
                writer.writeInt(snapshot.wallets.size)
                snapshot.wallets.forEach { wallet -> writer.writeWallet(wallet) }
            }
            return output.toByteArray()
        } finally {
            output.clear()
        }
    }

    fun decode(encoded: ByteArray): Snapshot {
        require(encoded.size in 17..MAX_BYTES) { "Portable wallet material size is invalid" }
        val reader = DataInputStream(ByteArrayInputStream(encoded))
        val magic = ByteArray(MAGIC.length)
        reader.readFully(magic)
        require(magic.contentEquals(MAGIC.toByteArray(Charsets.US_ASCII))) {
            "Portable wallet material magic is invalid"
        }
        require(reader.readInt() == VERSION) { "Unsupported portable wallet material draft version" }
        val walletCount = reader.readBoundedCount(MAX_WALLETS)
        require(walletCount > 0) { "Portable wallet material has no wallets" }
        val wallets = ArrayList<Wallet>(walletCount)
        try {
            repeat(walletCount) { wallets += reader.readWallet() }
            require(reader.available() == 0) { "Portable wallet material has trailing bytes" }
            return Snapshot(wallets).also(::requireValid)
        } catch (failure: Exception) {
            wallets.forEach(Wallet::clearSecrets)
            throw failure
        }
    }

    private fun requireValid(snapshot: Snapshot) {
        require(snapshot.wallets.size in 1..MAX_WALLETS) { "Portable wallet count is invalid" }
        require(snapshot.wallets.count { it.identity.isSelected } == 1) {
            "Portable wallet selection is inconsistent"
        }
        requireStrictOrder(snapshot.wallets.map { it.identity.id })
        snapshot.wallets.forEach { wallet ->
            val identity = wallet.identity
            require(identity.id > 0 && identity.position >= 0 && identity.hasMaterialIdentity()) {
                "Portable wallet identity is invalid"
            }
            validateText(identity.name)
            require((identity.substratePublicKey == null) == (identity.substrateCryptoType == null) &&
                (identity.substratePublicKey == null) == (identity.substrateAccountId == null)) {
                "Portable Substrate identity is incomplete"
            }
            require((identity.ethereumPublicKey == null) == (identity.ethereumAddress == null)) {
                "Portable Ethereum identity is incomplete"
            }
            require((identity.substratePublicKey == null) ==
                (wallet.substrateSecret == null && wallet.legacySubstrateSource == null) &&
                (identity.ethereumPublicKey == null) == (wallet.ethereumSecret == null) &&
                (identity.tonPublicKey == null) == (wallet.tonSecret == null)) {
                "Portable wallet root material is incomplete"
            }
            identity.substratePublicKey?.let { publicKey ->
                requireCryptoType(identity.substrateCryptoType!!)
                requirePublicId(identity.substrateAccountId!!)
                wallet.substrateSecret?.let { requirePublicKeyMatches(it, publicKey, SecretKind.SUBSTRATE) }
                wallet.legacySubstrateSource?.let { requireLegacySource(it, identity) }
            }
            identity.ethereumPublicKey?.let { publicKey ->
                requirePublicId(identity.ethereumAddress!!)
                requirePublicKeyMatches(wallet.ethereumSecret!!, publicKey, SecretKind.ETHEREUM)
            }
            identity.tonPublicKey?.let { publicKey ->
                requirePublicKeyMatches(wallet.tonSecret!!, publicKey, SecretKind.TON)
            }
            require(identity.chainAccounts.size <= MAX_CHAINS &&
                identity.chainAccounts.size == wallet.chainSecrets.size) {
                "Portable chain-account material count is invalid"
            }
            requireStrictOrder(identity.chainAccounts.map(ChainIdentity::chainId))
            identity.chainAccounts.zip(wallet.chainSecrets).forEach { (chain, secret) ->
                validateText(chain.chainId)
                validateText(chain.name)
                requirePublicId(chain.accountId)
                requireCryptoType(chain.cryptoType)
                requirePublicKeyMatches(secret, chain.publicKey, SecretKind.CHAIN)
            }
            require(identity.favoriteChains.size <= MAX_FAVORITES) {
                "Portable favorite-chain count is invalid"
            }
            requireStrictOrder(identity.favoriteChains.map(FavoriteIdentity::chainId))
            identity.favoriteChains.forEach { validateText(it.chainId) }
        }
    }

    private enum class SecretKind { SUBSTRATE, ETHEREUM, TON, CHAIN }

    private fun requireLegacySource(source: LegacySubstrateSource, identity: WalletIdentity) {
        validateText(source.accountAddress)
        val addressAccountId = try {
            source.accountAddress.toAccountId()
        } catch (_: Exception) {
            throw IllegalArgumentException("Portable V1 account address is invalid")
        }
        require(addressAccountId.contentEquals(requirePublicId(identity.substrateAccountId!!))) {
            "Portable V1 account address mismatch"
        }
        require(source.publicKey.contentEquals(requirePublicId(identity.substratePublicKey!!))) {
            "Portable V1 public identity mismatch"
        }
        require(source.publicKey.size in 1..MAX_PUBLIC_ID_BYTES &&
            source.privateKey.size in 1..MAX_SECRET_BYTES &&
            (source.nonce == null || source.nonce.size in 1..MAX_SECRET_BYTES) &&
            (source.seed == null || source.seed.size in 1..MAX_SECRET_BYTES) &&
            (source.entropy == null || source.entropy.size in 1..MAX_SECRET_BYTES)) {
            "Portable V1 source size is invalid"
        }
        source.mnemonic?.let(::validateText)
        source.derivationPath?.let(::validateText)
        require((source.mnemonic != null) == (source.entropy != null)) {
            "Portable V1 mnemonic entropy is incomplete"
        }
        require(when (source.sourceType) {
            SourceType.CREATE, SourceType.MNEMONIC -> source.mnemonic != null
            SourceType.SEED -> source.mnemonic == null
            SourceType.JSON, SourceType.UNSPECIFIED ->
                source.mnemonic == null && source.derivationPath == null
        }) { "Portable V1 source type is inconsistent" }
        if (source.sourceType == SourceType.UNSPECIFIED) {
            require(source.seed == null) { "Portable V1 unspecified source has unexpected seed" }
        }
        source.mnemonic?.let { words ->
            val derived = try {
                MnemonicCreator.fromWords(words).entropy
            } catch (_: Exception) {
                throw IllegalArgumentException("Portable V1 mnemonic is invalid")
            }
            try {
                require(derived.contentEquals(source.entropy)) {
                    "Portable V1 mnemonic entropy mismatch"
                }
            } finally {
                derived.fill(0)
            }
        }
        val keypair = Keypair(source.publicKey, source.privateKey, source.nonce)
        val securitySource = when (source.sourceType) {
            SourceType.CREATE -> SecuritySource.Specified.Create(
                source.seed, keypair, source.mnemonic!!, source.derivationPath)
            SourceType.SEED -> SecuritySource.Specified.Seed(source.seed, keypair, source.derivationPath)
            SourceType.JSON -> SecuritySource.Specified.Json(source.seed, keypair)
            SourceType.MNEMONIC -> SecuritySource.Specified.Mnemonic(
                source.seed, keypair, source.mnemonic!!, source.derivationPath)
            SourceType.UNSPECIFIED -> SecuritySource.Unspecified(keypair)
        }
        WalletRootSecretValidator.validateLegacySubstrateSource(
            securitySource,
            requirePublicId(identity.substratePublicKey!!),
            CryptoType.valueOf(identity.substrateCryptoType!!),
            requirePublicId(identity.substrateAccountId!!)
        )
    }

    private fun requirePublicKeyMatches(secret: ByteArray, publicKey: String, kind: SecretKind) {
        require(secret.size in 1..MAX_SECRET_BYTES) { "Portable wallet secret size is invalid" }
        val expected = requirePublicId(publicKey)
        val actual = try {
            val encoded = secret.toHexString(withPrefix = true)
            when (kind) {
                SecretKind.SUBSTRATE -> {
                    WalletSecretScalePreflight.requireSubstrateV3(encoded)
                    SubstrateSecrets.read(secret)[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.PublicKey]
                }
                SecretKind.ETHEREUM -> {
                    WalletSecretScalePreflight.requireEthereumV3(encoded)
                    EthereumSecrets.read(secret)[EthereumSecrets.EthereumKeypair][KeyPairSchema.PublicKey]
                }
                SecretKind.TON -> {
                    WalletSecretScalePreflight.requireTonV3(encoded)
                    TonSecrets.read(secret)[TonSecrets.PublicKey]
                }
                SecretKind.CHAIN -> {
                    WalletSecretScalePreflight.requireChainAccountV2(encoded)
                    ChainAccountSecrets.read(secret)[ChainAccountSecrets.Keypair][KeyPairSchema.PublicKey]
                }
            }
        } catch (_: Exception) {
            throw IllegalArgumentException("Portable wallet secret structure is invalid")
        }
        require(actual.contentEquals(expected)) { "Portable wallet secret public identity mismatch" }
    }

    private fun requireCryptoType(name: String) {
        require(CryptoType.entries.any { it.name == name }) { "Portable crypto type is invalid" }
    }

    private fun requirePublicId(value: String): ByteArray {
        validateText(value)
        val decoded = try { Base64.getDecoder().decode(value) } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Portable public identity encoding is invalid")
        }
        require(decoded.size in 1..MAX_PUBLIC_ID_BYTES && Base64.getEncoder().encodeToString(decoded) == value) {
            "Portable public identity encoding is invalid"
        }
        return decoded
    }

    private fun requireText(value: String): ByteArray {
        val bytes = value.toByteArray(Charsets.UTF_8)
        try {
            require(bytes.size <= MAX_TEXT_BYTES && strictUtf8(bytes) == value) {
                "Portable wallet text is invalid"
            }
            return bytes
        } catch (failure: Exception) {
            bytes.fill(0)
            throw failure
        }
    }

    private fun validateText(value: String) {
        requireText(value).fill(0)
    }

    private fun strictUtf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()

    private fun <T : Comparable<T>> requireStrictOrder(values: List<T>) {
        require(values.zipWithNext().all { (left, right) -> left < right }) {
            "Portable wallet entries are unordered or duplicated"
        }
    }

    private fun DataOutputStream.writeWallet(wallet: Wallet) {
        val identity = wallet.identity
        writeLong(identity.id)
        writeText(identity.name)
        writeBoolean(identity.isSelected)
        writeInt(identity.position)
        writeBoolean(identity.initialized)
        writeOptionalText(identity.substratePublicKey)
        writeOptionalText(identity.substrateCryptoType)
        writeOptionalText(identity.substrateAccountId)
        writeOptionalText(identity.ethereumPublicKey)
        writeOptionalText(identity.ethereumAddress)
        writeOptionalText(identity.tonPublicKey)
        writeOptionalBytes(wallet.substrateSecret)
        writeOptionalBytes(wallet.ethereumSecret)
        writeOptionalBytes(wallet.tonSecret)
        writeByte(if (wallet.legacySubstrateSource == null) 0 else 1)
        wallet.legacySubstrateSource?.let { source ->
            writeText(source.accountAddress)
            writeText(source.sourceType.name)
            writeBytes(source.publicKey)
            writeBytes(source.privateKey)
            writeOptionalBytes(source.nonce)
            writeOptionalBytes(source.entropy)
            writeOptionalBytes(source.seed)
            writeOptionalText(source.mnemonic)
            writeOptionalText(source.derivationPath)
        }
        writeInt(identity.chainAccounts.size)
        identity.chainAccounts.zip(wallet.chainSecrets).forEach { (chain, secret) ->
            writeText(chain.chainId)
            writeText(chain.publicKey)
            writeText(chain.accountId)
            writeText(chain.cryptoType)
            writeText(chain.name)
            writeBoolean(chain.initialized)
            writeBytes(secret)
        }
        writeInt(identity.favoriteChains.size)
        identity.favoriteChains.forEach { favorite ->
            writeText(favorite.chainId)
            writeBoolean(favorite.isFavorite)
        }
    }

    private fun DataInputStream.readWallet(): Wallet {
        val id = readLong()
        val name = readText()
        val selected = readBooleanCanonical()
        val position = readInt()
        val initialized = readBooleanCanonical()
        val substratePublicKey = readOptionalText()
        val substrateCryptoType = readOptionalText()
        val substrateAccountId = readOptionalText()
        val ethereumPublicKey = readOptionalText()
        val ethereumAddress = readOptionalText()
        val tonPublicKey = readOptionalText()
        var substrateSecret: ByteArray? = null
        var ethereumSecret: ByteArray? = null
        var tonSecret: ByteArray? = null
        var legacySource: LegacySubstrateSource? = null
        val chains = ArrayList<ChainIdentity>()
        val chainSecrets = ArrayList<ByteArray>()
        try {
            substrateSecret = readOptionalBytes()
            ethereumSecret = readOptionalBytes()
            tonSecret = readOptionalBytes()
            if (readBooleanCanonical()) {
                val address = readText()
                val type = try {
                    SourceType.valueOf(readText())
                } catch (_: Exception) {
                    throw IllegalArgumentException("Portable V1 source type is invalid")
                }
                val publicKey = readBytes()
                val privateKey = readBytes()
                var nonce: ByteArray? = null
                var entropy: ByteArray? = null
                var seed: ByteArray? = null
                try {
                    nonce = readOptionalBytes()
                    entropy = readOptionalBytes()
                    seed = readOptionalBytes()
                    legacySource = LegacySubstrateSource(
                        address, type, publicKey, privateKey, nonce, entropy, seed,
                        readOptionalText(), readOptionalText()
                    )
                } finally {
                    if (legacySource == null) {
                        privateKey.fill(0)
                        nonce?.fill(0)
                        entropy?.fill(0)
                        seed?.fill(0)
                    }
                }
            }
            repeat(readBoundedCount(MAX_CHAINS)) {
                chains += ChainIdentity(readText(), readText(), readText(), readText(), readText(), readBooleanCanonical())
                chainSecrets += readBytes()
            }
            val favorites = ArrayList<FavoriteIdentity>()
            repeat(readBoundedCount(MAX_FAVORITES)) {
                favorites += FavoriteIdentity(readText(), readBooleanCanonical())
            }
            return Wallet(
                WalletIdentity(id, name, selected, position, initialized, substratePublicKey,
                    substrateCryptoType, substrateAccountId, ethereumPublicKey, ethereumAddress,
                    tonPublicKey, chains, favorites),
                substrateSecret, ethereumSecret, tonSecret, chainSecrets, legacySource
            )
        } catch (failure: Exception) {
            substrateSecret?.fill(0)
            ethereumSecret?.fill(0)
            tonSecret?.fill(0)
            legacySource?.clearSecrets()
            chainSecrets.forEach { it.fill(0) }
            throw failure
        }
    }

    private fun DataOutputStream.writeText(value: String) {
        val bytes = requireText(value)
        try {
            writeInt(bytes.size)
            write(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun DataOutputStream.writeOptionalText(value: String?) {
        writeByte(if (value == null) 0 else 1)
        if (value != null) writeText(value)
    }

    private fun DataOutputStream.writeBytes(value: ByteArray) {
        require(value.size in 1..MAX_SECRET_BYTES) { "Portable wallet secret size is invalid" }
        writeInt(value.size)
        write(value)
    }

    private fun DataOutputStream.writeOptionalBytes(value: ByteArray?) {
        writeByte(if (value == null) 0 else 1)
        if (value != null) writeBytes(value)
    }

    private fun DataInputStream.readBoundedCount(max: Int): Int = readInt().also {
        require(it in 0..max) { "Portable wallet entry count is invalid" }
    }

    private fun DataInputStream.readBooleanCanonical(): Boolean = when (readUnsignedByte()) {
        0 -> false
        1 -> true
        else -> throw IllegalArgumentException("Portable wallet boolean is invalid")
    }

    private fun DataInputStream.readText(): String {
        val size = readInt()
        require(size in 0..MAX_TEXT_BYTES && size <= available()) { "Portable wallet text size is invalid" }
        val bytes = ByteArray(size)
        try {
            readFully(bytes)
            return strictUtf8(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun DataInputStream.readOptionalText(): String? = if (readBooleanCanonical()) readText() else null

    private fun DataInputStream.readBytes(): ByteArray {
        val size = readInt()
        require(size in 1..MAX_SECRET_BYTES && size <= available()) { "Portable wallet secret size is invalid" }
        return ByteArray(size).also(::readFully)
    }

    private fun DataInputStream.readOptionalBytes(): ByteArray? = if (readBooleanCanonical()) readBytes() else null

    private class BoundedOutputStream : ByteArrayOutputStream() {
        fun clear() {
            // toByteArray() returns a copy; erase the staging copy on every exit.
            buf.fill(0)
            reset()
        }

        override fun write(value: Int) {
            require(count < MAX_BYTES) { "Portable wallet material exceeds the encrypted-envelope limit" }
            super.write(value)
        }

        override fun write(value: ByteArray, offset: Int, length: Int) {
            require(length <= MAX_BYTES - count) { "Portable wallet material exceeds the encrypted-envelope limit" }
            super.write(value, offset, length)
        }
    }
}
