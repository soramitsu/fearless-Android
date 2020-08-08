package jp.co.soramitsu.feature_account_api.domain.interfaces

import io.reactivex.Single

interface AccountInteractor {

    fun getMnemonic(): Single<List<String>> {
        return Single.fromCallable {
            mutableListOf<String>().apply {
                add("song")
                add("toss")
                add("odor")
                add("click")
                add("blouse")
                add("lesson")
                add("runway")
                add("popular")
                add("owner")
                add("caught")
                add("wrist")
                add("poverty")
            }
        }
    }
}