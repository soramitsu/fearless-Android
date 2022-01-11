package jp.co.soramitsu.runtime.extrinsic

import jp.co.soramitsu.common.data.mappers.mapCryptoTypeToEncryption
import jp.co.soramitsu.common.data.network.runtime.binding.bindMultiAddress
import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.runtime.ext.addressFromPublicKey
import jp.co.soramitsu.runtime.ext.genesisHash
import jp.co.soramitsu.runtime.ext.multiAddressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import jp.co.soramitsu.runtime.network.rpc.RpcCalls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val FAKE_CRYPTO_TYPE = CryptoType.SR25519

class ExtrinsicBuilderFactory(
    private val rpcCalls: RpcCalls,
    private val chainRegistry: ChainRegistry,
    private val mortalityConstructor: MortalityConstructor,
) {

    /**
     * Create with fake keypair
     * Should be primarily used for fee calculation
     */
    suspend fun create(
        chain: Chain,
    ) = create(chain, generateFakeKeyPair(), FAKE_CRYPTO_TYPE)

    /**
     * Create with real keypair
     */
    suspend fun create(
        chain: Chain,
        keypair: Keypair,
        cryptoType: CryptoType,
    ): ExtrinsicBuilder {
        val accountAddress = chain.addressFromPublicKey(keypair.publicKey)

        val nonce = rpcCalls.getNonce(chain.id, accountAddress)
        val runtimeVersion = rpcCalls.getRuntimeVersion(chain.id)

        // todo (Denis Lyazgin 22.12.2021) try to figure out why we can't get Babe.ExpectedBlockTime from some chains
        // todo and what aftermath this fix may cause
        val mortality = try {
            mortalityConstructor.constructMortality(chain.id)
        } catch (e: Exception) {
            null
        }

        val runtime = chainRegistry.getRuntime(chain.id)
        val genesisHash = chain.genesisHash.fromHex()
        val blockHash = mortality?.blockHash?.fromHex()
        val multiChainEncryption = MultiChainEncryption.Substrate(mapCryptoTypeToEncryption(cryptoType))
        val accountIdentifier = bindMultiAddress(chain.multiAddressOf(accountAddress))

        return ExtrinsicBuilder(
            runtime = runtime,
            keypair = keypair,
            nonce = nonce,
            runtimeVersion = runtimeVersion,
            genesisHash = genesisHash,
            blockHash = blockHash ?: genesisHash,
            era = mortality?.era ?: Era.Immortal,
            multiChainEncryption = multiChainEncryption,
            accountIdentifier = accountIdentifier
        )
    }

    private suspend fun generateFakeKeyPair() = withContext(Dispatchers.Default) {
        val cryptoType = mapCryptoTypeToEncryption(FAKE_CRYPTO_TYPE)
        val emptySeed = ByteArray(32) { 1 }

        SubstrateKeypairFactory.generate(cryptoType, emptySeed, junctions = emptyList())
    }
}
