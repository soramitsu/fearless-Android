package jp.co.soramitsu.featureonboardingimpl.presentation.welcome

import android.os.Parcelable
import jp.co.soramitsu.featureaccountapi.presentation.account.create.ChainAccountCreatePayload
import kotlinx.parcelize.Parcelize

@Parcelize
class WelcomeFragmentPayload(
    val displayBack: Boolean,
    val createChainAccount: ChainAccountCreatePayload?
) : Parcelable
