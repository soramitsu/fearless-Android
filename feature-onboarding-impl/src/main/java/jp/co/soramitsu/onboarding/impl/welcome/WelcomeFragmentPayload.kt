package jp.co.soramitsu.onboarding.impl.welcome

import android.os.Parcelable
import jp.co.soramitsu.account.api.presentation.account.create.ChainAccountCreatePayload
import kotlinx.parcelize.Parcelize

@Parcelize
class WelcomeFragmentPayload(
    val displayBack: Boolean,
    val createChainAccount: ChainAccountCreatePayload?
) : Parcelable
