## General
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
-dontwarn androidx.camera.extensions.impl.InitializerImpl$OnExtensionsDeinitializedCallback
-dontwarn androidx.camera.extensions.impl.InitializerImpl$OnExtensionsInitializedCallback
-dontwarn lombok.NonNull
-dontwarn okhttp3.internal.Util
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
-dontwarn org.slf4j.impl.StaticLoggerBinder
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
-dontwarn org.webrtc.Dav1dDecoder
