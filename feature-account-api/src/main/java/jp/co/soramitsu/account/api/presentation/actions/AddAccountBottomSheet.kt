package jp.co.soramitsu.account.api.presentation.actions

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.textItem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.parcelize.Parcelize

class AddAccountBottomSheet(
    context: Context,
    private val payload: Payload,
    private val onCreate: (chainId: ChainId, metaId: Long) -> Unit,
    private val onImport: (chainId: ChainId, metaId: Long) -> Unit,
    private val onNoNeed: (chainId: ChainId, metaId: Long, assetId: String, priceId: String?) -> Unit
) : FixedListBottomSheet(context) {

    @Parcelize
    data class Payload(
        val metaId: Long,
        val chainId: ChainId,
        val chainName: String,
        val assetId: String,
        val priceId: String?,
        val markedAsNotNeed: Boolean
    ) : Parcelable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(context.getString(R.string.account_template, payload.chainName))

        textItem(R.string.create_new_account) {
            onCreate(payload.chainId, payload.metaId)
        }

        textItem(R.string.already_have_account) {
            onImport(payload.chainId, payload.metaId)
        }

        if (!payload.markedAsNotNeed) {
            textItem(R.string.i_dont_need_account) {
                onNoNeed(payload.chainId, payload.metaId, payload.assetId, payload.priceId)
            }
        }
    }
}
