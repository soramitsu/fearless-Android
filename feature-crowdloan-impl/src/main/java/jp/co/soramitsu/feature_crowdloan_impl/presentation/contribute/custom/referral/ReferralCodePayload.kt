package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral

import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload

interface ReferralCodePayload : BonusPayload {

    val referralCode: String
}
