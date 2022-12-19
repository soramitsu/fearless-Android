package jp.co.soramitsu.staking.impl.domain.validations.payout

import jp.co.soramitsu.wallet.impl.domain.validation.EnoughToPayFeesValidation

typealias PayoutFeeValidation = EnoughToPayFeesValidation<MakePayoutPayload, PayoutValidationFailure>
