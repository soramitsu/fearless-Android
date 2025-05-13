package jp.co.soramitsu.common.utils

import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.StateInit
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.crypto.hex
import org.ton.tlb.CellRef
import java.math.BigDecimal
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val workchain = 0
private const val DEFAULT_WALLET_ID = 698983191
private const val walletId = DEFAULT_WALLET_ID + workchain
@OptIn(ExperimentalEncodingApi::class)
private val v4r2Code =
    BagOfCells(
        Base64.decode("te6cckECFAEAAtQAART/APSkE/S88sgLAQIBIAIDAgFIBAUE+PKDCNcYINMf0x/THwL4I7vyZO1E0NMf0x/T//QE0VFDuvKhUVG68qIF+QFUEGT5EPKj+AAkpMjLH1JAyx9SMMv/UhD0AMntVPgPAdMHIcAAn2xRkyDXSpbTB9QC+wDoMOAhwAHjACHAAuMAAcADkTDjDQOkyMsfEssfy/8QERITAubQAdDTAyFxsJJfBOAi10nBIJJfBOAC0x8hghBwbHVnvSKCEGRzdHK9sJJfBeAD+kAwIPpEAcjKB8v/ydDtRNCBAUDXIfQEMFyBAQj0Cm+hMbOSXwfgBdM/yCWCEHBsdWe6kjgw4w0DghBkc3RyupJfBuMNBgcCASAICQB4AfoA9AQw+CdvIjBQCqEhvvLgUIIQcGx1Z4MesXCAGFAEywUmzxZY+gIZ9ADLaRfLH1Jgyz8gyYBA+wAGAIpQBIEBCPRZMO1E0IEBQNcgyAHPFvQAye1UAXKwjiOCEGRzdHKDHrFwgBhQBcsFUAPPFiP6AhPLassfyz/JgED7AJJfA+ICASAKCwBZvSQrb2omhAgKBrkPoCGEcNQICEekk30pkQzmkD6f+YN4EoAbeBAUiYcVnzGEAgFYDA0AEbjJftRNDXCx+AA9sp37UTQgQFA1yH0BDACyMoHy//J0AGBAQj0Cm+hMYAIBIA4PABmtznaiaEAga5Drhf/AABmvHfaiaEAQa5DrhY/AAG7SB/oA1NQi+QAFyMoHFcv/ydB3dIAYyMsFywIizxZQBfoCFMtrEszMyXP7AMhAFIEBCPRR8qcCAHCBAQjXGPoA0z/IVCBHgQEI9FHyp4IQbm90ZXB0gBjIywXLAlAGzxZQBPoCFMtqEssfyz/Jc/sAAgBsgQEI1xj6ANM/MFIkgQEI9Fnyp4IQZHN0cnB0gBjIywXLAlAFzxZQA/oCE8tqyx8Syz/Jc/sAAAr0AMntVGliJeU=")).first()

private fun v4r2SmartContractAddress(publicKey: ByteArray): AddrStd {
    val pubkey = PublicKeyEd25519.decode(publicKey)

    val v4r2StateCell = CellBuilder.createCell {
        storeUInt(0, 32)
        storeUInt(walletId, 32)
        storeBytes(pubkey.key.toByteArray())
        storeBit(false)
    }

    val stateInit = StateInit(v4r2Code, v4r2StateCell)
    val stateInitRef = CellRef(stateInit, StateInit)
    val hash = stateInitRef.hash()
    return AddrStd(workchain, hash)
}

fun ByteArray.v4r2tonAddress(isTestnet: Boolean): String {
    val contractAddress = v4r2SmartContractAddress(this)

    return contractAddress.toWalletAddress(isTestnet)
}

// Attention!!! Use the result of this function only with api requests. For internal fearless wallet purposes use tonPublicKey as accountId (MetaAccount.accountId(chain: IChain): ByteArray? function)
fun ByteArray.tonAccountId(isTestnet: Boolean): String {
    val contractAddress = v4r2SmartContractAddress(this)

    return contractAddress.toString(
        userFriendly = false,
        testOnly = isTestnet
    ).lowercase()
}

fun AddrStd.toWalletAddress(testnet: Boolean): String {
    return toString(
        userFriendly = true,
        bounceable = false,
        testOnly = testnet
    )
}

fun CellBuilder.putTransferParams(seqno: Int, validUntil: Long) = apply {
    if (seqno == 0) {
        for (i in 0 until 32) {
            storeBit(true)
        }
    } else {
        storeUInt(validUntil, 32)
    }
    storeUInt(seqno, 32)
}

fun String.toBoc(): BagOfCells {
    return try {
        BagOfCells(hex(this))
    } catch (e: Throwable) {
        BagOfCells(base64())
    }
}

fun String.parseCell(): Cell {
    return toBoc().first()
}

fun String.safeParseCell(): Cell? {
    if (this.isBlank()) {
        return null
    }
    return try {
        parseCell()
    } catch (e: Throwable) {
        null
    }
}

fun String.fixedBase64(): String {
    return this.trim().replace(" ", "+")
}

fun String.base64(): ByteArray {
    return fixedBase64().decodeBase64Bytes()
}

fun Cell.base64(): String {
    return this.toByteArray().encodeBase64()
}

fun Cell.toByteArray(): ByteArray {
    return BagOfCells(this).toByteArray()
}

val TON_BASE_FORWARD_AMOUNT = BigDecimal(0.064)

fun String.toUserFriendly(
    wallet: Boolean = true,
    testnet: Boolean,
    bounceable: Boolean = true,
): String {
    return try {
        val addr = AddrStd(this)
        if (wallet) {
            addr.toWalletAddress(testnet)
        } else {
            addr.toString(userFriendly = true, bounceable = bounceable)
        }
    } catch (e: Exception) {
        this
    }
}
