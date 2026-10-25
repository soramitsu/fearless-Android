package jp.co.soramitsu.fearless_utils.encrypt.mnemonic

import com.google.gson.annotations.SerializedName

data class MnemonicTestCase(
    val mnemonic: String,
    val path: String,
    @SerializedName("pk")
    val expectedPublicKey: String,
)