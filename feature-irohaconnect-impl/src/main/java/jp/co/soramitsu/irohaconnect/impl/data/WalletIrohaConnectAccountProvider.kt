package jp.co.soramitsu.irohaconnect.impl.data

import jp.co.soramitsu.account.api.domain.UniversalWalletSlot
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.universalWalletRoot
import jp.co.soramitsu.account.api.domain.universalWalletSlotAccount
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretLockedException
import jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.IrohaAddressCodec
import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectAccount
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectAccountProvider
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectActionError
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectActionException
import jp.co.soramitsu.irohaconnect.impl.domain.IrohaConnectSignature
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectCrypto
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectEncoding
import kotlinx.coroutines.CancellationException

/**
 * The wallets' Taira accounts, and signing with their Iroha keys. A key is re-derived from the wallet's root for
 * every signature (the universal BIP39 root, or a Taira account's own imported entropy) and must reproduce the listed
 * public key and address before anything is signed; the private key is wiped right after.
 */
internal class WalletIrohaConnectAccountProvider(
    private val accountRepository: AccountRepository
) : IrohaConnectAccountProvider {

    override suspend fun accounts(networkPrefix: Int): List<IrohaConnectAccount> {
        if (networkPrefix != UniversalWalletRegistry.taira.chainDiscriminant) return emptyList()
        return accountRepository.allMetaAccounts().mapNotNull { meta ->
            // A stored Taira row wins. Without one (no chain catalog lists Taira yet, so the account upgrade never adds
            // the row) the account is the one the wallet's universal root derives, as in the browser wallet.
            val publicKey = meta.universalWalletSlotAccount(UniversalWalletSlot.TAIRA)?.second ?: rootTairaKey(meta.id)
            if (publicKey == null || publicKey.size != IrohaConnectCrypto.KEY_BYTES) return@mapNotNull null
            val publicKeyHex = IrohaConnectEncoding.hex(publicKey)
            val address = runCatching {
                IrohaAddressCodec.encode(publicKeyHex, networkPrefix)
            }.getOrNull() ?: return@mapNotNull null
            IrohaConnectAccount(metaId = meta.id, address = address, name = meta.name, publicKeyHex = publicKeyHex)
        }.distinctBy { it.address }
    }

    // The Taira key UniversalWalletSlot.TAIRA would add for this wallet, or null without readable root material.
    private suspend fun rootTairaKey(metaId: Long): ByteArray? = try {
        accountRepository.universalWalletRoot(metaId)?.let { root -> UniversalWalletSlot.TAIRA.publicKey(root.mnemonic, root.passphrase) }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    @Suppress("NestedBlockDepth", "LoopWithTooManyJumpStatements") // Try each root until one reproduces the account.
    override suspend fun sign(
        account: IrohaConnectAccount,
        networkPrefix: Int,
        message: ByteArray
    ): IrohaConnectSignature {
        try {
            WalletViewOnlyAccess.requireSigningAccess()
        } catch (error: Exception) {
            throw IrohaConnectActionException(IrohaConnectActionError.WALLET_LOCKED, error)
        }
        val roots = try {
            signingRoots(account)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IrohaConnectActionException) {
            throw error
        } catch (error: WalletSecretLockedException) {
            throw IrohaConnectActionException(IrohaConnectActionError.WALLET_LOCKED, error)
        } catch (error: Exception) {
            throw IrohaConnectActionException(IrohaConnectActionError.CANNOT_SIGN, error)
        }
        for ((mnemonic, passphrase) in roots) {
            val derived = runCatching { IrohaKeyDerivation.deriveAccount(mnemonic, passphrase) }.getOrNull() ?: continue
            try {
                if (derived.publicKeyHex != account.publicKeyHex) continue
                if (IrohaAddressCodec.encode(derived.publicKeyHex, networkPrefix) != account.address) continue
                return IrohaConnectSignature(derived.publicKeyHex, IrohaConnectCrypto.ed25519Sign(derived.privateKey, message))
            } finally {
                derived.privateKey.fill(0)
                derived.chainCode.fill(0)
            }
        }
        throw IrohaConnectActionException(IrohaConnectActionError.KEY_MISMATCH)
    }

    @Suppress("ThrowsCount")
    private suspend fun signingRoots(account: IrohaConnectAccount): List<Pair<String, String>> {
        val meta = accountRepository.getMetaAccount(account.metaId)
        val stored = meta.universalWalletSlotAccount(UniversalWalletSlot.TAIRA)
        if (stored != null && IrohaConnectEncoding.hex(stored.second) != account.publicKeyHex) {
            throw IrohaConnectActionException(IrohaConnectActionError.ACCOUNT_UNAVAILABLE)
        }
        val chainId = stored?.first
        val roots = mutableListOf<Pair<String, String>>()
        chainId?.let {
            accountRepository.getChainAccountSecrets(meta.id, it)?.get(ChainAccountSecrets.Entropy)
        }?.let { entropy ->
            roots += MnemonicCreator.fromEntropy(entropy.clone()).words to ""
        }
        // A password wallet's account derives with its passphrase, or without it for an address an earlier version showed.
        accountRepository.universalWalletRoot(meta.id)?.let { root ->
            root.passphraseCandidates.forEach { roots += root.mnemonic to it }
        }
        if (roots.isEmpty()) throw IrohaConnectActionException(IrohaConnectActionError.CANNOT_SIGN)
        return roots
    }
}
