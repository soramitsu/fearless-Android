package jp.co.soramitsu.feature_staking_impl.domain.staking.bond

import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.bondMore
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger

class BondMoreInteractor(
    private val feeEstimator: FeeEstimator,
    private val extrinsicService: ExtrinsicService,
) {

    suspend fun estimateFee(accountAddress: String, amount: BigInteger): BigInteger {
        return withContext(Dispatchers.IO) {
            feeEstimator.estimateFee(accountAddress) {
                bondMore(amount)
            }
        }
    }

    suspend fun bondMore(accountAddress: String, amount: BigInteger): Result<String> {
       return withContext(Dispatchers.IO) {
           extrinsicService.submitExtrinsic(accountAddress) {
               bondMore(amount)
           }
       }
    }
}
