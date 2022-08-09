package jp.co.soramitsu.featurestakingimpl.domain.validations.payout

import jp.co.soramitsu.featurewalletapi.domain.validation.EnoughToPayFeesValidation

typealias PayoutFeeValidation = EnoughToPayFeesValidation<MakePayoutPayload, PayoutValidationFailure>
