package jp.co.soramitsu.core.extrinsic.keypair_provider

import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair

/** New mutations require a provider that checks authority at the physical secret read. */
interface GuardedKeypairProvider : KeypairProvider {
    suspend fun getAuthorizedKeypairFor(chain: IChain, accountId: ByteArray, guard: MutationExecutionGuard, intentSha256: String): Keypair

    /** Presence-only check for local Substrate signing. Never decrypts a wallet secret. */
    suspend fun hasLocalSubstrateKey(chain: IChain, accountId: ByteArray): Boolean
}
