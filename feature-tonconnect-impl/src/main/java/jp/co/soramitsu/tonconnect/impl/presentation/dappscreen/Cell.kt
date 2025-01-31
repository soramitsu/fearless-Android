package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import io.ktor.util.encodeBase64
import org.ton.boc.BagOfCells
import org.ton.cell.Cell

fun Cell.toByteArray(): ByteArray {
    return BagOfCells(this).toByteArray()
}

fun Cell.base64(): String {
    return toByteArray().encodeBase64()
}
