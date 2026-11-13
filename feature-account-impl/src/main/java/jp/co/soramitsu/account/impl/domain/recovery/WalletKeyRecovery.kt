package jp.co.soramitsu.account.impl.domain.recovery

import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.data.repository.WalletKeyIdentityReader
import jp.co.soramitsu.account.impl.data.repository.WalletSecretRebindService
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretKeyring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Keeps expensive derivation off the UI thread; no input is persisted or logged. */
class WalletKeyRecovery @Inject constructor(
    private val identities: WalletKeyIdentityReader,
    private val verifier: WalletIdentityVerifier,
    private val rebindService: WalletSecretRebindService
) {
    suspend fun restore(
        metaId: Long,
        scope: WalletSecretScope,
        mode: ImportMode,
        material: String,
        path: String = "",
        password: String = ""
    ): WalletSecretRebindService.Result {
        val candidate = prove(metaId, scope, mode, material, path, password)
        coroutineContext.ensureActive()
        return rebind(metaId, candidate, recoveredKey = null)
    }

    /** Derives the backup's key with the shipped importers and proves it against the durable identity. */
    suspend fun prove(
        metaId: Long,
        scope: WalletSecretScope,
        mode: ImportMode,
        material: String,
        path: String = "",
        password: String = ""
    ): VerifiedWalletSecret = withContext(Dispatchers.Default) {
        val identity = identities.read(metaId, scope)
        when (mode) {
            ImportMode.MnemonicPhrase -> verifier.verifyMnemonic(identity, material, path)
            ImportMode.RawSeed -> verifier.verifySeed(identity, material, path)
            ImportMode.Json -> verifier.verifyJson(identity, material, password)
            else -> error("This recovery source is not supported")
        }
    }

    /** Repairs the proved key only when needed; [recoveredKey] opens it when the session has no SK. */
    suspend fun rebind(
        metaId: Long,
        candidate: VerifiedWalletSecret,
        recoveredKey: WalletSecretKeyring.OpenedKey?
    ): WalletSecretRebindService.Result = withContext(Dispatchers.Default) {
        rebindService.rebind(metaId, candidate, recoveredKey)
    }
}
