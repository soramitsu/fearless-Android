package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.extrinsics

import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.feature_wallet_impl.data.network.struct.ExtrinsicPayloadValue

fun Signer.signExtrinsic(payload: EncodableStruct<ExtrinsicPayloadValue>,
    keyPair: Keypair,
    encryptionType: EncryptionType): ByteArray {
    val message = ExtrinsicPayloadValue.toByteArray(payload)

    return sign(encryptionType, message, keyPair).signature
}