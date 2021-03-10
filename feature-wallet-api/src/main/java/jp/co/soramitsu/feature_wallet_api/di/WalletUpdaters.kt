package jp.co.soramitsu.feature_wallet_api.di

import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.feature_account_api.domain.updaters.AccountUpdater

class WalletUpdaters(val globalUpdaters: Array<Updater>, val accountUpdaters: Array<AccountUpdater>)