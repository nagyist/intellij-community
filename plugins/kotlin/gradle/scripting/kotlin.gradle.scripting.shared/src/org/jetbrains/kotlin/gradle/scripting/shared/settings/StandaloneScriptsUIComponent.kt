// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.ui.table.TableView
import com.intellij.util.ui.JBUI
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsLocator
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsManager
import javax.swing.JComponent
import javax.swing.ListSelectionModel

class StandaloneScriptsUIComponent(val project: Project) : UnnamedConfigurable {
    private val fileChooser: FileChooserDescriptor = object : FileChooserDescriptor(
        true, false, false,
        false, false, true
    ) {
        override fun isFileSelectable(file: VirtualFile?): Boolean {
            if (file == null) return false
            val rootsManager = GradleBuildRootsManager.getInstance(project)
            val scriptUnderRoot = rootsManager?.findScriptBuildRoot(file)
            val notificationKind = scriptUnderRoot?.notificationKind
            return notificationKind == GradleBuildRootsLocator.NotificationKind.legacyOutside || notificationKind == GradleBuildRootsLocator.NotificationKind.notEvaluatedInLastImport
        }
    }

    private var panel: JComponent? = null

    private val scriptsFromStorage = StandaloneScriptsStorage.getInstance(project)?.files?.toList() ?: emptyList()
    private val scriptsInTable = scriptsFromStorage.toMutableList()

    private val table: JBTable
    private val model: KotlinStandaloneScriptsModel = KotlinStandaloneScriptsModel.createModel(scriptsInTable)

    init {
        table = TableView(model)
        table.preferredScrollableViewportSize = JBUI.size(300, -1)
        table.selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION

        model.addTableModelListener(table)
    }

    override fun reset() {
        scriptsInTable.clear()
        scriptsInTable.addAll(scriptsFromStorage)
        model.items = scriptsInTable
    }

    override fun apply() {
        GradleBuildRootsManager.getInstance(project)?.updateStandaloneScripts {
            val previousScripts = scriptsFromStorage

            scriptsInTable
                .filterNot { previousScripts.contains(it) }
                .forEach { addStandaloneScript(it) }

            previousScripts
                .filterNot { scriptsInTable.contains(it) }
                .forEach { removeStandaloneScript(it) }
        }
    }

    override fun createComponent(): JComponent? {
        if (panel == null) {
            val component = ToolbarDecorator.createDecorator(table)
                .setAddAction { addScripts() }
                .setRemoveAction { removeScripts() }
                .setRemoveActionUpdater { table.selectedRow >= 0 }
                .disableUpDownActions()
                .createPanel()

            this.panel = component
        }
        return panel
    }

    override fun isModified(): Boolean {
        return scriptsFromStorage != scriptsInTable
    }

    override fun disposeUIResources() {
        panel = null
    }

    private fun addScripts() {
        val chosen = FileChooser.chooseFiles(fileChooser, project, null)
        for (chosenFile in chosen) {
            val path = chosenFile.path

            if (scriptsInTable.contains(path)) continue

            scriptsInTable.add(path)
            model.addRow(path)
        }
    }

    private fun removeScripts() {
        val selected = table.selectedRows
        if (selected == null || selected.isEmpty()) {
            return
        }
        for ((removedCount, indexToRemove) in selected.withIndex()) {
            val row = indexToRemove - removedCount
            scriptsInTable.removeAt(row)
            model.removeRow(row)
        }
        IdeFocusManager.getGlobalInstance()
            .doWhenFocusSettlesDown { IdeFocusManager.getGlobalInstance().requestFocus(table, true) }
    }
}