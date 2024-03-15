## General

# Application classes that will be serialized/deserialized over Gson (Web3j Jackson)
-keep, allowobfuscation class jp.co.soramitsu.nft.data.models.** { *; }
-keep, allowobfuscation class jp.co.soramitsu.nft.impl.domain.utils.** { *;}

#-keep class ** { *; }
#-dontobfuscate
-keep class jp.co.soramitsu.shared_utils.** { *; }
-keep class jp.co.soramitsu.runtime.** { *; }

-keep class jp.co.soramitsu.wallet.impl.data.** { *; }
-keep class jp.co.soramitsu.wallet.impl.domain.** { *; }

-keep class jp.co.soramitsu.wallet.api.data.** { *; }
-keep class jp.co.soramitsu.wallet.api.domain.** { *; }

-keep class jp.co.soramitsu.staking.impl.domain.** { *; }
-keep class jp.co.soramitsu.staking.impl.data.** { *; }

-keep class jp.co.soramitsu.staking.api.domain.** { *; }
-keep class jp.co.soramitsu.staking.api.data.** { *; }

-keep class jp.co.soramitsu.account.api.data.** { *; }
-keep class jp.co.soramitsu.account.api.domain.** { *; }

-keep class jp.co.soramitsu.account.impl.data.** { *; }
-keep class jp.co.soramitsu.account.impl.domain.** { *; }

-keep class jp.co.soramitsu.common.data.** { *; }
-keep class jp.co.soramitsu.common.domain.** { *; }

-keep class jp.co.soramitsu.crowdloan.api.data.** { *; }

-keep class jp.co.soramitsu.crowdloan.impl.data.** { *; }
-keep class jp.co.soramitsu.crowdloan.impl.domain.** { *; }

-keep class jp.co.soramitsu.core_db.** { *; }
-keep class jp.co.soramitsu.coredb.** { *; }
-keep class jp.co.soramitsu.core.** { *; }

-keep class net.jpountz.** { *; }
-keep class org.web3j.** { *; }

# Jackson (serializer used in web3j lib) tries to get Enum classes from the whole application (not only its packages)
# by doing so it tries to find Enums that are defined inside its packages :/
# So, if this keep rule is not enabled, R8 removes (obfucates, etc..) all application enums to its liking
# leaving Jackson unaware of it is looking for, and thus an exception will be thrown
# Actual exception is: ExceptionInInitializerError(), but it happens in static code of Enum classes
# while searching for the needed Jackson defined enum
-keepclassmembers,allowoptimization enum com.fasterxml.jackson.databind.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

-keep class jp.co.soramitsu.backup.** { *; }
-keep class com.google.api.** { *; }
-keep class io.opencensus.trace.** { *; }
#for beacon sdk

-keep class it.airgap.beaconsdk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class it.airgap.beaconsdk.**$$serializer { *; }
-keepclassmembers class it.airgap.beaconsdk.** {
    *** Companion;
}
-keepclasseswithmembers class it.airgap.beaconsdk.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-dontwarn java.awt.*

# Google Drive and Signin
# Needed to keep generic types and @Key annotations accessed via reflection
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault

-keepclassmembers class * {
  @com.google.api.client.util.Key <fields>;
}

# Needed by google-http-client-android when linking against an older platform version

-dontwarn com.google.api.client.extensions.android.**

# Needed by google-api-client-android when linking against an older platform version

-dontwarn com.google.api.client.googleapis.extensions.android.**

# Needed by google-play-services when linking against an older platform version

-dontwarn com.google.android.gms.**

# This is generated automatically by the Android Gradle plugin.
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.impl.StaticMDCBinder
-dontwarn org.w3c.dom.events.DocumentEvent
-dontwarn org.w3c.dom.events.Event
-dontwarn org.w3c.dom.events.EventException
-dontwarn org.w3c.dom.events.EventListener
-dontwarn org.w3c.dom.events.EventTarget
-dontwarn org.w3c.dom.events.MutationEvent
-dontwarn org.w3c.dom.ls.LSSerializerFilter
-dontwarn org.w3c.dom.ranges.DocumentRange
-dontwarn org.w3c.dom.ranges.Range
-dontwarn org.w3c.dom.traversal.DocumentTraversal
-dontwarn org.w3c.dom.traversal.NodeFilter
-dontwarn org.w3c.dom.traversal.NodeIterator

-dontwarn org.jetbrains.kotlin.compiler.plugin.CliOption
-dontwarn org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
-dontwarn org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
-dontwarn org.jetbrains.kotlin.diagnostics.DiagnosticFactory0
-dontwarn org.jetbrains.kotlin.diagnostics.DiagnosticFactory1
-dontwarn org.jetbrains.kotlin.diagnostics.DiagnosticFactory2
-dontwarn org.jetbrains.kotlin.diagnostics.DiagnosticFactory3
-dontwarn org.jetbrains.kotlin.diagnostics.Errors$Initializer
-dontwarn org.jetbrains.kotlin.diagnostics.PositioningStrategies
-dontwarn org.jetbrains.kotlin.diagnostics.PositioningStrategy
-dontwarn org.jetbrains.kotlin.diagnostics.Severity
-dontwarn org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
-dontwarn org.jetbrains.kotlin.diagnostics.rendering.ContextIndependentParameterRenderer
-dontwarn org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages$Extension
-dontwarn org.jetbrains.kotlin.diagnostics.rendering.DiagnosticFactoryToRendererMap
-dontwarn org.jetbrains.kotlin.diagnostics.rendering.DiagnosticParameterRenderer
-dontwarn org.jetbrains.kotlin.diagnostics.rendering.Renderers
-dontwarn org.jetbrains.kotlin.diagnostics.rendering.SmartDescriptorRenderer
-dontwarn org.jetbrains.kotlin.diagnostics.rendering.SmartTypeRenderer

-dontwarn javax.naming.InvalidNameException
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid

# Retrofit does reflection on generic parameters. InnerClasses is required to use Signature and
# EnclosingMethod is required to use InnerClasses.
-keepattributes Signature, InnerClasses, EnclosingMethod

# Retrofit does reflection on method and parameter annotations.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Keep annotation default values (e.g., retrofit2.http.Field.encoded).
-keepattributes AnnotationDefault

# Retain service method parameters when optimizing.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Ignore annotation used for build tooling.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Ignore JSR 305 annotations for embedding nullability information.
-dontwarn javax.annotation.**

# Guarded by a NoClassDefFoundError try/catch and only used when on the classpath.
-dontwarn kotlin.Unit

# Top-level functions that can only be used by Kotlin.
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# With R8 full mode, it sees no subtypes of Retrofit interfaces since they are created with a Proxy
# and replaces all potential values with null. Explicitly keeping the interfaces prevents this.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Keep inherited services.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>

# With R8 full mode generic signatures are stripped for classes that are not
# kept. Suspend functions are wrapped in continuations where the type argument
# is used.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# R8 full mode strips generic signatures from return types if not kept.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# With R8 full mode generic signatures are stripped for classes that are not kept.
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-dontwarn sun.misc.**
#-keep class com.google.gson.stream.** { *; }

# Application classes that will be serialized/deserialized over Gson
-keep class com.google.gson.examples.android.model.** { <fields>; }

# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Prevent R8 from leaving Data object members always null
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

-keepclassmembers class * {
  @com.google.api.client.util.Key <fields>;
}