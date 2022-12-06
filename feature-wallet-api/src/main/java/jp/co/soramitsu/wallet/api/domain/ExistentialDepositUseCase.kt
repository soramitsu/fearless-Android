package jp.co.soramitsu.wallet.api.domain

import java.math.BigInteger
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

@Deprecated("DON'T USE IT")
interface ExistentialDepositUseCase {
    suspend operator fun invoke(chainAsset: Chain.Asset): BigInteger
}
