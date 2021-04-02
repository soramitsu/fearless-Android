package jp.co.soramitsu.feature_staking_impl.presentation.common.fee

import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.mixin.api.Retriable
import jp.co.soramitsu.feature_staking_impl.presentation.common.model.FeeModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import kotlinx.coroutines.CoroutineScope
import java.math.BigDecimal

sealed class FeeStatus {
    object Loading : FeeStatus()

    class Loaded(val feeModel: FeeModel) : FeeStatus()

    object Error : FeeStatus()
}

interface FeeLoaderMixin : Retriable {

    val feeLiveData: LiveData<FeeStatus>

    interface Presentation : FeeLoaderMixin {

        fun loadFee(
            coroutineScope: CoroutineScope,
            feeConstructor: suspend (Asset) -> BigDecimal,
            onRetryCancelled: () -> Unit
        )

        fun requireFee(
            block: (BigDecimal) -> Unit,
            onError: (title: String, message: String) -> Unit
        )
    }
}
