package jp.co.soramitsu.fearless_utils.scale.utils

import io.emeraldpay.polkaj.scale.ScaleCodecWriter

fun ScaleCodecWriter.directWrite(byteArray: ByteArray) {
    directWrite(byteArray, 0, byteArray.size)
}
