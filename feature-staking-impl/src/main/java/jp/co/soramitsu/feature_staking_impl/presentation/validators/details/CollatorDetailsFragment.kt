package jp.co.soramitsu.feature_staking_impl.presentation.validators.details

import android.os.Bundle
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.utils.createSendEmailIntent
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentCollatorDetailsBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.CollatorDetailsParcelModel

class CollatorDetailsFragment : BaseFragment<CollatorDetailsViewModel>(R.layout.fragment_collator_details) {

    companion object {
        private const val KEY_COLLATOR = "collator"

        fun getBundle(collator: CollatorDetailsParcelModel): Bundle {
            return Bundle().apply {
                putParcelable(KEY_COLLATOR, collator)
            }
        }
    }

    private val binding by viewBinding(FragmentCollatorDetailsBinding::bind)

    override fun initViews() {
        with(binding) {
            collatorDetailsToolbar.setHomeButtonListener { viewModel.backClicked() }

            collatorInfo.setTotalStakeClickListener {
                viewModel.totalStakeClicked()
            }

            collatorIdentity.setEmailClickListener {
                viewModel.emailClicked()
            }

            collatorIdentity.setWebClickListener {
                viewModel.webClicked()
            }

            collatorIdentity.setTwitterClickListener {
                viewModel.twitterClicked()
            }

            collatorAccountInfo.setWholeClickListener { viewModel.accountActionsClicked() }
        }
    }

    override fun inject() {
        val collator = argument<CollatorDetailsParcelModel>(KEY_COLLATOR)

        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .collatorDetailsComponentFactory()
            .create(this, collator)
            .inject(this)
    }

    override fun subscribe(viewModel: CollatorDetailsViewModel) {
        setupExternalActions(viewModel)
        observeBrowserEvents(viewModel)

        viewModel.collatorDetails.observe { collator ->
            with(binding) {
                collatorInfo.setStatus(collator.statusText, collator.statusColor)

                collatorInfo.showActiveStakeFields()
                collatorInfo.setDelegationsCount(collator.delegations)
                collatorInfo.setEstimatedRewardApr(collator.estimatedRewardsApr)
                collatorInfo.setTotalStakeValue(collator.totalStake)
                collatorInfo.setTotalStakeValueFiat(collator.totalStakeFiat)
                collatorInfo.setMinBond(collator.minBond)
                collatorInfo.setSelfBonded(collator.selfBonded)
                collatorInfo.setEffectiveAmountBonded(collator.effectiveAmountBonded)

                if (collator.identity == null || collator.identity.isEmptyExceptName) {
                    collatorIdentity.makeGone()
                } else {
                    collatorIdentity.makeVisible()
                    collatorIdentity.populateIdentity(collator.identity)
                }

                collatorAccountInfo.setAccountIcon(collator.addressImage)

                if (collator.identity?.display == null) {
                    collatorAccountInfo.setTitle(collator.address)
                    collatorAccountInfo.hideBody()
                } else {
                    collatorAccountInfo.setTitle(collator.identity.display)
                    collatorAccountInfo.setText(collator.address)
                    collatorAccountInfo.showBody()
                }
            }
        }

//        viewModel.errorFlow.observe {
//            it?.let { validatorInfo.setErrors(it) }
//        }

        viewModel.openEmailEvent.observeEvent {
            requireContext().createSendEmailIntent(it, getString(R.string.common_email_chooser_title))
        }

        viewModel.totalStakeEvent.observeEvent {
            ValidatorStakeBottomSheet(requireContext(), it).show()
        }
    }
}
