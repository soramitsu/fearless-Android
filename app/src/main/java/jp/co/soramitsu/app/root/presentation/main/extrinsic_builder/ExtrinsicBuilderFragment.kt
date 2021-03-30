package jp.co.soramitsu.app.root.presentation.main.extrinsic_builder

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.switchmaterial.SwitchMaterial
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.di.RootApi
import jp.co.soramitsu.app.root.di.RootComponent
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.getDrawableCompat
import jp.co.soramitsu.common.utils.inflateChildTyped
import jp.co.soramitsu.common.view.InputField
import jp.co.soramitsu.common.view.LabeledTextView
import kotlinx.android.synthetic.main.fragment_extrinsic_builder.argumentsContainer
import kotlinx.android.synthetic.main.fragment_extrinsic_builder.callChooser
import kotlinx.android.synthetic.main.fragment_extrinsic_builder.extrinsicBuilderContainer
import kotlinx.android.synthetic.main.fragment_extrinsic_builder.extrinsicBuilderSend
import kotlinx.android.synthetic.main.fragment_extrinsic_builder.moduleChooser

class ExtrinsicBuilderFragment : BaseFragment<ExtrinsicBuilderViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_extrinsic_builder, container, false)
    }

    override fun initViews() {
        callChooser.setWholeClickListener { viewModel.callClicked() }
        moduleChooser.setWholeClickListener { viewModel.moduleClicked() }

        extrinsicBuilderContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<RootComponent>(this, RootApi::class.java)
            .extrinsicBuilderFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ExtrinsicBuilderViewModel) {
        viewModel.selectedCallName.observe {
            callChooser.setMessage(it)
        }

        viewModel.selectedModuleName.observe {
            moduleChooser.setMessage(it)
        }

        viewModel.categoryChooserEvent.observeEvent {
            CategoryChooser(requireContext(), it).show()
        }

        viewModel.argumentsState.observe { argumentsState ->
            argumentsContainer.removeAllViews()

            argumentsState.argumentStates.forEach { (_, state) ->
                createArgumentView(state)?.let(argumentsContainer::addView)
            }
        }

        extrinsicBuilderSend.setOnClickListener { viewModel.send() }
    }

    private fun createArgumentView(argumentState: ArgumentState<*>): View? {
        return when (argumentState) {
            is ArgumentState.BoolState -> SwitchMaterial(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)

                text = argumentState.name

                bindTo(argumentState.checked, viewLifecycleOwner)
            }
            is ArgumentState.PrimitiveState -> argumentsContainer.inflateChildTyped<InputField>(R.layout.field_input).apply {
                hint = argumentState.name

                content.inputType = argumentState.inputType

                content.bindTo(argumentState.stringValue)
            }

            is ArgumentState.DictEnumState -> LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = LinearLayout.VERTICAL

                addView(LabeledTextView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                        setMargins(0, 16.dp, 0, 0)
                    }

                    setLabel(argumentState.name)

                    argumentState.selectedOption.observe {
                        setMessage(it)
                    }

                    setActionIcon(context.getDrawableCompat(R.drawable.ic_pin_white_24))

                    setWholeClickListener { argumentState.optionSelectorClicked() }
                })

                argumentState.selectedOptionState.observe {
                    removeViews(1, childCount - 1)

                    createArgumentView(it)?.let(::addView)
                }
            }

            is ArgumentState.ListState -> LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = LinearLayout.VERTICAL

                addView(TextView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                        setMargins(0, 16.dp, 0, 0)
                    }

                    text = argumentState.name
                })

                addView(ImageView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        setMargins(0, 16.dp, 0, 0)
                    }

                    setImageResource(R.drawable.ic_plus_circle)

                    setOnClickListener { argumentState.add() }
                })

                argumentState.newElementEvent.observeEvent { elementState ->
                    addView(LinearLayout(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                        orientation = LinearLayout.HORIZONTAL

                        createArgumentView(elementState)?.also {
                            it.layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT).apply { weight = 1.0f }
                        }?.let(::addView)

                        addView(ImageView(requireContext()).apply {
                            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                                setMargins(16.dp, 16.dp, 0, 0)
                            }

                            setImageResource(R.drawable.ic_delete_symbol)

                            setOnClickListener { argumentState.remove(elementState) }
                        })
                    }, childCount - 1)
                }

                argumentState.deletedElementEvent.observeEvent {
                    removeViewAt(it + 1)
                }
            }

            is ArgumentState.StructState -> LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                orientation = LinearLayout.VERTICAL

                argumentState.elements.forEach { name, state ->
                    createArgumentView(state)?.let(::addView)
                }
            }

            is ArgumentState.CollectionEnumState -> LabeledTextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    setMargins(0, 16.dp, 0, 0)
                }

                setLabel(argumentState.name)

                argumentState.selectedOption.observe {
                    setMessage(it)
                }

                setActionIcon(context.getDrawableCompat(R.drawable.ic_pin_white_24))

                setWholeClickListener { argumentState.optionSelectorClicked() }
            }

            is ArgumentState.NullState -> null
            else -> TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)

                text = argumentState.name + " is not supported"
            }
        }
    }
}
