package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import coil.load
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.setCompoundDrawableTint
import jp.co.soramitsu.common.view.dialog.warningDialog
import jp.co.soramitsu.common.view.setFromAddressModel
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import kotlinx.android.synthetic.main.fragment_beacon.beaconAppAddress
import kotlinx.android.synthetic.main.fragment_beacon.beaconAppIcon
import kotlinx.android.synthetic.main.fragment_beacon.beaconAppName
import kotlinx.android.synthetic.main.fragment_beacon.beaconContainer
import kotlinx.android.synthetic.main.fragment_beacon.beaconAppUrl
import kotlinx.android.synthetic.main.fragment_beacon.beaconSelectedAccount
import kotlinx.android.synthetic.main.fragment_beacon.beaconStatus
import kotlinx.android.synthetic.main.fragment_beacon.beaconToolbar

private const val QR_CONTENT_KEY = "QR_CONTENT_KEY"

class BeaconFragment : BaseFragment<BeaconViewModel>() {

    companion object {

        fun getBundle(qrContent: String) = Bundle().apply {
            putString(QR_CONTENT_KEY, qrContent)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_beacon, container, false)

    override fun initViews() {
        beaconStatus.valuePrimary.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_status_indicator, 0)
        beaconStatus.valuePrimary.compoundDrawablePadding = 12.dp
        setStatus(active = false)

        beaconContainer.applyInsetter {
            type(statusBars = true) {
                margin()
            }
        }

        beaconToolbar.setHomeButtonListener { openExitDialog() }
        onBackPressed { openExitDialog() }
    }

    private fun openExitDialog() {
        warningDialog(
            requireContext(),
            onConfirm = { viewModel.exit() }
        ) {
            setTitle(R.string.common_are_you_sure)
            setMessage(R.string.beacon_exit_message)
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .beaconComponentFactory()
            .create(this, argument(QR_CONTENT_KEY))
            .inject(this)
    }

    override fun subscribe(viewModel: BeaconViewModel) {
        viewModel.state.observe {
            when(it) {
                is BeaconStateMachine.State.Connected -> {
                    val metadata = it.dAppMetadata

                    setStatus(active = true)

                    beaconAppName.text = metadata.name
                    metadata.icon?.let(beaconAppIcon::load)

                    beaconAppUrl.showValueOrHide(metadata.url)
                    beaconAppAddress.showValue(metadata.address)
                }
            }
        }

        viewModel.currentAccountAddressModel.observe(beaconSelectedAccount::setFromAddressModel)

        viewModel.showPermissionRequestSheet.observeEvent {
            PermissionRequestBottomSheet(requireContext(), it, viewModel::permissionGranted, viewModel::permissionDenied)
                .show()
        }
    }

    private fun setStatus(active: Boolean) {
        val (statusMessageRes, statusIconTintRes) = if (active) {
            R.string.staking_nominator_status_active to R.color.green
        } else {
            R.string.common_connecting to R.color.gray2
        }

        beaconStatus.showValue(getString(statusMessageRes))
        beaconStatus.valuePrimary.setCompoundDrawableTint(statusIconTintRes)
    }
}
