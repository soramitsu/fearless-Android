package jp.co.soramitsu.app.root.presentation.main.extrinsic_builder

import android.text.InputType
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.app.root.presentation.main.extrinsic_builder.CategoryChooser.CategoryChooserPayload
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.CollectionEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Vec
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Null
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.OpaqueCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.BooleanType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.DynamicByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.NumberType
import jp.co.soramitsu.fearless_utils.runtime.metadata.Function
import jp.co.soramitsu.fearless_utils.runtime.metadata.FunctionArgument
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

private fun <T> mutableShareFlow() = MutableSharedFlow<T>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

class ArgumentsState(buildingContext: ExtrinsicBuilderContext, call: Function) {

    val argumentStates = call.arguments.associateBy(
        keySelector = FunctionArgument::name,
        valueTransform = { mapArgumentToArgumentState(buildingContext, it) }
    )

    suspend fun collect(): Map<String, Any?> {
        return argumentStates.mapValues { (name, state) -> state.collect() }
    }
}

sealed class ArgumentState<T>(val name: String) {

    abstract suspend fun collect(): T

    class BoolState(name: String) : ArgumentState<Boolean>(name) {

        val checked = MutableLiveData(false)

        override suspend fun collect(): Boolean {
            return checked.value!!
        }
    }

    class PrimitiveState<T>(
        name: String,
        val inputType: Int,
        val converter: suspend (String) -> T
    ) : ArgumentState<T>(name) {

        val stringValue = MutableLiveData("")

        override suspend fun collect(): T {
            return converter(stringValue.value!!)
        }
    }

    class UnknownState(name: String) : ArgumentState<Nothing>(name) {

        override suspend fun collect(): Nothing {
            throw IllegalArgumentException("Type for ${name} is unknown")
        }
    }

    class DictEnumState(
        name: String,
        val type: DictEnum,
        private val buildingContext: ExtrinsicBuilderContext,
    ) : ArgumentState<DictEnum.Entry<Any?>>(name) {

        val allOptions = type.elements.map(DictEnum.Entry<TypeReference>::name)

        val selectedOption = mutableShareFlow<String>().also {
            buildingContext.coroutineScope.launch {
                it.emit(allOptions.first())
            }
        }

        val selectedOptionState = selectedOption.map {
            mapTypeToState(buildingContext, it, type[it]!!)
        }.shareIn(buildingContext.coroutineScope, started = SharingStarted.Eagerly, replay = 1)

        fun optionSelectorClicked() {
            val payload = CategoryChooserPayload(name, allOptions) {
                buildingContext.coroutineScope.launch { selectedOption.emit(it) }
            }

            buildingContext.categoryChooserEvent.value = Event(payload)
        }

        override suspend fun collect(): DictEnum.Entry<Any?> {
            return DictEnum.Entry(selectedOption.first(), selectedOptionState.first().collect())
        }
    }

    class CollectionEnumState(
        name: String,
        type: CollectionEnum,
        private val buildingContext: ExtrinsicBuilderContext,
    ) : ArgumentState<String>(name) {

        val allOptions = type.elements

        val selectedOption = mutableShareFlow<String>().also {
            buildingContext.coroutineScope.launch {
                it.emit(allOptions.first())
            }
        }

        fun optionSelectorClicked() {
            val payload = CategoryChooserPayload(name, allOptions) {
                buildingContext.coroutineScope.launch { selectedOption.emit(it) }
            }

            buildingContext.categoryChooserEvent.value = Event(payload)
        }

        override suspend fun collect(): String {
            return selectedOption.first()
        }
    }

    object NullState : ArgumentState<Any?>("Null") {

        override suspend fun collect(): Any? {
            return null
        }
    }

    class ListState(
        name: String,
        val type: Vec,
        private val buildingContext: ExtrinsicBuilderContext,
    ) : ArgumentState<List<Any?>>(name) {
        val newElementEvent = MutableLiveData<Event<ArgumentState<*>>>()
        val deletedElementEvent = MutableLiveData<Event<Int>>()

        val elements = mutableListOf<ArgumentState<*>>()

        init {
            add()
        }

        fun add() {
            val newElement = createElement()
            elements.add(newElement)

            newElementEvent.value = Event(newElement)
        }

        fun remove(elementState: ArgumentState<*>) {
            val index = elements.indexOf(elementState)

            elements.removeAt(index)

            deletedElementEvent.value = Event(index)
        }

        private fun createElement(): ArgumentState<*> {
            val innerType = type.innerType!!

            return mapTypeToState(buildingContext, innerType.name, innerType)
        }

        override suspend fun collect(): List<Any?> {
            return elements.map { it.collect() }
        }
    }

