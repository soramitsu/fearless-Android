package jp.co.soramitsu.feature_staking_impl.presentation.validators.details

import android.os.Bundle
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.utils.createSendEmailIntent
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentCollatorDetailsBinding
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.CollatorDetailsParcelModel
import javax.inject.Inject

@AndroidEntryPoint
class CollatorDetailsFragment : BaseFragment<CollatorDetailsViewModel>(R.layout.fragment_collator_details) {

    companion object {
        private const val KEY_COLLATOR = "collator"

        fun getBundle(collator: CollatorDetailsParcelModel): Bundle {
            return Bundle().apply {
                putParcelable(KEY_COLLATOR, collator)
            }
        }
    }

    @Inject
    lateinit var factory: CollatorDetailsViewModel.CollatorDetailsViewModelFactory

    private val vm: CollatorDetailsViewModel by viewModels {
        CollatorDetailsViewModel.provideFactory(
            factory,
            argument(KEY_COLLATOR)
        )
    }
    override val viewModel: CollatorDetailsViewModel
        get() = vm

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

                collatorInfoProgress.makeGone()
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
