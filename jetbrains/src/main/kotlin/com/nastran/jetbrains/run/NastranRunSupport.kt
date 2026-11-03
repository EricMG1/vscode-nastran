package com.nastran.jetbrains.run

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.nastran.jetbrains.actions.nastranFile
import com.nastran.jetbrains.lang.NastranFileType
import com.nastran.jetbrains.settings.NastranSettings
import javax.swing.JComponent

class NastranSettingsEditor(project: Project) : SettingsEditor<NastranRunConfiguration>() {
    private val inputFile = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener("Input Deck", null, project, FileChooserDescriptorFactory.createSingleFileDescriptor())
    }
    private val keywords = JBTextField()
    private val executable = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener("Nastran Executable", null, project, FileChooserDescriptorFactory.createSingleFileDescriptor())
    }
    private val workingDirectory = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener("Working Directory", null, project, FileChooserDescriptorFactory.createSingleFolderDescriptor())
    }

    private val panel = FormBuilder.createFormBuilder()
        .addLabeledComponent("Input deck:", inputFile)
        .addLabeledComponent("Keywords:", keywords)
        .addTooltip("Passed after the deck, e.g. mem=8gb scr=yes")
        .addLabeledComponent("Executable:", executable)
        .addTooltip("Leave empty to use Settings | Tools | Nastran")
        .addLabeledComponent("Working directory:", workingDirectory)
        .addTooltip("Leave empty to run in the deck's folder")
        .panel

    override fun resetEditorFrom(configuration: NastranRunConfiguration) {
        inputFile.text = configuration.inputFile
        keywords.text = configuration.keywords
        executable.text = configuration.executable
        workingDirectory.text = configuration.workingDirectory
    }

    override fun applyEditorTo(configuration: NastranRunConfiguration) {
        configuration.inputFile = inputFile.text.trim()
        configuration.keywords = keywords.text.trim()
        configuration.executable = executable.text.trim()
        configuration.workingDirectory = workingDirectory.text.trim()
    }

    override fun createEditor(): JComponent = panel
}

/** Offers "Run 'deck.bdf'" in the editor and project view context menus. */
class NastranRunConfigurationProducer : LazyRunConfigurationProducer<NastranRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory = NastranRunConfigurationType.getInstance().factory

    override fun setupConfigurationFromContext(
        configuration: NastranRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val file = deck(context) ?: return false
        configuration.setUp(file)
        return true
    }

    override fun isConfigurationFromContext(configuration: NastranRunConfiguration, context: ConfigurationContext): Boolean =
        deck(context)?.path == configuration.inputFile

    private fun deck(context: ConfigurationContext): VirtualFile? {
        val file = context.location?.virtualFile ?: context.psiLocation?.containingFile?.virtualFile ?: return null
        return file.takeIf { it.fileType == NastranFileType && it.isInLocalFileSystem }
    }
}

internal fun NastranRunConfiguration.setUp(file: VirtualFile) {
    inputFile = file.path
    keywords = NastranSettings.getInstance().keywords
    name = file.name
}

/** Runs the current deck, reusing its run configuration if there is one. */
class RunNastranAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null && nastranFile(e)?.isInLocalFileSystem == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = nastranFile(e) ?: return
        val runManager = RunManager.getInstance(project)
        val settings = runManager.allSettings.firstOrNull {
            (it.configuration as? NastranRunConfiguration)?.inputFile == file.path
        } ?: runManager.createConfiguration(file.name, NastranRunConfigurationType.getInstance().factory).also {
            (it.configuration as NastranRunConfiguration).setUp(file)
            runManager.addConfiguration(it)
        }
        runManager.selectedConfiguration = settings
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }
}
