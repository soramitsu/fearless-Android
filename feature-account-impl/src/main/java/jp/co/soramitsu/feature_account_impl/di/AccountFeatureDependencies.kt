package jp.co.soramitsu.feature_account_impl.di

import jp.co.soramitsu.common.data.network.AppLinksProvider

interface AccountFeatureDependencies {

    fun appLinksProvider(): AppLinksProvider
}