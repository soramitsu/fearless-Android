package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.karura

import android.os.Parcelable
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.karura.KaruraContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeSubmitter

class KaruraContributeSubmitter(
    private val interactor: KaruraContributeInteractor
) : CustomContributeSubmitter {

    override suspend fun submit(payload: Parcelable): Result<Unit> {
        TODO("Not yet implemented")
    }
}
