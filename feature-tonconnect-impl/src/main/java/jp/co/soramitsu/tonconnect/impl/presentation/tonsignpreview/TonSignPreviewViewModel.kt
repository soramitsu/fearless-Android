package jp.co.soramitsu.tonconnect.impl.presentation.tonsignpreview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.feature_tonconnect_impl.R
import co.jp.soramitsu.tonconnect.domain.TonConnectInteractor
import co.jp.soramitsu.tonconnect.domain.TonConnectRouter
import co.jp.soramitsu.tonconnect.model.DappModel
import co.jp.soramitsu.tonconnect.model.SignRequestEntity
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.http.Url
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.WalletNameItemViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.putTransferParams
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.TonWalletContract
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.V4R2WalletContract
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MessageRelaxed
import org.ton.block.StateInit
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import org.ton.tlb.CellRef
import org.ton.tlb.asRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeRef

@HiltViewModel
class TonSignPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tonConnectInteractor: TonConnectInteractor,
    private val tonConnectRouter: TonConnectRouter,
    private val resourceManager: ResourceManager,
    private val accountRepository: AccountRepository,
    private val addressIconGenerator: AddressIconGenerator
) : TonSignPreviewScreenInterface, BaseViewModel() {

    private val signRequest: SignRequestEntity = savedStateHandle[TonSignPreviewFragment.TON_SIGN_REQUEST_KEY] ?: error("No sign request provided")
    private val dApp: DappModel = savedStateHandle[TonSignPreviewFragment.PAYLOAD_DAPP_KEY] ?: error("No dApp info provided")
    private val method: String = savedStateHandle.get<String>(TonSignPreviewFragment.METHOD_KEY).orEmpty()

    private val isLoading = MutableStateFlow(false)
    private val requestChainFlow = MutableSharedFlow<Chain?>()
        .onStart {
            val value: Chain = tonConnectInteractor.getChain()
            emit(value)
        }
        .stateIn(this, SharingStarted.Eagerly, null)

    private val requestWalletItemFlow: SharedFlow<WalletNameItemViewState?> = requestChainFlow.filterNotNull().map { requestChain ->

        val requestedWallet = accountRepository.selectedMetaAccountFlow().firstOrNull() ?: error("no wallet")

        val requestedWalletIcon = addressIconGenerator.createAddressIcon(
            requestedWallet.substrateAccountId,
            AddressIconGenerator.SIZE_BIG
        )

        WalletNameItemViewState(
            id = requestedWallet.id,
            title = requestedWallet.name,
            isSelected = false,
            walletIcon = requestedWalletIcon
        )
    }
        .inBackground()
        .share()

    val state = combine(requestWalletItemFlow, requestChainFlow.filterNotNull(), isLoading) { requestWallet, requestChain, isLoading ->
        val icon = GradientIconState.Remote(requestChain.icon, "0098ED")

        val tableItems = listOf(
            TitleValueViewState(
                resourceManager.getString(R.string.common_dapp),
                dApp.name
            ),
            TitleValueViewState(
                resourceManager.getString(R.string.common_host),
                kotlin.runCatching { Url(dApp.url.orEmpty()).host }.getOrNull()
            ),
            TitleValueViewState(
                resourceManager.getString(R.string.common_network),
                requestChain.name
            ),
            TitleValueViewState(
                resourceManager.getString(R.string.common_transaction_raw_data),
                value = "",
                clickState = TitleValueViewState.ClickState.Value(R.drawable.ic_right_arrow_24_align_right, TRANSACTION_RAW_DATA_CLICK_ID)
            )
        )

        requestWallet?.let {
            TonSignPreviewViewState(
                chainIcon = icon,
                method = method,
                tableItems = tableItems,
                wallet = it,
                loading = isLoading
            )
        } ?: TonSignPreviewViewState.default
    }
        .stateIn(this, SharingStarted.Eagerly, TonSignPreviewViewState.default)

    override fun onClose() {
        launch(Dispatchers.Main) {
            tonConnectRouter.back()
        }
    }

    override fun onSignClick() {
        println("!!! onSignClick")
        if (isLoading.value) return
        val chain = requestChainFlow.value ?: return

        viewModelScope.launch {
            val selectedMetaAccount = async { accountRepository.getSelectedMetaAccount() }

            val tonPublicKey = selectedMetaAccount.await().tonPublicKey ?: error("No account provided")
            val senderAccountId = tonPublicKey.tonAccountId(chain.isTestNet)

            val contract = V4R2WalletContract(tonPublicKey)


            val validUntil: Long = signRequest.validUntil
            val seqnoDeferred = async { tonConnectInteractor.getSeqno(chain, senderAccountId) }
            val seqNo = seqnoDeferred.await()


            val transfers = mutableListOf<WalletTransfer>()


            val tonAssets = listOf<Asset>()
            val compressedTokens = tonAssets.filter {
                "it.isCompressed"
                true
            }

            for (message in signRequest.messages) {
                val jetton = compressedTokens.firstOrNull {
//                    it.address.equalsAddress(message.addressValue) ||
//                            it.balance.walletAddress.equalsAddress(message.addressValue)
                    false
                }
                val jettonCustomPayload = jetton?.let {
//                    api.getJettonCustomPayload(wallet.accountId, wallet.testnet, it.address)
                }

                val newStateInit = null //jettonCustomPayload?.stateInit,
                val newCustomPayload = null // jettonCustomPayload?.customPayload,

                val getStateInit = message.stateInitValue?.cellFromBase64()?.asRef(StateInit)

//                val payload = getPayload()
                val payload = Cell.empty()
                val body = payload

                val builder = WalletTransferBuilder()
                builder.destination = message.address
//                builder.messageData = MessageData.Raw(body, newStateInit ?: getStateInit)
                builder.bounceable = message.address.isBounceable()
                if (newCustomPayload != null) {
                    val defCoins = Coins.of(0.5)
                    if (defCoins.amount.value > message.coins.amount.value) {
                        builder.coins = defCoins
                    } else {
                        builder.coins = message.coins
                    }
                } else {
                    builder.coins = message.coins
                }
                val transfer = builder.build()

                transfers.add(transfer)
            }


            if (transfers.size > contract.maxMessages) {
                throw IllegalArgumentException("Maximum number of messages in a single transfer is ${contract.maxMessages}")
            }

            val unsignedBody: Cell = CellBuilder.createCell {
                storeUInt(contract.walletId, 32)
                putTransferParams(seqNo, validUntil)
                storeUInt(0, 8)
                for (gift in transfers) {
                    var sendMode = 3
                    if (gift.sendMode > -1) {
                        sendMode = gift.sendMode
                    }
                    storeUInt(sendMode, 8)

                    val intMsg = CellRef(TonWalletContract.createIntMsg(gift))
                    storeRef(MessageRelaxed.tlbCodec(AnyTlbConstructor), intMsg)
                }
            }

            //== sign


        }


//        isLoading.value = true
    }

    /// ==== Extentions begin
    fun String.cellFromBase64(): Cell {
        val parsed = bocFromBase64()
        if (parsed.roots.size != 1) {
            throw IllegalArgumentException("Deserialized more than one cell")
        }
        return parsed.first()
    }

    fun String.bocFromBase64(): BagOfCells {
        if (startsWith("{")) {
            throw IllegalArgumentException("js objects are not supported")
        }
        return BagOfCells(decodeBase64())
    }

    fun String.decodeBase64(): ByteArray {
        // force non-url safe base64
        val replaced = replace('-', '+').replace('_', '/')
        /*
        val replaced = trim()
            .replace('-', '+')
            .replace('_', '/')
        val paddedLength = (4 - replaced.length % 4) % 4
        val paddedString = replaced + "=".repeat(paddedLength)
        return paddedString.base64DecodedBytes*/
        return replaced.base64DecodedBytes
    }

    /**
     * Decode a Base64 standard encoded [String] to [ByteArray].
     *
     * See [RFC 4648 §4](https://datatracker.ietf.org/doc/html/rfc4648#section-4)
     */
    val String.base64DecodedBytes: ByteArray
        get() = decodeInternal(Encoding.Standard).map { it.toByte() }.toList().dropLast(count { it == '=' }).toByteArray()

    fun String.decodeInternal(encoding: Encoding): Sequence<Int> {
        val padLength = when (length % 4) {
            1 -> 3
            2 -> 2
            3 -> 1
            else -> 0
        }
        return padEnd(length + padLength, '=')
            .replace("=", "A")
            .chunkedSequence(4) {
                (encoding.alphabet.indexOf(it[0]) shl 18) + (encoding.alphabet.indexOf(it[1]) shl 12) +
                        (encoding.alphabet.indexOf(it[2]) shl 6) + encoding.alphabet.indexOf(it[3])
            }
            .map { sequenceOf(0xFF.and(it shr 16), 0xFF.and(it shr 8), 0xFF.and(it)) }
            .flatten()
    }

    sealed interface Encoding {
        val alphabet: String
        val requiresPadding: Boolean

        data object Standard : Encoding {
            override val alphabet: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            override val requiresPadding: Boolean = true
        }

        data object UrlSafe : Encoding {
            override val alphabet: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
            override val requiresPadding: Boolean = false // Padding is optional
        }
    }

    fun AddrStd.isBounceable(): Boolean {
        return toString(userFriendly = true).isBounceable()
    }
    fun String.isBounceable(): Boolean {
        return !startsWith("UQ")
    }

    /// ==== Extentions end

    private fun onRespondSessionRequestSuccess(operationHash: String?, chainId: ChainId?) {
//        isLoading.value = false
//        viewModelScope.launch(Dispatchers.Main) {
//            tonConnectRouter.openOperationSuccessAndPopUpToNearestRelatedScreen(
//                operationHash = operationHash,
//                chainId = chainId,
//                customMessage = null
//            )
//        }
    }

//    private fun onRespondRequestSessionError(error: Wallet.Model.Error) {
//        isLoading.value = false
//        viewModelScope.launch(Dispatchers.Main) {
//            showError(
//                title = resourceManager.getString(R.string.common_error_general_title),
//                message = resourceManager.getString(R.string.common_try_again) + "\n" + error.throwable.message.orEmpty(),
//                positiveButtonText = resourceManager.getString(R.string.common_ok)
//            )
//        }
//    }

    private fun onSignError(e: Exception) {
//        isLoading.value = false
//        viewModelScope.launch(Dispatchers.Main) {
//            showError(
//                title = resourceManager.getString(R.string.common_error_general_title),
//                message = resourceManager.getString(R.string.common_try_again) + "\n" + e.message.orEmpty(),
//                positiveButtonText = resourceManager.getString(R.string.common_ok)
//            )
//        }
    }

    override fun onTableItemClick(id: Int) {
        println("!!! onTableItemClick id = $id")
        if (id == TRANSACTION_RAW_DATA_CLICK_ID) {
            tonConnectRouter.openRawData(Gson().toJson(signRequest))
        }
    }

    override fun onTableRowClick(id: Int) {
        println("!!! onTableRowClick id = $id")
        if (id == TRANSACTION_RAW_DATA_CLICK_ID) {
            tonConnectRouter.openRawData(Gson().toJson(signRequest))
        }
    }

    companion object {
        private const val TRANSACTION_RAW_DATA_CLICK_ID = 1
    }
}
