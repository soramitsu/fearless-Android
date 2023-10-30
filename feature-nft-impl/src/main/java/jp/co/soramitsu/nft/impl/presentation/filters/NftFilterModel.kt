package jp.co.soramitsu.nft.impl.presentation.filters

import android.os.Bundle
import androidx.core.os.bundleOf

data class NftFilterModel(
    val items: Map<NftFilter, Boolean>
) {

    companion object {
        fun fromBundle(bundle: Bundle): NftFilterModel =
            NftFilterModel(NftFilter.values().associateWith { bundle.getBoolean(it.name) })
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

enum class NftFilter {
    Spam, Airdrop
}