package jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.referral

import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.BonusPayload

interface ReferralCodePayload : BonusPayload {

    val referralCode: String
}
