package jp.co.soramitsu.feature_wallet_api.di

import jp.co.soramitsu.core.updater.ScopedUpdater
import jp.co.soramitsu.core.updater.Updater

class WalletUpdaters(val globalUpdaters: Array<Updater>, val accountUpdaters: Array<ScopedUpdater<String>>)