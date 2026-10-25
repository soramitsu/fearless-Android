package jp.co.soramitsu.account.impl.domain.recovery

import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.data.repository.WalletKeyIdentityReader
import jp.co.soramitsu.account.impl.data.repository.WalletSecretRebindService
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Keeps expensive derivation off the UI thread; no input is persisted or logged. */
class WalletKeyRecovery @Inject constructor(
    private val identities: WalletKeyIdentityReader,
    private val verifier: WalletIdentityVerifier,
    private val rebind: WalletSecretRebindService
) {
    suspend fun restore(
        metaId: Long,
        scope: WalletSecretScope,
        mode: ImportMode,
        material: String,
        path: String = "",
        password: String = ""
    ): WalletSecretRebindService.Result = withContext(Dispatchers.Default) {
        val identity = identities.read(metaId, scope)
        val candidate = when (mode) {
            ImportMode.MnemonicPhrase -> verifier.verifyMnemonic(identity, material, path)
            ImportMode.RawSeed -> verifier.verifySeed(identity, material, path)
            ImportMode.Json -> verifier.verifyJson(identity, material, password)
            else -> error("This recovery source is not supported")
        }
        coroutineContext.ensureActive()
        rebind.rebind(metaId, candidate)
    }
}
