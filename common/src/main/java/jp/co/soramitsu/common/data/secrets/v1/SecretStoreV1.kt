package jp.co.soramitsu.common.data.secrets.v1

import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.model.SigningData
import jp.co.soramitsu.core.model.WithDerivationPath
import jp.co.soramitsu.core.model.WithMnemonic
import jp.co.soramitsu.core.model.WithSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SecretStoreV1 {

    suspend fun saveSecuritySource(accountAddress: String, source: SecuritySource)

    suspend fun getSecuritySource(accountAddress: String): SecuritySource?
}

private const val PREFS_SECURITY_SOURCE_MASK = "security_source_%s"


internal class SecretStoreV1Impl(
    private val encryptedPreferences: EncryptedPreferences
) : SecretStoreV1 {

    override suspend fun saveSecuritySource(accountAddress: String, source: SecuritySource) = withContext(Dispatchers.Default) {
        val key = PREFS_SECURITY_SOURCE_MASK.format(accountAddress)

        val signingData = source.signingData
        val seed = (source as? WithSeed)?.seed
        val mnemonic = (source as? WithMnemonic)?.mnemonic
        val derivationPath = (source as? WithDerivationPath)?.derivationPath

        val toSave = SourceInternal {
            it[Type] = getSourceType(source).name

            it[PrivateKey] = signingData.privateKey
            it[PublicKey] = signingData.publicKey
            it[Nonce] = signingData.nonce

            it[Seed] = seed
            it[Mnemonic] = mnemonic
            it[DerivationPath] = derivationPath
        }

        val raw = SourceInternal.toHexString(toSave)

        encryptedPreferences.putEncryptedString(key, raw)
    }

    override suspend fun getSecuritySource(accountAddress: String): SecuritySource? = withContext(Dispatchers.Default) {
        val key = PREFS_SECURITY_SOURCE_MASK.format(accountAddress)

        val raw = encryptedPreferences.getDecryptedString(key) ?: return@withContext null
        val internalSource = SourceInternal.read(raw)

        val signingData = SigningData(
            publicKey = internalSource[SourceInternal.PublicKey],
            privateKey = internalSource[SourceInternal.PrivateKey],
            nonce = internalSource[SourceInternal.Nonce]
        )

        val seed = internalSource[SourceInternal.Seed]
        val mnemonic = internalSource[SourceInternal.Mnemonic]
        val derivationPath = internalSource[SourceInternal.DerivationPath]

        when (SourceType.valueOf(internalSource[SourceInternal.Type])) {
            SourceType.CREATE -> SecuritySource.Specified.Create(seed, signingData, mnemonic!!, derivationPath)
            SourceType.SEED -> SecuritySource.Specified.Seed(seed, signingData, derivationPath)
            SourceType.JSON -> SecuritySource.Specified.Json(seed, signingData)
            SourceType.MNEMONIC -> SecuritySource.Specified.Mnemonic(seed, signingData, mnemonic!!, derivationPath)
            SourceType.UNSPECIFIED -> SecuritySource.Unspecified(signingData)
        }
    }

    private fun getSourceType(securitySource: SecuritySource): SourceType {
        return when (securitySource) {
            is SecuritySource.Specified.Create -> SourceType.CREATE
            is SecuritySource.Specified.Mnemonic -> SourceType.MNEMONIC
            is SecuritySource.Specified.Json -> SourceType.JSON
            is SecuritySource.Specified.Seed -> SourceType.SEED
            else -> SourceType.UNSPECIFIED
        }
    }
}
