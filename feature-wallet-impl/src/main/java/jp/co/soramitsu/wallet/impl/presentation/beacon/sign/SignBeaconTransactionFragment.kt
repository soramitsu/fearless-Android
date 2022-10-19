package jp.co.soramitsu.wallet.impl.presentation.beacon.sign

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import it.airgap.beaconsdk.blockchain.substrate.data.SubstrateSignerPayload
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.view.dialog.warningDialog
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentSignBeaconTransactionBinding
import jp.co.soramitsu.wallet.impl.presentation.beacon.main.DAppMetadataModel

const val SIGN_PAYLOAD_KEY = "SIGN_PAYLOAD_KEY"

class ParcelableJsonPayload(val payload: SubstrateSignerPayload.Json) : Parcelable {
    constructor(parcel: Parcel) : this(
        SubstrateSignerPayload.Json(
            parcel.readString() ?: "",
            parcel.readString() ?: "",
            parcel.readString() ?: "",
            parcel.readString() ?: "",
            parcel.readString() ?: "",
            parcel.readString() ?: "",
            parcel.readString() ?: "",
            parcel.readString() ?: "",
            parcel.readString() ?: "",
            parcel.createStringArrayList() ?: listOf(),
            parcel.readLong()
        )
    ) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(payload.blockHash)
        parcel.writeString(payload.blockNumber)
        parcel.writeString(payload.era)
        parcel.writeString(payload.genesisHash)
        parcel.writeString(payload.method)
        parcel.writeString(payload.nonce)
        parcel.writeString(payload.specVersion)
        parcel.writeString(payload.tip)
        parcel.writeString(payload.transactionVersion)
        parcel.writeStringList(payload.signedExtensions)
        parcel.writeLong(payload.version)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ParcelableJsonPayload> {
        override fun createFromParcel(parcel: Parcel): ParcelableJsonPayload {
            return ParcelableJsonPayload(parcel)
        }

        override fun newArray(size: Int): Array<ParcelableJsonPayload?> {
            return arrayOfNulls(size)
        }
    }
}

@AndroidEntryPoint
class SignBeaconTransactionFragment : BaseFragment<SignBeaconTransactionViewModel>(R.layout.fragment_sign_beacon_transaction) {

    companion object {
        const val SIGN_RESULT_KEY = "SIGN_STATUS_KEY"
        const val METADATA_KEY = "METADATA_KEY"
        const val JSON_PAYLOAD_KEY = "JSON_PAYLOAD_KEY"

        fun getBundle(payload: SubstrateSignerPayload, dAppMetadata: DAppMetadataModel) = Bundle().apply {
            when (payload) {
                is SubstrateSignerPayload.Raw -> {
                    putString(SIGN_PAYLOAD_KEY, payload.data)
                }
                is SubstrateSignerPayload.Json -> {
                    putParcelable(JSON_PAYLOAD_KEY, ParcelableJsonPayload(payload))
                }
            }

            putParcelable(METADATA_KEY, dAppMetadata)
        }
    }

    override val viewModel: SignBeaconTransactionViewModel by viewModels()
    private val binding by viewBinding(FragmentSignBeaconTransactionBinding::bind)

    override fun initViews() {
        binding.signBeaconTransactionContainer.applyInsetter {
            type(statusBars = true) {
                margin()
            }
        }

        binding.signBeaconTransactionToolbar.setHomeButtonListener { openExitDialog() }
        onBackPressed { openExitDialog() }

        binding.signBeaconTransactionConfirm.setOnClickListener { viewModel.confirmClicked() }
        binding.signBeaconTransactionRawData.setOnClickListener { viewModel.rawDataClicked() }
    }

    private fun openExitDialog() {
        warningDialog(
            requireContext(),
            onConfirm = { viewModel.exit() }
        ) {
            setTitle(R.string.common_are_you_sure)
            setMessage(R.string.beacon_decline_signing_message)
        }
    }

    override fun subscribe(viewModel: SignBeaconTransactionViewModel) {
        viewModel.operationModel.observe {
            when (it) {
                is SignableOperationModel.Success -> {
                    binding.signBeaconTransactionAmount.showValueOrHide(it.amount?.token, it.amount?.fiat)
                    binding.signBeaconTransactionDappName.showValueOrHide(viewModel.dAppMetadataModel.name)
                    binding.signBeaconTransactionNetwork.showValueOrHide(it.chainName)
                }
                is SignableOperationModel.Failure -> {
                    binding.signBeaconTransactionAmount.makeGone()
                    binding.signBeaconTransactionRawData.makeGone()
                    binding.signBeaconTransactionFee.makeGone()
                }
            }
        }

        viewModel.feeLiveData.observe(binding.signBeaconTransactionFee::setFeeStatus)

        viewModel.currentAccountAddressModel.observe {
            binding.signBeaconTransactionOrigin.setTitle(it.name ?: "")
            binding.signBeaconTransactionOrigin.setAccountIcon(it.image)
        }

        viewModel.totalBalanceLiveData.observe {
            binding.signBeaconTransactionOrigin.setText(it)
        }

        viewModel.receiver.observe {
            binding.signBeaconTransactionReceiver.isVisible = it != null
            it ?: return@observe
            binding.signBeaconTransactionReceiver.setAccountIcon(it.image)
            binding.signBeaconTransactionReceiver.setTitle("To")
            binding.signBeaconTransactionReceiver.setText(it.address)
        }
    }
}
