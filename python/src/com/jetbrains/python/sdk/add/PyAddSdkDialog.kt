// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add

import com.intellij.CommonBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBCardLayout
import com.intellij.ui.components.DialogPanel
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.util.ExceptionUtil
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.sdk.add.PyAddSdkDialog.Companion.show
import com.jetbrains.python.showErrorDialog
import java.awt.CardLayout
import java.awt.event.ActionEvent
import java.io.IOException
import java.util.function.Consumer
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The dialog may look like the normal dialog with OK, Cancel and Help buttons
 * or the wizard dialog with Next, Previous, Finish, Cancel and Help buttons.
 *
 * Use [show] to instantiate and show the dialog.
 *
 */
class PyAddSdkDialog private constructor(
  private val project: Project,
  private val module: Module?,
  private val existingSdks: List<Sdk>,
) : DialogWrapper(project) {
  /**
   * This is the main panel that supplies sliding effect for the wizard states.
   */
  private val mainPanel: JPanel = DialogPanel(null, JBCardLayout())

  private var selectedPanel: PyAddSdkView? = null
  private val context = UserDataHolderBase()
  private var panels: List<PyAddSdkView> = emptyList()

  init {
    title = PyBundle.message("python.sdk.add.python.interpreter.title")
  }

  override fun createCenterPanel(): JComponent {
    val panels = PyAddSdkProvider.EP_NAME.extensionList
      .mapNotNull {
        safeCreateView(it, project = project, module = module, existingSdks = existingSdks, context = context)
          .registerIfDisposable()
      }
    mainPanel.add(SPLITTER_COMPONENT_CARD_PANE, createCardSplitter(panels))
    return mainPanel
  }


  private fun <T> T.registerIfDisposable(): T = apply { (this as? Disposable)?.let { Disposer.register(disposable, it) } }

  private var navigationPanelCardLayout: CardLayout? = null

  private var southPanel: JPanel? = null

  override fun createSouthPanel(): JComponent {
    val regularDialogSouthPanel = super.createSouthPanel()
    val wizardDialogSouthPanel = createWizardSouthPanel()

    navigationPanelCardLayout = CardLayout()

    val result = JPanel(navigationPanelCardLayout).apply {
      add(regularDialogSouthPanel, REGULAR_CARD_PANE)
      add(wizardDialogSouthPanel, WIZARD_CARD_PANE)
    }

    southPanel = result

    return result
  }

  private fun createWizardSouthPanel(): JPanel {
    assert(value = style != DialogStyle.COMPACT,
           lazyMessage = { "${PyAddSdkDialog::class.java} is not ready for ${DialogStyle.COMPACT} dialog style" })

    return doCreateSouthPanel(leftButtons = listOf(),
                              rightButtons = listOf(previousButton.value, nextButton.value,
                                                    cancelButton.value))
  }

  private val nextAction: Action = object : DialogWrapperAction(PyBundle.message("python.sdk.next")) {
    override fun doAction(e: ActionEvent) {
      selectedPanel?.let {
        if (it.actions.containsKey(PyAddSdkDialogFlowAction.NEXT)) onNext()
        else if (it.actions.containsKey(PyAddSdkDialogFlowAction.FINISH)) {
          onFinish()
        }
      }
    }
  }

  private val nextButton = lazy { createJButtonForAction(nextAction) }

  private val previousAction = object : DialogWrapperAction(PyBundle.message("python.sdk.previous")) {
    override fun doAction(e: ActionEvent) = onPrevious()
  }

  private val previousButton = lazy { createJButtonForAction(previousAction) }

  private val cancelButton = lazy { createJButtonForAction(cancelAction) }

  override fun postponeValidation(): Boolean = false

  override fun doValidateAll(): List<ValidationInfo> = selectedPanel?.validateAll() ?: emptyList()

  fun getOrCreateSdk(): Sdk? = selectedPanel?.getOrCreateSdk()

  private fun createCardSplitter(panels: List<PyAddSdkView>): Splitter {
    this.panels = panels
    return Splitter(false, 0.1f).apply {
      dividerPositionStrategy = Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE

      val cardLayout = CardLayout()
      val cardPanel = JPanel(cardLayout).apply {
        preferredSize = JBUI.size(800, 300)
        for (panel in panels) {
          add(panel.component, panel.panelName)

          panel.addStateListener(object : PyAddSdkStateListener {
            override fun onComponentChanged() {
              show(mainPanel, panel.component)

              selectedPanel?.let { updateWizardActionButtons(it) }
            }

            override fun onActionsStateChanged() {
              selectedPanel?.let { updateWizardActionButtons(it) }
            }
          })
        }
      }
      val cardsList = JBList(panels).apply {
        val descriptor = object : ListItemDescriptorAdapter<PyAddSdkView>() {
          override fun getTextFor(value: PyAddSdkView) = StringUtil.toTitleCase(value.panelName)
          override fun getIconFor(value: PyAddSdkView) = value.icon
        }
        cellRenderer = object : GroupedItemsListRenderer<PyAddSdkView>(descriptor) {
          override fun createItemComponent() = super.createItemComponent().apply {
            border = JBUI.Borders.empty(4, 4, 4, 10)
          }
        }
        addListSelectionListener {
          // Only last even must be processed. Other events may leave UI in inconsistent state
          if (it.valueIsAdjusting) return@addListSelectionListener
          selectedPanel = selectedValue
          isOKActionEnabled = false

          cardLayout.show(cardPanel, selectedValue.panelName)

          southPanel?.let {
            if (selectedValue.actions.containsKey(PyAddSdkDialogFlowAction.NEXT)) {
              navigationPanelCardLayout?.show(it, WIZARD_CARD_PANE)
              rootPane.defaultButton = nextButton.value

              updateWizardActionButtons(selectedValue)
            }
            else {
              navigationPanelCardLayout?.show(it, REGULAR_CARD_PANE)
              rootPane.defaultButton = getButton(okAction)
            }
          }

          selectedValue.onSelected()
        }
        selectedPanel = panels.getOrNull(0)
        selectedIndex = 0
      }

      firstComponent = cardsList
      secondComponent = cardPanel
    }
  }



  /**
   * Navigates to the next step of the current wizard view.
   */
  private fun onNext() {
    selectedPanel?.let {
      it.next()

      // sliding effect
      swipe(mainPanel, it.component, JBCardLayout.SwipeDirection.FORWARD)

      updateWizardActionButtons(it)
    }
  }

  /**
   * Navigates to the previous step of the current wizard view.
   */
  private fun onPrevious() {
    selectedPanel?.let {
      it.previous()

      // sliding effect
      if (it.actions.containsKey(PyAddSdkDialogFlowAction.PREVIOUS)) {
        val stepContent = it.component
        val stepContentName = stepContent.hashCode().toString()

        (mainPanel.layout as JBCardLayout).swipe(mainPanel, stepContentName, JBCardLayout.SwipeDirection.BACKWARD)
      }
      else {
        // this is the first wizard step
        (mainPanel.layout as JBCardLayout).swipe(mainPanel, SPLITTER_COMPONENT_CARD_PANE, JBCardLayout.SwipeDirection.BACKWARD)
      }

      updateWizardActionButtons(it)
    }
  }

  /**
   * Tries to create the SDK and closes the dialog if the creation succeeded.
   *
   * @see [doOKAction]
   */
  override fun doOKAction() {
    try {
      selectedPanel?.complete()
    }
    catch (e: IOException) {
      Messages.showErrorDialog(e.localizedMessage, CommonBundle.message("title.error"))
      return
    }
    catch (e: Exception) {
      val cause = ExceptionUtil.findCause(e, PyExecutionException::class.java)
      if (cause != null) {
        showErrorDialog(project, cause.pyError)
        return
      }
      throw e
    }
    close(OK_EXIT_CODE)
  }

  private fun onFinish() {
    doOKAction()
  }

  private fun updateWizardActionButtons(it: PyAddSdkView) {
    previousButton.value.isEnabled = false

    it.actions.forEach { (action, isEnabled) ->
      val actionButton = when (action) {
        PyAddSdkDialogFlowAction.PREVIOUS -> previousButton.value
        PyAddSdkDialogFlowAction.NEXT -> nextButton.value.apply { text = PyBundle.message("python.sdk.next") }
        PyAddSdkDialogFlowAction.FINISH -> nextButton.value.apply { text = PyBundle.message("python.sdk.finish") }
        else -> null
      }
      actionButton?.isEnabled = isEnabled
    }
  }
    /**
     * Fixes the problem when `PyAddDockerSdkProvider.createView` for Docker
     * and Docker Compose types throws [NoClassDefFoundError] exception when
     * `org.jetbrains.plugins.remote-run` plugin is disabled.
     */
    private fun safeCreateView(
      provider: PyAddSdkProvider,
      project: Project,
      module: Module?,
      existingSdks: List<Sdk>,
      context: UserDataHolder,
    ): PyAddSdkView? {
      try {
        return provider.createView(project, module, null, existingSdks, context, this)
      }
      catch (e: NoClassDefFoundError) {
        LOG.info(e)
        return null
      }
    }

  companion object {
    private val LOG: Logger = Logger.getInstance(PyAddSdkDialog::class.java)

    private const val SPLITTER_COMPONENT_CARD_PANE = "Splitter"

    private const val REGULAR_CARD_PANE = "Regular"

    private const val WIZARD_CARD_PANE = "Wizard"

    @JvmStatic
    fun show(project: Project, module: Module?, existingSdks: List<Sdk>, sdkAddedCallback: Consumer<Sdk?>) {
      val dialog = PyAddSdkDialog(project = project, module = module, existingSdks = existingSdks)
      dialog.init()

      val sdk = if (dialog.showAndGet()) dialog.getOrCreateSdk() else null
      sdkAddedCallback.accept(sdk)
    }

  }
}