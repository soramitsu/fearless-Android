package jp.co.soramitsu.common.data.network.coingecko

import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet

typealias FiatChooserEvent = Event<DynamicListBottomSheet.Payload<FiatCurrency>>

data class FiatCurrency(val id: String, val symbol: String, val name: String, val icon: String)
