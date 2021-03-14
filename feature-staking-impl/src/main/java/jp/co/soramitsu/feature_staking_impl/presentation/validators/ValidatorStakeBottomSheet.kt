package jp.co.soramitsu.feature_staking_impl.presentation.validators

import android.content.Context
import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.feature_staking_impl.R

class ValidatorStakeBottomSheet(
    context: Context,
) : FixedListBottomSheet(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        item(R.layout.view_validator_total_stake_item, ) {

        }

        item(R.layout.view_validator_total_stake_item) {

        }

        item(R.layout.view_validator_total_stake_item) {

        }
    }
}
