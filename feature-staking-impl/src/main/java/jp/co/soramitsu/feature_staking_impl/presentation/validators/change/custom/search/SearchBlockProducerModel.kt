package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.search

import android.graphics.drawable.PictureDrawable

data class SearchBlockProducerModel(val name: String, val address: String, val selected: Boolean, val rewardsPercent: String, val image: PictureDrawable)
