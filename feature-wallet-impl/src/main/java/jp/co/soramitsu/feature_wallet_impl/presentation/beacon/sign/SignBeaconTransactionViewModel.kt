package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.sign

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_wallet_api.presentation.model.AmountModel
import jp.co.soramitsu.feature_wallet_api.presentation.model.mapAmountToAmountModel
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.domain.beacon.BeaconInteractor
import jp.co.soramitsu.feature_wallet_impl.domain.beacon.SignStatus
import jp.co.soramitsu.feature_wallet_impl.domain.beacon.SignableOperation
import jp.co.soramitsu.feature_wallet_impl.domain.beacon.WithAmount
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.math.BigDecimal

class SignableOperationModel(
    val module: String,
    val call: String,
    val amount: AmountModel?,
    val rawData: String
)

class SignBeaconTransactionViewModel(
    private val beaconInteractor: BeaconInteractor,
    private val router: WalletRouter,
    private val interactor: WalletInteractor,
    private val iconGenerator: AddressIconGenerator,
    private val payloadToSign: String,
    private val resourceManager: ResourceManager,
    private val feeLoaderProvider: FeeLoaderMixin.Presentation
) : BaseViewModel(), FeeLoaderMixin by feeLoaderProvider {

    private val currentAccount = interactor.selectedAccountFlow(polkadotChainId)
        .inBackground()
        .share()

    val currentAccountAddressModel = currentAccount
        .map { iconGenerator.createAddressModel(it.address, AddressIconGenerator.SIZE_SMALL, it.name) }
        .inBackground()
        .share()

    private val decodedOperation = flow {
        val result = beaconInteractor.decodeOperation(payloadToSign)

        if (result.isSuccess) {
            emit(result.getOrThrow())
        } else {
            showMessage(resourceManager.getString(R.string.common_cannot_decode_transaction))

            exit()
        }
    }

    init {
        loadFee()
    }

    val operationModel = combine(
        decodedOperation,
        interactor.assetFlow(polkadotChainId, "0"), //0 is polkadot asset id
        ::mapOperationToOperationModel
    )

    fun exit() {
        router.setBeaconSignStatus(SignStatus.DECLINED)

        router.back()
    }

    private fun loadFee() {
        decodedOperation.onEach { operation ->
            feeLoaderProvider.loadFee(
                viewModelScope,
                feeConstructor = {
                    val feeInPlanks = beaconInteractor.estimateFee(operation)

                    it.amountFromPlanks(feeInPlanks).toBigInteger()
                },
                onRetryCancelled = ::exit
            )
        }.launchIn(viewModelScope)
    }

    private fun mapOperationToOperationModel(operation: SignableOperation, asset: Asset): SignableOperationModel {
        val amountModel = (operation as? WithAmount)?.let {
            mapAmountToAmountModel(it.amount, asset)
        }

        return SignableOperationModel(
            module = operation.module,
            call = operation.call,
            amount = amountModel,
            rawData = operation.rawData
        )
    }

    fun confirmClicked() = requireFee {
        router.setBeaconSignStatus(SignStatus.APPROVED)

        router.back()
    }

    private fun requireFee(block: (BigDecimal) -> Unit) = feeLoaderProvider.requireFee(
        block,
        onError = { title, message -> showError(title, message) }
    )
}
