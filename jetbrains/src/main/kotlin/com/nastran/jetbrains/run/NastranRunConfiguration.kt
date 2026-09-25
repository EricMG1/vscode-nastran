package com.nastran.jetbrains.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.LocatableRunConfigurationOptions
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.util.execution.ParametersListUtil
import com.nastran.jetbrains.lang.NastranIcons
import com.nastran.jetbrains.settings.NastranSettings
import java.io.File

class NastranRunConfigurationType : ConfigurationTypeBase(
    ID,
    "Nastran",
    "Run an MSC Nastran input deck",
    NotNullLazyValue.createValue { NastranIcons.FILE },
) {
    init {
        addFactory(NastranConfigurationFactory(this))
    }

    val factory: ConfigurationFactory get() = configurationFactories.single()

    companion object {
        const val ID = "NastranRunConfiguration"

        fun getInstance(): NastranRunConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(NastranRunConfigurationType::class.java)
    }
}

class NastranConfigurationFactory(type: NastranRunConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = NastranRunConfigurationType.ID

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        NastranRunConfiguration(project, this, "Nastran")

    override fun getOptionsClass(): Class<out BaseState> = NastranRunConfigurationOptions::class.java
}

class NastranRunConfigurationOptions : LocatableRunConfigurationOptions() {
    var executable by string("")
    var inputFile by string("")
    var keywords by string("")
    var workingDirectory by string("")
}

/**
 * Runs `nastran <deck> <keywords>` in the deck's folder and shows the output
 * in the Run tool window. Port of client/src/execute.ts, which sent the same
 * command to the VS Code terminal.
 */
class NastranRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    LocatableConfigurationBase<NastranRunConfigurationOptions>(project, factory, name) {

    override fun getOptions(): NastranRunConfigurationOptions = super.getOptions() as NastranRunConfigurationOptions

    /** Empty means the executable from Settings | Tools | Nastran. */
    var executable: String
        get() = options.executable ?: ""
        set(value) { options.executable = value }

    var inputFile: String
        get() = options.inputFile ?: ""
        set(value) { options.inputFile = value }

    var keywords: String
        get() = options.keywords ?: ""
        set(value) { options.keywords = value }

    /** Empty means the folder of the input file. */
    var workingDirectory: String
        get() = options.workingDirectory ?: ""
        set(value) { options.workingDirectory = value }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = NastranSettingsEditor(project)

    override fun checkConfiguration() {
        if (inputFile.isBlank()) throw RuntimeConfigurationError("No input deck is selected")
        if (!File(inputFile).isFile) throw RuntimeConfigurationError("Input deck $inputFile does not exist")
    }

    override fun suggestedName(): String? = inputFile.takeIf { it.isNotBlank() }?.let { File(it).name }

    fun commandLine(): GeneralCommandLine {
        val deck = File(inputFile)
        val directory = workingDirectory.ifBlank { deck.parent ?: project.basePath ?: "." }
        val exe = executable.ifBlank { NastranSettings.getInstance().executable }
        // Nastran writes its output next to the deck name it is given, so pass
        // the bare file name when running in the deck's own folder
        val deckArgument = if (File(directory).absoluteFile == deck.absoluteFile.parentFile) deck.name else deck.absolutePath
        return GeneralCommandLine(exe)
            .withWorkDirectory(directory)
            .withParameters(deckArgument)
            .withParameters(ParametersListUtil.parse(keywords))
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        object : CommandLineState(environment) {
            @Throws(ExecutionException::class)
            override fun startProcess(): ProcessHandler {
                val handler = KillableColoredProcessHandler(commandLine())
                ProcessTerminatedListener.attach(handler)
                return handler
            }
        }
}
