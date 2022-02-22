package jp.co.soramitsu.common.utils

import android.widget.EditText
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.LiveDataScope
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import kotlinx.coroutines.flow.Flow

fun MutableLiveData<Event<Unit>>.sendEvent() {
    this.value = Event(Unit)
}

fun <FROM, TO> LiveData<FROM>.map(mapper: (FROM) -> TO): LiveData<TO> {
    return map(null, mapper)
}

fun <FROM, TO> LiveData<FROM>.map(initial: TO?, mapper: (FROM) -> TO): LiveData<TO> {
    return MediatorLiveData<TO>().apply {
        addSource(this@map) {
            value = mapper.invoke(it)
        }

        initial?.let(::setValue)
    }
}

fun <T> mediatorLiveData(mediatorBuilder: MediatorLiveData<T>.() -> Unit): MediatorLiveData<T> {
    val liveData = MediatorLiveData<T>()

    mediatorBuilder.invoke(liveData)

    return liveData
}

fun <T> multipleSourceLiveData(vararg sources: LiveData<T>): MediatorLiveData<T> = mediatorLiveData {
    for (source in sources) {
        updateFrom(source)
    }
}

fun <T> MediatorLiveData<T>.updateFrom(other: LiveData<T>) = addSource(other) {
    value = it
}

/**
 * Supports up to N sources, where N is last componentN() in ComponentHolder
 * @see ComponentHolder
 */
fun <R> combine(
    vararg sources: LiveData<*>,
    combiner: (ComponentHolder) -> R
): LiveData<R> {
    return MediatorLiveData<R>().apply {
        for (source in sources) {
            addSource(source) {
                val values = sources.map { it.value }

                val nonNull = values.filterNotNull()

                if (nonNull.size == values.size) {
                    value = combiner.invoke(ComponentHolder(nonNull))
                }
            }
        }
    }
}

/**
 * Supports up to N sources, where N is last componentN() in ComponentHolder
 * @see ComponentHolder
 */
fun <R> mediateWith(
    vararg sources: LiveData<*>,
    combiner: (ComponentHolder) -> R?
): LiveData<R> {
    return MediatorLiveData<R>().apply {
        var isInitialized = false

        fun handleChanges() {
            combiner.invoke(ComponentHolder(sources.map { it.value }))?.let { newValue ->
                value = newValue
            }
        }

        for (source in sources) {
            addSource(source) {
                if (isInitialized) {
                    handleChanges()
                }
            }
        }

        isInitialized = true
        handleChanges()
    }
}

fun <FIRST, SECOND, RESULT> LiveData<FIRST>.combine(
    another: LiveData<SECOND>,
    initial: RESULT? = null,
    zipper: (FIRST, SECOND) -> RESULT
): LiveData<RESULT> {

    return MediatorLiveData<RESULT>().apply {
        addSource(this@combine) { first ->
            val second = another.value

            if (first != null && second != null) {
                value = zipper.invoke(first, second)
            }
        }

        addSource(another) { second ->
            val first = this@combine.value

            if (first != null && second != null) {
                value = zipper.invoke(first, second)
            }
        }

        initial?.let { value = it }
    }
}

fun <FROM, TO> LiveData<FROM>.switchMap(
    mapper: (FROM) -> LiveData<TO>
) = switchMap(mapper, true)

fun <FROM, TO> LiveData<FROM>.switchMap(
    mapper: (FROM) -> LiveData<TO>,
    triggerOnSwitch: Boolean
): LiveData<TO> {
    val result: MediatorLiveData<TO> = MediatorLiveData()

    result.addSource(
        this,
        object : Observer<FROM> {
            var mSource: LiveData<TO>? = null

            override fun onChanged(x: FROM) {
                val newLiveData: LiveData<TO> = mapper.invoke(x)

                if (mSource === newLiveData) {
                    return
                }
                if (mSource != null) {
                    result.removeSource(mSource!!)
                }

                mSource = newLiveData

                if (mSource != null) {
                    result.addSource(mSource!!) { y -> result.setValue(y) }

                    if (triggerOnSwitch && mSource!!.value != null) {
                        mSource!!.notifyObservers()
                    }
                }
            }
        }
    )

    return result
}

fun <T> LiveData<T>.distinctUntilChanged() = Transformations.distinctUntilChanged(this)

fun EditText.bindTo(liveData: MutableLiveData<String>, lifecycleOwner: LifecycleOwner) {
    onTextChanged {
        if (liveData.value != it) {
            liveData.value = it
        }
    }

    liveData.observe(
        lifecycleOwner,
        Observer {
            if (it != text.toString()) {
                setText(it)
            }
        }
    )
}

fun LiveData<String>.isNotEmpty() = !value.isNullOrEmpty()

fun <T> LiveData<T>.notifyObservers() {
    (this as MutableLiveData<T>).value = value
}

suspend fun <T> LiveDataScope<T>.emitAll(flow: Flow<T>) = flow.collect { emit(it) }
