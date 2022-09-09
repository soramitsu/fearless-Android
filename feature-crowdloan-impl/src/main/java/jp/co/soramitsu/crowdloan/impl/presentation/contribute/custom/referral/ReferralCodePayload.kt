package jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.referral

import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.BonusPayload

interface ReferralCodePayload : BonusPayload {

    val referralCode: String
}
