package jp.co.soramitsu.feature_staking_impl.domain.validations.payout

import jp.co.soramitsu.feature_wallet_api.domain.validation.EnoughToPayFeesValidation

typealias PayoutFeeValidation = EnoughToPayFeesValidation<MakePayoutPayload, PayoutValidationFailure>
