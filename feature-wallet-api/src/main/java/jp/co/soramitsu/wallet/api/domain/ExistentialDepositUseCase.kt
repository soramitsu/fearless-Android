package jp.co.soramitsu.wallet.api.domain

import java.math.BigInteger
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

interface ExistentialDepositUseCase {
    suspend operator fun invoke(chainAsset: Chain.Asset): BigInteger
}
