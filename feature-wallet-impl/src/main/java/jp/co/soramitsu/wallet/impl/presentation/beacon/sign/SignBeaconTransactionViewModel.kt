package jp.co.soramitsu.wallet.impl.presentation.beacon.sign

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.GetTotalBalanceUseCase
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.wallet.api.data.mappers.mapFeeToFeeModel
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import jp.co.soramitsu.wallet.api.presentation.model.AmountModel
import jp.co.soramitsu.wallet.api.presentation.model.mapAmountToAmountModel
import jp.co.soramitsu.wallet.impl.domain.beacon.BeaconInteractor
import jp.co.soramitsu.wallet.impl.domain.beacon.SignStatus
import jp.co.soramitsu.wallet.impl.domain.beacon.SignableOperation
import jp.co.soramitsu.wallet.impl.domain.beacon.WithAmount
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.beacon.main.DAppMetadataModel
import jp.co.soramitsu.wallet.impl.presentation.beacon.sign.SignBeaconTransactionFragment.Companion.JSON_PAYLOAD_KEY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed class SignableOperationModel {
    data class Success(
        val module: String,
        val call: String,
        val amount: AmountModel?,
        val rawData: String,
        val chainName: String?,
        val destination: String?
    ) : SignableOperationModel()

    object Failure : SignableOperationModel()
}

@HiltViewModel
class SignBeaconTransactionViewModel @Inject constructor(
    private val beaconInteractor: BeaconInteractor,
    private val router: WalletRouter,
    private val interactor: WalletInteractor,
    private val iconGenerator: AddressIconGenerator,
    private val resourceManager: ResourceManager,
    totalBalance: GetTotalBalanceUseCase,
    savedStateHandle: SavedStateHandle
) : BaseViewModel() {

    private val rawToSign = savedStateHandle.get<String>(SIGN_PAYLOAD_KEY)
    private val jsonToSign = savedStateHandle.get<ParcelableJsonPayload>(JSON_PAYLOAD_KEY)

    val dAppMetadataModel = savedStateHandle.get<DAppMetadataModel>(SignBeaconTransactionFragment.METADATA_KEY)!!

    private val currentAccount = interactor.selectedAccountFlow(polkadotChainId)
        .inBackground()
        .share()

    val currentAccountAddressModel = currentAccount
        .map { iconGenerator.createAddressModel(it.address, AddressIconGenerator.SIZE_SMALL, it.name) }
        .inBackground()
        .share()

    val totalBalanceLiveData = totalBalance().map { it.balance.format() }.asLiveData()

    private val decodedOperation = flow {
        val result = when {
            rawToSign != null -> beaconInteractor.decodeOperation(rawToSign)
            jsonToSign != null -> beaconInteractor.decodeOperation(jsonToSign.payload)
            else -> null
        }
        result?.let { emit(it) }
    }

    val receiver = decodedOperation.map {
        val destination = (it.getOrNull() as? SignableOperation.Transfer)?.destination ?: return@map null
        iconGenerator.createAddressModel(destination, AddressIconGenerator.SIZE_SMALL, null)
    }

    private val _feeLiveData = MutableLiveData<FeeStatus>(FeeStatus.Loading)
    val feeLiveData: LiveData<FeeStatus> = _feeLiveData

    init {
        loadTransactionFee()
    }

    private val currentAssetFlow: Flow<Asset?> = flowOf {
        beaconInteractor.getBeaconRegisteredChain()
    }.flatMapLatest {
        it ?: flowOf { null }
        val assetId = it!!.assets.first().id
        interactor.assetFlow(it.id, assetId)
    }

    val operationModel = combine(
        decodedOperation,
        currentAssetFlow,
        ::mapOperationToOperationModel
    ).onEach {
        cachedOperationModel = it
    }

    private var cachedOperationModel: SignableOperationModel? = null

    fun exit() {
        router.setBeaconSignStatus(SignStatus.DECLINED)

        router.back()
    }

    private fun loadTransactionFee() {
        decodedOperation.onEach { result ->
            try {
                val operation = result.getOrNull() ?: return@onEach

                val feeInPlanks = beaconInteractor.estimateFee(operation)
                val chain = beaconInteractor.getBeaconRegisteredChain() ?: return@onEach
                val asset = chain.assets.firstOrNull() ?: return@onEach
                val token = interactor.getCurrentAsset(chain.id, asset.id).token

                val fee = asset.amountFromPlanks(feeInPlanks)

                _feeLiveData.value = FeeStatus.Loaded(mapFeeToFeeModel(fee, token))
            } catch (e: Exception) {
                _feeLiveData.value = FeeStatus.Error
            }
        }.launchIn(viewModelScope)
    }

    private fun mapOperationToOperationModel(operationResult: Result<SignableOperation>, asset: Asset?): SignableOperationModel {
        val operation = operationResult.getOrNull() ?: return SignableOperationModel.Failure
        asset ?: return SignableOperationModel.Failure
        val amountModel = (operation as? WithAmount)?.let {
            mapAmountToAmountModel(it.amount, asset)
        }
        val destination = (operation as? SignableOperation.Transfer)?.destination
        return SignableOperationModel.Success(
            module = operation.module,
            call = operation.call,
            amount = amountModel,
            rawData = operation.rawData,
            chainName = asset.token.configuration.chainName,
            destination = destination
        )
    }

    fun confirmClicked() {
        viewModelScope.launch {
            router.setBeaconSignStatus(SignStatus.APPROVED)

            router.back()
            router.openSuccessFragment(currentAccountAddressModel.first().image)
        }
    }

    fun rawDataClicked() {
        viewModelScope.launch {
            val rawData = (cachedOperationModel as? SignableOperationModel.Success)?.rawData ?: return@launch
            router.openTransactionRawData(rawData)
        }
    }
}
