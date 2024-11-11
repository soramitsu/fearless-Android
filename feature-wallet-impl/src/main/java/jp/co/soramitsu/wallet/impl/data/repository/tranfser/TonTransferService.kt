package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import android.annotation.SuppressLint
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.ton.AccountAddress
import jp.co.soramitsu.common.data.network.ton.AccountStatus
import jp.co.soramitsu.common.data.network.ton.EmulateMessageToWalletRequest
import jp.co.soramitsu.common.data.network.ton.SendBlockchainMessageRequest
import jp.co.soramitsu.common.data.network.ton.TonAccountData
import jp.co.soramitsu.common.data.network.ton.totalFees
import jp.co.soramitsu.common.utils.Punycode
import jp.co.soramitsu.common.utils.TON_BASE_FORWARD_AMOUNT
import jp.co.soramitsu.common.utils.base64
import jp.co.soramitsu.common.utils.lessThanOrEquals
import jp.co.soramitsu.common.utils.putTransferParams
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.common.utils.v4r2tonAddress
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.V4R2WalletContract
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.wallet.impl.domain.model.TonTransferParams
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.bitstring.BitString
import org.ton.bitstring.Bits256
import org.ton.block.AddrNone
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.CommonMsgInfoRelaxed
import org.ton.block.Either
import org.ton.block.ExtInMsgInfo
import org.ton.block.Maybe
import org.ton.block.Message
import org.ton.block.MessageRelaxed
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.CellBuilder.Companion.beginCell
import org.ton.cell.buildCell
import org.ton.contract.wallet.WalletTransfer
import org.ton.crypto.Ed25519
import org.ton.crypto.SecureRandom
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeRef
import org.ton.tlb.storeTlb
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.floor

private const val KEYPAIR_REQUIRED_MESSAGE = "Ton keypair is required for ton transfers"

