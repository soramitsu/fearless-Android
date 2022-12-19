package jp.co.soramitsu.wallet.api.presentation.mixin.fee

import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Retriable
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.api.presentation.model.FeeModel
import kotlinx.coroutines.CoroutineScope
import java.math.BigDecimal
import java.math.BigInteger

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
            feeConstructor: suspend (Token) -> BigInteger,
            onRetryCancelled: () -> Unit,
            onComplete: ((FeeStatus?) -> Unit)? = null
        )

        fun requireFee(
            block: (BigDecimal) -> Unit,
            onError: (title: String, message: String) -> Unit
        )
    }
}

fun FeeLoaderMixin.Presentation.requireFee(
    viewModel: BaseViewModel,
    block: (BigDecimal) -> Unit
) {
    requireFee(block) { title, message ->
        viewModel.showError(title, message)
    }
}
