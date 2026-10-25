package jp.co.soramitsu.core.extrinsic.keypair_provider

import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair

interface KeypairProvider {
    suspend fun getCryptoTypeFor(chain: IChain, accountId: ByteArray): CryptoType

    suspend fun getKeypairFor(chain: IChain, accountId: ByteArray): Keypair
}

class SingleKeypairProvider(
    private val keypair: Keypair,
    private val cryptoType: CryptoType
) : KeypairProvider {
    override suspend fun getCryptoTypeFor(chain: IChain, accountId: ByteArray): CryptoType = cryptoType

    override suspend fun getKeypairFor(chain: IChain, accountId: ByteArray): Keypair = keypair
}