    class StructState(
        name: String,
        type: Struct,
        private val buildingContext: ExtrinsicBuilderContext,
    ) : ArgumentState<Map<String, Any?>>(name) {

        val elements = type.mapping.mapValues { (name, _) ->
            mapTypeToState(buildingContext, name, type[name]!!)
        }

        override suspend fun collect(): Map<String, Any?> {
            return elements.mapValues { (_, state) -> state.collect() }
        }
    }

    class CallState(
        name: String,
        val buildingContext: ExtrinsicBuilderContext
    ) : ArgumentState<GenericCall.Instance>(name), CoroutineScope by buildingContext.coroutineScope {

        val selectedModuleName = mutableShareFlow<String>().also {
            launch { it.emit(buildingContext.interactor.modules().first()) }
        }

        val selectedCallName = mutableShareFlow<String>().also { selectedCall ->
            launch {
                selectedModuleName.collect { module ->
                    selectedCall.emit(buildingContext.interactor.calls(module).first())
                }
            }
        }

        val callArgumentsState = selectedModuleName.combine(selectedCallName) { moduleName, callName ->
            ArgumentsState(buildingContext, buildingContext.interactor.call(moduleName, callName))
        }.shareIn(this, SharingStarted.Eagerly, replay = 1)


        fun callClicked() {
            launch {
                val module = selectedModuleName.first()
                val calls = buildingContext.interactor.calls(module)

                buildingContext.categoryChooserEvent.value = Event(CategoryChooserPayload(module, calls, ::callChosen))
            }
        }

        fun moduleClicked() {
            launch {
                val modules = buildingContext.interactor.modules()

                buildingContext.categoryChooserEvent.value = Event(CategoryChooserPayload("Module", modules, ::moduleChosen))
            }
        }

        private fun callChosen(call: String) = launch {
            selectedCallName.emit(call)
        }

        private fun moduleChosen(module: String) = launch {
            selectedModuleName.emit(module)
        }

        override suspend fun collect(): GenericCall.Instance {
            val moduleName = selectedModuleName.first()
            val callName = selectedCallName.first()

            return buildingContext.interactor.callInstance(moduleName, callName, callArgumentsState.first().collect())
        }
    }
}

fun mapTypeToState(
    buildingContext: ExtrinsicBuilderContext,
    argName: String,
    type: Type<*>,
) = when (type) {
    BooleanType -> ArgumentState.BoolState(argName)
    is NumberType -> ArgumentState.PrimitiveState(
        argName,
        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
        { buildingContext.currentAssetFlow.first().token.planksFromAmount(BigDecimal(it)) }
    )
    is FixedByteArray, is DynamicByteArray -> ArgumentState.PrimitiveState(argName + " (in hex)", InputType.TYPE_CLASS_TEXT) { it.fromHex() }
    is DictEnum -> ArgumentState.DictEnumState(argName, type, buildingContext)
    is CollectionEnum -> ArgumentState.CollectionEnumState(argName, type, buildingContext)
    is Struct -> ArgumentState.StructState(argName, type, buildingContext)
    is Null -> ArgumentState.NullState
    is Vec -> ArgumentState.ListState(argName, type, buildingContext)
    is OpaqueCall, is GenericCall -> ArgumentState.CallState(argName, buildingContext)
    else -> ArgumentState.UnknownState(argName)
}

fun mapArgumentToArgumentState(buildingContext: ExtrinsicBuilderContext, argument: FunctionArgument): ArgumentState<*> {
    return mapTypeToState(buildingContext, argument.name, argument.type!!)
}

class ExtrinsicBuilderContext(
    val interactor: ExtrinsicBuilderInteractor,
    val categoryChooserEvent: MutableLiveData<Event<CategoryChooserPayload>>,
    val coroutineScope: CoroutineScope,
    val currentAssetFlow: Flow<Asset>,
)

class ExtrinsicBuilderViewModel(
    private val interactor: ExtrinsicBuilderInteractor,
) : BaseViewModel() {

    val categoryChooserEvent = MutableLiveData<Event<CategoryChooserPayload>>()

    val currentAssetFlow = interactor.currentAssetFlow().share()

    private val buildingContext = ExtrinsicBuilderContext(interactor, categoryChooserEvent, viewModelScope, currentAssetFlow)

    val callState = ArgumentState.CallState("Build extrinsic", buildingContext)

    fun send() = viewModelScope.launch {
        val result = runCatching {
            val call = callState.collect()

            interactor.send(call)
        }

        if (result.isSuccess) {
            showMessage("Extrinsic with hash ${result.requireValue()} was sent")
        } else {
            showError(result.requireException())
        }
    }


}
