package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

interface CustomContributeViewState {

    val applyEnabled: LiveData<Boolean>
}

class KaruraContributeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle)

class KaruraContributeViewState(
    private val interactor: KaruraContributeInteractor
) : CustomContributeViewState {

    override val applyEnabled = MutableLiveData<Boolean>()
}

class KaruraContributeInteractor {

    fun fearlessReferralCode(): String {
        return TODO()
    }
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

interface CustomContributePayload : Parcelable {

    fun calculateBonus(amount: BigDecimal): BigDecimal
}

private val KARURA_BONUS_MULTIPLIER = 0.05.toBigDecimal() // 5%

@Parcelize
class KaruraContributePayload(
    val referralCode: String
) : CustomContributePayload {

    override fun calculateBonus(amount: BigDecimal): BigDecimal {
        return amount * KARURA_BONUS_MULTIPLIER
    }
}


