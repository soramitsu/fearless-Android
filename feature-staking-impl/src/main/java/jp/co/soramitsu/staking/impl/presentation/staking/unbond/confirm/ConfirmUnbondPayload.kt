package jp.co.soramitsu.staking.impl.presentation.staking.unbond.confirm

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.UnbondRequestAuthority
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
class ConfirmUnbondPayload(
    val amount: BigDecimal,
    val fee: BigDecimal,
    val collatorAddress: String?,
    @IgnoredOnParcel val review: UnbondRequestAuthority.Confirmation? = null
) : Parcelable
