package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleCoroutineScope
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

sealed class ApplyActionState {
    object Available : ApplyActionState()

    class Unavailable(val reason: String) : ApplyActionState()
}

interface CustomContributeViewState {

    suspend fun generatePayload(): Result<BonusPayload>

    val applyActionState: Flow<ApplyActionState>
}

abstract class CustomContributeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle) {

    abstract fun bind(viewState: CustomContributeViewState, scope: LifecycleCoroutineScope)
}

interface CustomContributeSubmitter {

    suspend fun submitOnChain(
        payload: BonusPayload,
        amount: BigDecimal,
        extrinsicBuilder: ExtrinsicBuilder
    ) {
        // do nothing by default
    }

    suspend fun submitOffChain(
        payload: BonusPayload,
        amount: BigDecimal
    ): Result<Unit> {
        // do nothing by default

        return Result.success(Unit)
    }
}

interface BonusPayload : Parcelable {

    fun calculateBonus(amount: BigDecimal): BigDecimal
}
