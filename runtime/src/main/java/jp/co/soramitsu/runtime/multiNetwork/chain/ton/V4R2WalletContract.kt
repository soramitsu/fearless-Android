package jp.co.soramitsu.runtime.multiNetwork.chain.ton

import jp.co.soramitsu.common.utils.toWalletAddress
import jp.co.soramitsu.shared_utils.extensions.fromHex
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.StateInit
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.contract.SmartContract
import org.ton.tlb.CellRef
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class V4R2WalletContract(val publicKey: PublicKeyEd25519) : TonWalletContract() {

    constructor(publicKey: String): this(PublicKeyEd25519.decode(publicKey.fromHex()))
    constructor(publicKey: ByteArray): this(PublicKeyEd25519.decode(publicKey))

    override val maxMessages: Int = 4

    override fun getStateCell(): Cell {
        return CellBuilder.createCell {
            storeUInt(0, 32)
            storeUInt(walletId, 32)
            storeBytes(publicKey.key.toByteArray())
            storeBit(false)
        }
    }

    override fun getCode(): Cell {
        return code
    }

    override fun getAddress(isTestnet: Boolean): String {
        val smartContractAddress = getSmartContractAddress()
        return smartContractAddress.toWalletAddress(isTestnet)
    }

    override fun getAccountId(isTestnet: Boolean): String {
        val smartContractAddress = getSmartContractAddress()
        return smartContractAddress.toString(userFriendly = false, testOnly = isTestnet).lowercase()
    }

    override fun getSmartContractAddress(): AddrStd {
        val v4r2StateCell = getStateCell()
        val stateInit = StateInit(getCode(), v4r2StateCell)
        val stateInitRef = CellRef(stateInit, StateInit)
        val hash = stateInitRef.hash()
        return AddrStd(workchain, hash)
    }

    companion object {
        @OptIn(ExperimentalEncodingApi::class)
        @JvmField
        val code =
            BagOfCells(
                Base64.decode("te6cckECFAEAAtQAART/APSkE/S88sgLAQIBIAIDAgFIBAUE+PKDCNcYINMf0x/THwL4I7vyZO1E0NMf0x/T//QE0VFDuvKhUVG68qIF+QFUEGT5EPKj+AAkpMjLH1JAyx9SMMv/UhD0AMntVPgPAdMHIcAAn2xRkyDXSpbTB9QC+wDoMOAhwAHjACHAAuMAAcADkTDjDQOkyMsfEssfy/8QERITAubQAdDTAyFxsJJfBOAi10nBIJJfBOAC0x8hghBwbHVnvSKCEGRzdHK9sJJfBeAD+kAwIPpEAcjKB8v/ydDtRNCBAUDXIfQEMFyBAQj0Cm+hMbOSXwfgBdM/yCWCEHBsdWe6kjgw4w0DghBkc3RyupJfBuMNBgcCASAICQB4AfoA9AQw+CdvIjBQCqEhvvLgUIIQcGx1Z4MesXCAGFAEywUmzxZY+gIZ9ADLaRfLH1Jgyz8gyYBA+wAGAIpQBIEBCPRZMO1E0IEBQNcgyAHPFvQAye1UAXKwjiOCEGRzdHKDHrFwgBhQBcsFUAPPFiP6AhPLassfyz/JgED7AJJfA+ICASAKCwBZvSQrb2omhAgKBrkPoCGEcNQICEekk30pkQzmkD6f+YN4EoAbeBAUiYcVnzGEAgFYDA0AEbjJftRNDXCx+AA9sp37UTQgQFA1yH0BDACyMoHy//J0AGBAQj0Cm+hMYAIBIA4PABmtznaiaEAga5Drhf/AABmvHfaiaEAQa5DrhY/AAG7SB/oA1NQi+QAFyMoHFcv/ydB3dIAYyMsFywIizxZQBfoCFMtrEszMyXP7AMhAFIEBCPRR8qcCAHCBAQjXGPoA0z/IVCBHgQEI9FHyp4IQbm90ZXB0gBjIywXLAlAGzxZQBPoCFMtqEssfyz/Jc/sAAgBsgQEI1xj6ANM/MFIkgQEI9Fnyp4IQZHN0cnB0gBjIywXLAlAFzxZQA/oCE8tqyx8Syz/Jc/sAAAr0AMntVGliJeU=")).first()
    }
}