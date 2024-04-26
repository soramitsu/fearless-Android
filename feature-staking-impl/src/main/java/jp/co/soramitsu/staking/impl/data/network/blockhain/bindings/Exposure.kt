package jp.co.soramitsu.staking.impl.data.network.blockhain.bindings

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.HelperBinding
import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.common.data.network.runtime.binding.requireType
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.definitions.types.Type
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.shared_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.staking.api.domain.model.Exposure
import jp.co.soramitsu.staking.api.domain.model.ExposurePage
import jp.co.soramitsu.staking.api.domain.model.IndividualExposure
import jp.co.soramitsu.staking.api.domain.model.LegacyExposure

@HelperBinding
fun bindIndividualExposure(dynamicInstance: Any?): IndividualExposure {
    requireType<Struct.Instance>(dynamicInstance)

    val who = dynamicInstance.get<ByteArray>("who") ?: incompatible()
    val value = dynamicInstance.get<BigInteger>("value") ?: incompatible()

    return IndividualExposure(who, value)
}

@UseCaseBinding
fun bindLegacyExposure(scale: String, runtime: RuntimeSnapshot, type: Type<*>): LegacyExposure? {
    val decoded = type.fromHexOrNull(runtime, scale) as? Struct.Instance ?: return null

    val total = decoded.get<BigInteger>("total") ?: return null
    val own = decoded.get<BigInteger>("own") ?: return null

    val others = decoded.get<List<*>>("others")?.map { bindIndividualExposure(it) } ?: return null

    return LegacyExposure(total, own, others)
}

@UseCaseBinding
fun bindExposure(scale: String, runtime: RuntimeSnapshot): Exposure? {
    val storageType = runtime.metadata.staking().storage("ErasStakersOverview").returnType()

    val decoded = storageType.fromHexOrNull(runtime, scale) as? Struct.Instance ?: return null

    val total = decoded.get<BigInteger>("total") ?: return null
    val own = decoded.get<BigInteger>("own") ?: return null

    val nominatorCount = decoded.get<BigInteger>("nominatorCount") ?: return null
    val pageCount = decoded.get<BigInteger>("pageCount") ?: return null

    return Exposure(total, own, nominatorCount.toInt(), pageCount)
}

@UseCaseBinding
fun bindExposurePage(scale: String, runtime: RuntimeSnapshot): ExposurePage? {
    return runCatching {
        val storageType = runtime.metadata.staking().storage("ErasStakersPaged").returnType()
        val decoded = storageType.fromHexOrNull(runtime, scale) as? Struct.Instance ?: return null
        val pageTotal = decoded.get<BigInteger>("pageTotal") ?: return null
        val others =
            decoded.get<List<*>>("others")?.map { bindIndividualExposure(it) } ?: return null
        ExposurePage(pageTotal, others)
    }.getOrNull()
}