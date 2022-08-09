package jp.co.soramitsu.featurestakingimpl.data.network.blockhain.bindings

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.HelperBinding
import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.common.data.network.runtime.binding.requireType
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.featurestakingapi.domain.model.Exposure
import jp.co.soramitsu.featurestakingapi.domain.model.IndividualExposure

/*
IndividualExposure: {
  who: AccountId; // account id of the nominator
  value: Compact<Balance>; // nominator’s stake
}
 */
@HelperBinding
fun bindIndividualExposure(dynamicInstance: Any?, runtime: RuntimeSnapshot): IndividualExposure {
    requireType<Struct.Instance>(dynamicInstance)

    val who = dynamicInstance.get<ByteArray>("who") ?: incompatible()
    val value = dynamicInstance.get<BigInteger>("value") ?: incompatible()

    return IndividualExposure(who, value)
}

/*
 Exposure: {
  total: Compact<Balance>; // total stake of the validator
  own: Compact<Balance>; // own stake of the validator
  others: Vec<IndividualExposure>; // nominators stakes
}
 */
@UseCaseBinding
fun bindExposure(scale: String, runtime: RuntimeSnapshot, type: Type<*>): Exposure? {
    val decoded = type.fromHexOrNull(runtime, scale) as? Struct.Instance ?: return null

    val total = decoded.get<BigInteger>("total") ?: return null
    val own = decoded.get<BigInteger>("own") ?: return null

    val others = decoded.get<List<*>>("others")?.map { bindIndividualExposure(it, runtime) } ?: return null

    return Exposure(total, own, others)
}
