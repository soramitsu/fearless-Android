package jp.co.soramitsu.runtime.extrinsic

import jp.co.soramitsu.common.data.mappers.mapCryptoTypeToEncryption
import jp.co.soramitsu.common.data.network.runtime.calls.RpcCalls
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.multiAddressFromId
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val FAKE_CRYPTO_TYPE = CryptoType.SR25519

class ExtrinsicBuilderFactory(
    private val accountRepository: AccountRepository,
    private val rpcCalls: RpcCalls,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val mortalityConstructor: MortalityConstructor,
) {

    /**
     * Can be executed with arbitary address
     * Should be primarily used for fee calculation
     */
    suspend fun createWithFakeKeyPair(
        accountAddress: String,
    ) = create(accountAddress, generateFakeKeyPair(), FAKE_CRYPTO_TYPE)

    /**
     * Require account to be present in database and have keypair saved locally
     */
    suspend fun create(
        accountAddress: String,
    ): ExtrinsicBuilder {
        val account = accountRepository.getAccount(accountAddress)

        val securitySource = accountRepository.getSecuritySource(account.address)

        return create(accountAddress, securitySource.keypair, account.cryptoType)
    }

    private suspend fun create(
        accountAddress: String,
        keypair: Keypair,
        cryptoType: CryptoType,
    ): ExtrinsicBuilder {
        val nonce = rpcCalls.getNonce(accountAddress)
        val runtimeVersion = rpcCalls.getRuntimeVersion()
        val mortality = mortalityConstructor.constructMortality()

        val runtimeConfiguration = accountAddress.networkType().runtimeConfiguration

        return ExtrinsicBuilder(
            runtime = runtimeProperty.get(),
            keypair = keypair,
            nonce = nonce,
            runtimeVersion = runtimeVersion,
            genesisHash = runtimeConfiguration.genesisHash.fromHex(),
            blockHash = mortality.blockHash.fromHex(),
            era = mortality.era,
            encryptionType = mapCryptoTypeToEncryption(cryptoType),
            accountIdentifier = multiAddressFromId(accountAddress.toAccountId())
        )
    }

    private suspend fun generateFakeKeyPair() = withContext(Dispatchers.Default) {
        val cryptoType = mapCryptoTypeToEncryption(FAKE_CRYPTO_TYPE)
        val emptySeed = ByteArray(32) { 1 }

        SubstrateKeypairFactory.generate(cryptoType, emptySeed, junctions = emptyList())
    }
}
