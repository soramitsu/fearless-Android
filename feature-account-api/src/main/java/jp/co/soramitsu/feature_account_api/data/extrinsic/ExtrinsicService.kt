package jp.co.soramitsu.feature_account_api.data.extrinsic

import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v2.getChainAccountKeypair
import jp.co.soramitsu.common.data.secrets.v2.getMetaAccountKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.cryptoTypeIn
import jp.co.soramitsu.feature_account_api.domain.model.hasChainAccountIn
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.network.rpc.RpcCalls
import java.math.BigInteger

class ExtrinsicService(
    private val rpcCalls: RpcCalls,
    private val accountRepository: AccountRepository,
    private val secretStoreV2: SecretStoreV2,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory,
) {

    suspend fun submitExtrinsic(
        chain: Chain,
        accountId: ByteArray,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): Result<String> = runCatching {
        val metaAccount = accountRepository.findMetaAccount(accountId) ?: error("No meta account found accessing ${accountId.toHexString()}")
        val keypair = secretStoreV2.getKeypairFor(chain, metaAccount)

        val extrinsicBuilder = extrinsicBuilderFactory.create(chain, keypair, metaAccount.cryptoTypeIn(chain))

        extrinsicBuilder.formExtrinsic()

        val extrinsic = extrinsicBuilder.build()

        rpcCalls.submitExtrinsic(chain.id, extrinsic)
    }

    suspend fun estimateFee(
        chain: Chain,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): BigInteger {
        val extrinsicBuilder = extrinsicBuilderFactory.create(chain)

        extrinsicBuilder.formExtrinsic()

        val extrinsic = extrinsicBuilder.build()

        return rpcCalls.getExtrinsicFee(chain.id, extrinsic)
    }

    private suspend fun SecretStoreV2.getKeypairFor(chain: Chain, metaAccount: MetaAccount): Keypair {
        return if (metaAccount.hasChainAccountIn(chain.id)) {
            getChainAccountKeypair(metaAccount.id, chain.id)
        } else {
            getMetaAccountKeypair(metaAccount.id, chain.isEthereumBased)
        }
    }
}
