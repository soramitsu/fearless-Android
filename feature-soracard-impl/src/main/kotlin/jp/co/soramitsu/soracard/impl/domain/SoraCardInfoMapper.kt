package jp.co.soramitsu.soracard.impl.domain

import jp.co.soramitsu.coredb.model.SoraCardInfoLocal
import jp.co.soramitsu.soracard.api.presentation.models.SoraCardInfo

object SoraCardInfoMapper {

    fun map(infoLocal: SoraCardInfoLocal): SoraCardInfo =
        SoraCardInfo(
            id = infoLocal.id,
            accessToken = infoLocal.accessToken,
            refreshToken = infoLocal.refreshToken,
            accessTokenExpirationTime = infoLocal.accessTokenExpirationTime,
            kycStatus = infoLocal.kycStatus
        )
}