class TonTransferService(
    private val chain: Chain,
    private val keyPairRepository: KeypairProvider,
    private val accountRepository: AccountRepository,
    private val tonRemoteSource: TonRemoteSource,
    private val assetDao: AssetDao
) : TransferService {

    @SuppressLint("LogNotTimber")
    override suspend fun getTransferFee(transfer: Transfer): BigDecimal = withContext(Dispatchers.Default + SupervisorJob()) {
        val selectedMetaAccount = async { accountRepository.getSelectedMetaAccount() }

        val senderAccountId =
            selectedMetaAccount.await().tonPublicKey?.tonAccountId(chain.isTestNet) ?: throw IllegalStateException(KEYPAIR_REQUIRED_MESSAGE)

        val utilityAsset = chain.utilityAsset ?: throw IllegalStateException(
            "Can't find utility asset for ${chain.name}"
        )

        val account = accountRepository.getSelectedMetaAccount()
        val accountId = account.tonPublicKey ?: throw IllegalStateException(KEYPAIR_REQUIRED_MESSAGE)
        val tonAsset = assetDao.getAsset(account.id, accountId, chain.id, utilityAsset.id)

        if (transfer.chainAsset.type == ChainAssetType.Jetton && tonAsset?.asset?.freeInPlanks.lessThanOrEquals(BigInteger.ZERO)) {
            throw RuntimeException("Can't calculate fee: Not enough tokens for fee")
        }

        val senderSmartContract = V4R2WalletContract(selectedMetaAccount.await().tonPublicKey!!)

        val seqnoDeferred = async { tonRemoteSource.getSeqno(chain, senderAccountId) }

        val fakePrivateKey = PrivateKeyEd25519(Bits256(ByteArray(Ed25519.KEY_SIZE_BYTES)).toByteArray())

        val customPayloadStateInit = when (transfer.chainAsset.type) {
            ChainAssetType.Normal -> {
                null
            }
            ChainAssetType.Jetton -> {
                null
            }
            else -> {
                null
            }
        }

        val seqno = seqnoDeferred.await()
        val stateInit = if (seqno == 0) {
            customPayloadStateInit ?: senderSmartContract.stateInit
        } else {
            customPayloadStateInit
        }
        val transferUnsignedBody = createUnsignedBody(
            transfer.copy(recipient = transfer.sender),
            stateInit,
            seqno,
            senderSmartContract
        )
        val info = ExtInMsgInfo(
            src = AddrNone,
            dest = senderSmartContract.address,
            importFee = Coins()
        )

        val init = if (seqno == 0) {
            stateInit
        } else null

        val maybeStateInit = Maybe.of(init?.let { Either.of<StateInit, CellRef<StateInit>>(null, CellRef(it)) })

        val signature = BitString(fakePrivateKey.sign(transferUnsignedBody.hash()))

        val transferBody = CellBuilder.createCell {
            storeBits(signature)
            storeBits(transferUnsignedBody.bits)
            storeRefs(transferUnsignedBody.refs)
        }

        val body = Either.of<Cell, CellRef<Cell>>(null, CellRef(transferBody))
        val message =  Message(
            info = info,
            init = maybeStateInit,
            body = body
        )

        val transferMessageCell = buildCell {
            storeTlb(Message.tlbCodec(AnyTlbConstructor), message)
        }
        val transferMessageCellBase64 = transferMessageCell.base64()

        val request = EmulateMessageToWalletRequest(transferMessageCellBase64, emptyList())
        val fees = tonRemoteSource.emulateBlockchainMessageRequest(chain, request).totalFees
        requireNotNull(chain.utilityAsset?.amountFromPlanks(BigInteger.valueOf(fees)))
    }

    override fun observeTransferFee(transfer: Transfer): Flow<BigDecimal> {
        return flow {
            val fee =  getTransferFee(transfer)

            emit(fee)
        }
    }

    override suspend fun transfer(transfer: Transfer): String = withContext(Dispatchers.Default) {
        val selectedMetaAccount = async { accountRepository.getSelectedMetaAccount() }
        val senderAccountId =
            selectedMetaAccount.await().tonPublicKey?.tonAccountId(chain.isTestNet) ?: throw IllegalStateException(KEYPAIR_REQUIRED_MESSAGE)
        val senderSmartContract = V4R2WalletContract(selectedMetaAccount.await().tonPublicKey!!)
        val seqnoDeferred = async { tonRemoteSource.getSeqno(chain, senderAccountId) }

        val keypair =
            keyPairRepository.getKeypairFor(chain, selectedMetaAccount.await().tonPublicKey!!)
        val privateKey = PrivateKeyEd25519.of(keypair.privateKey)

        val seqno = seqnoDeferred.await()

        val customPayloadStateInit = when (transfer.chainAsset.type) {
            ChainAssetType.Normal -> {
                null
            }
            ChainAssetType.Jetton -> {
                null
            }
            else -> {
                null
            }
        }

        val stateInit = if (seqno == 0) {
            customPayloadStateInit ?: senderSmartContract.stateInit
        } else {
            customPayloadStateInit
        }

        val transferUnsignedBody = createUnsignedBody(transfer, stateInit, seqno, senderSmartContract)

        val info = ExtInMsgInfo(
            src = AddrNone,
            dest = senderSmartContract.address,
            importFee = Coins()
        )

        val init = if (seqno == 0) {
            stateInit
        } else null

        val maybeStateInit = Maybe.of(init?.let { Either.of<StateInit, CellRef<StateInit>>(null, CellRef(it)) })

        val signature = BitString(privateKey.sign(transferUnsignedBody.hash()))
        val transferBody = CellBuilder.createCell {
            storeBits(signature)
            storeBits(transferUnsignedBody.bits)
            storeRefs(transferUnsignedBody.refs)
        }

        val body = Either.of<Cell, CellRef<Cell>>(null, CellRef(transferBody))
        val message =  Message(
            info = info,
            init = maybeStateInit,
            body = body
        )

        val transferMessageCell = buildCell {
            storeTlb(Message.tlbCodec(AnyTlbConstructor), message)
        }
        val transferMessageCellBase64 = transferMessageCell.base64()
        val request = SendBlockchainMessageRequest(transferMessageCellBase64)

        tonRemoteSource.sendBlockchainMessage(chain, request)

        val hash = transferMessageCell.hash()
        val hashHex = hash.toHexString(true)
        return@withContext hashHex
    }

    private suspend fun createUnsignedBody(
        transfer: Transfer,
        stateInit: StateInit?,
        seqno: Int,
        senderSmartContract: V4R2WalletContract,
    ): Cell = coroutineScope {
        val validUntilDeferred = async {
            val time = runCatching { tonRemoteSource.getRawTime(chain) }.getOrNull()
                ?: (System.currentTimeMillis() / 1000).toInt()
            time + (5 * 30L)
        }

        val normalizedRecipientAccountId = if (transfer.recipient.endsWith(".ton")) {
            transfer.recipient.lowercase().trim().unicodeToPunycode()
        } else if(transfer.recipient.isEmpty()) {
            PrivateKeyEd25519().publicKey().key.toByteArray().v4r2tonAddress(chain.isTestNet)
        } else {
            transfer.recipient
        }
        val recipientAccountDataDeferred =
            async { tonRemoteSource.loadAccountData(chain, normalizedRecipientAccountId) }
        val validUntil = validUntilDeferred.await()
        val recipientAccountData =  recipientAccountDataDeferred.await()

        val sendMode = 3 // todo //if (max && isTon) (TonSendMode.CARRY_ALL_REMAINING_BALANCE.value + TonSendMode.IGNORE_ERRORS.value) else (TonSendMode.PAY_GAS_SEPARATELY.value + TonSendMode.IGNORE_ERRORS.value)

        val walletTransfer = WalletTransfer {
            this.bounceable = isBounce(transfer.recipient, recipientAccountData)
            this.body = body(transfer, senderSmartContract.address)
            this.sendMode = sendMode
            if (transfer.chainAsset.type == ChainAssetType.Jetton) {
                val baseForwardAmountInPlanks = transfer.chainAsset.planksFromAmount(TON_BASE_FORWARD_AMOUNT)
                val jettonWalletAddress = getJettonWalletAddress(senderSmartContract, transfer)
                this.coins = Coins.ofNano(baseForwardAmountInPlanks)
                this.destination = AddrStd.parse(jettonWalletAddress.address)
            } else if(transfer.chainAsset.type == ChainAssetType.Normal) {
                this.coins = Coins.ofNano(transfer.amountInPlanks)
                this.destination = AddrStd(recipientAccountDataDeferred.await().address)
            }
            this.stateInit = stateInit
        }

        return@coroutineScope CellBuilder.createCell {
            storeUInt(senderSmartContract.walletId, 32)
            putTransferParams(seqno, validUntil)
            storeUInt(0, 8)
            storeUInt(sendMode, 8)
            val intMsg = CellRef(createIntMsg(walletTransfer))
            storeRef(MessageRelaxed.tlbCodec(AnyTlbConstructor), intMsg)
        }
    }

    private suspend fun getJettonWalletAddress(senderSmartContract: V4R2WalletContract, transfer: Transfer): AccountAddress {
        val jettons = tonRemoteSource.loadJettonBalances(chain, senderSmartContract.getAccountId(chain.isTestNet))
        val transferringJetton = jettons.balances.find { it.jetton.address == transfer.chainAsset.id }
        return transferringJetton?.walletAddress ?: throw RuntimeException("Cannot find jetton (${transfer.chainAsset.name}) wallet address")
    }

    private fun isBounce(query: String, account: TonAccountData): Boolean {
        if (account.status != AccountStatus.active && query.startsWith("EQ")) {
            return false
        }
        val bounce = query.startsWith("EQ") || !query.startsWith("U")
        if (!query.isValidTonAddress()) {
            return !account.isWallet
        }
        return bounce
    }

    fun String.isValidTonAddress(): Boolean {
        return try {
            AddrStd(this)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun newWalletQueryId(): BigInteger {
        return try {
            val randomBytes = SecureRandom.nextBytes(8)
            val hexString = randomBytes.toHexString(true)
            BigInteger(hexString, 16)
        } catch (e: Throwable) {
            BigInteger.ZERO
        }
    }

    private fun body(transfer: Transfer, senderSmartContractAddrStd: AddrStd): Cell? {
        val jettonTransferOpCode = 0xf8a7ea5
        val randomQueryId = newWalletQueryId()
        val amount = Coins.ofNano(transfer.amountInPlanks)
        val recipient = MsgAddressInt.parse(transfer.recipient)
        val transferParams = transfer.additionalParams as? TonTransferParams

        return if(transfer.chainAsset.type == ChainAssetType.Jetton) {
            val commentPayload = getCommentForwardPayload(transferParams!!.comment!!)

            buildCell {
                storeUInt(jettonTransferOpCode, 32)
                storeUInt(randomQueryId, 64)
                storeTlb(Coins, amount)
                storeTlb(MsgAddressInt, recipient)
                storeTlb(MsgAddressInt, senderSmartContractAddrStd)
                storeBit(false)//customPayload
                storeTlb(Coins, Coins.ofNano(1L))
                storeMaybeRef(commentPayload)
            }
        } else if (transferParams?.comment.isNullOrBlank()) {
            null
        } else {
            getCommentForwardPayload(transferParams!!.comment!!)
        }
    }

    private fun getCommentForwardPayload(
        comment: String?
    ): Cell? {
        return if (comment.isNullOrBlank()) {
            return null
        } else {
            beginCell()
                .storeUInt(0, 32)
                .storeStringTail(comment)
                .endCell()
        }
    }

    private fun String.unicodeToPunycode(): String {
        return try {
            Punycode.encode(this) ?: throw IllegalArgumentException("Invalid punycode")
        } catch (e: Exception) {
            this
        }
    }

    private fun CellBuilder.storeMaybeRef(value: Cell?) = apply {
        if (value == null) {
            storeBit(false)
        } else {
            storeBit(true)
            storeRef(value)
        }
    }

    private fun CellBuilder.storeStringTail(src: String) = apply {
        writeBytes(src.toByteArray(), this)
    }

    private fun writeBytes(src: ByteArray, builder: CellBuilder) {
        if (src.isNotEmpty()) {
            val bytes = floor(builder.availableBits / 8f).toInt()
            if (src.size > bytes) {
                val a = src.copyOfRange(0, bytes)
                val t = src.copyOfRange(bytes, src.size)
                builder.storeBytes(a)
                val bb = beginCell()
                writeBytes(t, bb)
                builder.storeRef(bb.endCell())
            } else {
                builder.storeBytes(src)
            }
        }
    }

    val CellBuilder.availableBits: Int
        get() = 1023 - bits.size

    private fun createIntMsg(transfer: WalletTransfer): MessageRelaxed<Cell> {
        val info = CommonMsgInfoRelaxed.IntMsgInfoRelaxed(
            ihrDisabled = true,
            bounce = transfer.bounceable,
            bounced = false,
            src = AddrNone,
            dest = transfer.destination,
            value = transfer.coins,
            ihrFee = Coins(),
            fwdFee = Coins(),
            createdLt = 0u,
            createdAt = 0u
        )
        val init = Maybe.of(transfer.stateInit?.let {
            Either.of<StateInit, CellRef<StateInit>>(it, null)
        })
        val body = if (transfer.body == null) {
            Either.of<Cell, CellRef<Cell>>(Cell.empty(), null)
        } else {
            Either.of<Cell, CellRef<Cell>>(null, CellRef(transfer.body!!))
        }

        return MessageRelaxed(
            info = info,
            init = init,
            body = body,
        )
    }
}