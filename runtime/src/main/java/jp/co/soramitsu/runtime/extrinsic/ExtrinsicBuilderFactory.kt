package jp.co.soramitsu.runtime.extrinsic

import jp.co.soramitsu.common.data.mappers.mapCryptoTypeToEncryption
import jp.co.soramitsu.common.data.mappers.mapSigningDataToKeypair
import jp.co.soramitsu.common.data.network.runtime.calls.SubstrateCalls
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.fearless_utils.encrypt.KeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.multiAddressFromId
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

typealias KeypairProvider = suspend (account: Account) -> Keypair

class ExtrinsicBuilderFactory(
    private val accountRepository: AccountRepository,
    private val substrateCalls: SubstrateCalls,
    private val keypairFactory: KeypairFactory,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>
) {

    fun accountKeypairProvider(): KeypairProvider = { account: Account ->
        val securitySource = accountRepository.getSecuritySource(account.address)
        mapSigningDataToKeypair(securitySource.signingData)
    }

    fun fakeKeypairProvider(): KeypairProvider = {
        generateFakeKeyPair(it)
    }

    suspend fun create(
        accountAddress: String,
        keypairProvider: KeypairProvider = accountKeypairProvider()
    ): ExtrinsicBuilder {
        val account = accountRepository.getAccount(accountAddress)

        val nonce = substrateCalls.getNonce(accountAddress)
        val runtimeVersion = substrateCalls.getRuntimeVersion()

        val runtimeConfiguration = accountAddress.networkType().runtimeConfiguration

        return ExtrinsicBuilder(
            runtime = runtimeProperty.get(),
            keypair = keypairProvider(account),
            nonce = nonce,
            runtimeVersion = runtimeVersion,
            genesisHash = runtimeConfiguration.genesisHash.fromHex(),
            encryptionType = mapCryptoTypeToEncryption(account.cryptoType),
            accountIdentifier = multiAddressFromId(account.address.toAccountId())
        )
    }

    private suspend fun generateFakeKeyPair(account: Account) = withContext(Dispatchers.Default) {
        val cryptoType = mapCryptoTypeToEncryption(account.cryptoType)
        val emptySeed = ByteArray(32) { 1 }

        keypairFactory.generate(cryptoType, emptySeed, "")
    }
}
