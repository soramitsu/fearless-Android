package jp.co.soramitsu.nft.impl.presentation.filters

import android.os.Bundle
import androidx.core.os.bundleOf
import jp.co.soramitsu.nft.domain.models.NFTFilter

data class NftFilterModel(
    val items: Map<NFTFilter, Boolean>
) {

    companion object {
        fun fromBundle(bundle: Bundle): NftFilterModel =
            NftFilterModel(NFTFilter.values().associateWith { bundle.getBoolean(it.name) })
    }

    val bundle: Bundle
        get() {
            val bundle = bundleOf()
            items.entries.forEach {
                bundle.putBoolean(it.key.name, it.value)
            }
            return bundle
        }
}

