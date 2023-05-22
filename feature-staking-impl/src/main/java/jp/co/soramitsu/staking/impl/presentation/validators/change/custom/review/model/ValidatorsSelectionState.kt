package jp.co.soramitsu.staking.impl.presentation.validators.change.custom.review.model

import jp.co.soramitsu.common.view.ButtonState

class ValidatorsSelectionState(
    val selectedHeaderText: String,
    val nextButtonText: String,
    val isOverflow: Boolean,
    val buttonState: ButtonState
)
