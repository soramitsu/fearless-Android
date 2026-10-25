package jp.co.soramitsu.account.impl.domain.recovery

import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryTarget
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.RecoveryAssessment
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryKind
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.model.WalletEcosystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn

/** A failure in one wallet's inventory never hides the other wallets or their actions. */
class WalletRecoveryInventory @Inject constructor(
    private val accounts: AccountRepository,
    private val preferences: EncryptedPreferences
) {
    data class Item(val target: RecoveryTarget, val kind: WalletRecoveryKind)
    data class Advisory(val scope: WalletSecretScope, val asTyped: Boolean, val chainId: String? = null)
    data class Wallet(val id: Long, val name: String, val items: List<Item>, val advisories: List<Advisory>, val readFailed: Boolean)
    data class State(val wallets: List<Wallet>, val archivedCount: Int?, val loading: Boolean = false, val readFailed: Boolean = false)

    fun observe() = combine(accounts.lightMetaAccountsFlow(), preferences.walletSecretQuarantineVersion) { wallets, _ ->
        val rows = wallets.map { wallet ->
            var failed = false
            val items = try {
                accounts.walletRecoveryStatus(wallet.id).items.map { Item(RecoveryTarget.from(wallet.id, it.scope), it.kind) }.distinct()
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                failed = true
                emptyList()
            }
            val advisories = buildList {
                for ((ecosystem, scope) in ROOT_SCOPES) {
                    try {
                        when (accounts.recoveryAssessment(wallet.id, ecosystem)) {
                            is RecoveryAssessment.Unverified -> add(Advisory(scope, false))
                            is RecoveryAssessment.VerifiedAsTyped -> add(Advisory(scope, true))
                            else -> Unit
                        }
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        failed = true
                    }
                }
                for ((chainId, account) in wallet.universalWalletChainAccounts.entries.distinctBy { it.value.accountId.toList() }) {
                    if (account.accountId.size !in WalletSecretScope.CHAIN_ACCOUNT_SECRET_ID_BYTE_LENGTHS) continue
                    val scope = WalletSecretScope.chainAccount(account.accountId)
                    if (!scope.hasSecretNamespace || items.any { it.target.scope() == scope }) continue
                    try {
                        val assessment = SecretStoreV2(preferences).getChainAccountSecretsWithAssessment(
                            wallet.id, account.accountId, account.publicKey, account.cryptoType
                        )?.second
                        if (assessment is RecoveryAssessment.Unverified) add(Advisory(scope, false, chainId))
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        failed = true
                    }
                }
            }
            Wallet(wallet.id, wallet.name, items, advisories, failed)
        }
        val count = try {
            preferences.keysWithPrefixes(setOf(WalletSecretArchive.KEY_PREFIX), 4096, 1024, 1_048_576, failOnOversizedMatch = true).size
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            null
        }
        State(rows, count)
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        emit(State(emptyList(), null, readFailed = true))
    }.flowOn(Dispatchers.IO)

    private companion object {
        val ROOT_SCOPES = listOf(
            WalletEcosystem.Substrate to WalletSecretScope.Substrate,
            WalletEcosystem.Ethereum to WalletSecretScope.Ethereum,
            WalletEcosystem.Ton to WalletSecretScope.Ton
        )
    }
}
