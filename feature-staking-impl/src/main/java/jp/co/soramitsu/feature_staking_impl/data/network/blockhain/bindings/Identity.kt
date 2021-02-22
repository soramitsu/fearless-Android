package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.HelperBinding
import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.feature_staking_api.domain.model.Identity

/*
Registration: {
  judgements: Vec<Judgement>;
  deposit: Balance;
  info: IdentityInfo;
}

IdentityInfo: {
  additional: Vec<IdentityInfoAdditional>;
  display: Data;
  legal: Data;
  web: Data;
  riot: Data;
  email: Data;
  pgpFingerprint: Option<H160>;
  image: Data;
  twitter: Data;
}
 */

@UseCaseBinding
fun bindIdentity(
    scale: String,
    runtime: RuntimeSnapshot,
    type: Type<*>
): Identity {
    val decoded = type.fromHexOrNull(runtime, scale) as? Struct.Instance ?: incompatible()

    val identityInfo = decoded.get<Struct.Instance>("info") ?: incompatible()

    val pgpFingerprint = identityInfo.get<ByteArray?>("pgpFingerprint")

    return Identity(
        display = bindIdentityData(identityInfo, "display"),
        legal = bindIdentityData(identityInfo, "legal"),
        web = bindIdentityData(identityInfo, "web"),
        riot = bindIdentityData(identityInfo, "riot"),
        email = bindIdentityData(identityInfo, "email"),
        pgpFingerprint = pgpFingerprint?.toHexString(withPrefix = true),
        image = bindIdentityData(identityInfo, "image"),
        twitter = bindIdentityData(identityInfo, "twitter")
    )
}

@HelperBinding
fun bindIdentityData(identityInfo: Struct.Instance, field: String): String? {
    val value = identityInfo.get<Any?>(field) ?: incompatible()

    return bindData(value).asString()
}