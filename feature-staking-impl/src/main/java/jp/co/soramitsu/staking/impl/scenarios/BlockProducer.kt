package jp.co.soramitsu.staking.impl.scenarios

import android.graphics.drawable.PictureDrawable
import jp.co.soramitsu.common.address.AddressModel

data class BlockProducer(
    val accountIdHex: String,
    val address: String,
    val scoring: Scoring?,
    val title: String,
    val isChecked: Boolean
) {
    sealed class Scoring {
        class OneField(val field: String) : Scoring()

        class TwoFields(val primary: String, val secondary: String?) : Scoring()
    }
}

data class BlockProducerModel(
    val accountIdHex: String,
    val image: PictureDrawable,
    val address: String,
    val scoring: BlockProducer.Scoring?,
    val title: String,
    val isChecked: Boolean?,
    val blockProducer: BlockProducer
)

suspend fun Collection<BlockProducer>.toModels(createIcon: suspend (address: String) -> AddressModel): List<BlockProducerModel> {
    return map { it.toModel(createIcon) }
}

suspend fun BlockProducer.toModel(createIcon: suspend (address: String) -> AddressModel): BlockProducerModel {
    val icon = createIcon(address)
    return BlockProducerModel(
        accountIdHex,
        icon.image,
        address,
        scoring,
        title,
        isChecked,
        this
    )
}
