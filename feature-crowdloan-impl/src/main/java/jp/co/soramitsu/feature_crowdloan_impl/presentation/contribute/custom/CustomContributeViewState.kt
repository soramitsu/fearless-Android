package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleCoroutineScope
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

class KaruraContributeInteractor {

    fun fearlessReferralCode(): String {
        return "0x9642d0db9f3b301b44df74b63b0b930011e3f52154c5ca24b4dc67b3c7322f15"
    }
}

abstract class CustomContributeView  @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle) {

    abstract fun bind(viewState: CustomContributeViewState, scope: LifecycleCoroutineScope)
}

interface CustomContributeSubmitter {

    suspend fun submit(payload: Parcelable): Result<Unit>
}

class KaruraContributeSubmitter(
    private val interactor: KaruraContributeInteractor
) : CustomContributeSubmitter {

    override suspend fun submit(payload: Parcelable): Result<Unit> {
        TODO("Not yet implemented")
    }
}

interface BonusPayload : Parcelable {

    fun calculateBonus(amount: BigDecimal): BigDecimal
}




