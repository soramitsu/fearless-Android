package jp.co.soramitsu.feature_onboarding_impl.presentation.welcome

import android.os.Parcelable
import jp.co.soramitsu.feature_account_api.presentation.account.create.ChainAccountCreatePayload
import kotlinx.parcelize.Parcelize

@Parcelize
class WelcomeFragmentPayload(
    val displayBack: Boolean,
    val createChainAccount: ChainAccountCreatePayload?,
) : Parcelable
