package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.sign

import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.flowOf
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

sealed class SignableOperationModel {
    data class Success(
        val module: String,
        val call: String,
        val amount: AmountModel?,
        val rawData: String
    ) : SignableOperationModel()

    object Failure : SignableOperationModel()
}

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
        hashCode()
        val result = if (payloadToSign.isEmpty()) {
            showMessage(resourceManager.getString(R.string.common_cannot_decode_transaction))
            Result.failure(IllegalArgumentException())
        } else beaconInteractor.decodeOperation(payloadToSign)

        emit(result)
    }

    init {
        loadFee()
    }

    private val currentAssetFlow: Flow<Asset?> = flowOf {
        beaconInteractor.getBeaconRegisteredChain()
    }.flatMapLatest {
        it ?: flowOf { null }
        val assetId = it!!.assets.first().symbol.lowercase()
        interactor.assetFlow(it.id, assetId)
    }

    val operationModel = combine(
        decodedOperation,
        currentAssetFlow,
        ::mapOperationToOperationModel
    )

    fun exit() {
        router.setBeaconSignStatus(SignStatus.DECLINED)

        router.back()
    }

    private fun loadFee() {
        decodedOperation.onEach { result ->
            val operation = result.getOrNull() ?: return@onEach
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

    private fun mapOperationToOperationModel(operationResult: Result<SignableOperation>, asset: Asset?): SignableOperationModel {
        val operation = operationResult.getOrNull() ?: return SignableOperationModel.Failure
        asset ?: return SignableOperationModel.Failure
        val amountModel = (operation as? WithAmount)?.let {
            mapAmountToAmountModel(it.amount, asset)
        }

        return SignableOperationModel.Success(
            module = operation.module,
            call = operation.call,
            amount = amountModel,
            rawData = operation.rawData
        )
    }

    fun confirmClicked() {// = requireFee {
        router.setBeaconSignStatus(SignStatus.APPROVED)

        router.back()
    }

    private fun requireFee(block: (BigDecimal) -> Unit) = feeLoaderProvider.requireFee(
        block,
        onError = { title, message -> showError(title, message) }
    )
}
