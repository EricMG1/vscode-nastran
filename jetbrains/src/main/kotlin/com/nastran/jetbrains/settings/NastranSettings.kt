package com.nastran.jetbrains.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

@Service(Service.Level.APP)
@State(name = "NastranSettings", storages = [Storage("nastran.xml")])
class NastranSettings : SimplePersistentStateComponent<NastranSettings.Settings>(Settings()) {
    class Settings : BaseState() {
        var executable by string(DEFAULT_EXECUTABLE)
        var keywords by string("")
        var shortRuler by string(DEFAULT_SHORT_RULER)
        var longRuler by string(DEFAULT_LONG_RULER)
    }

    val executable: String get() = state.executable?.ifBlank { null } ?: DEFAULT_EXECUTABLE
    val keywords: String get() = state.keywords ?: ""
    val shortRuler: String get() = state.shortRuler?.ifBlank { null } ?: DEFAULT_SHORT_RULER
    val longRuler: String get() = state.longRuler?.ifBlank { null } ?: DEFAULT_LONG_RULER

    companion object {
        const val DEFAULT_EXECUTABLE = "nastran"
        const val DEFAULT_SHORT_RULER = "$---1---$---2---$---3---$---4---$---5---$---6---$---7---$---8---$---9---$---10--$"
        const val DEFAULT_LONG_RULER = "$---1---$-------2-------$-------3-------$-------4-------$-------5-------$---6---$"

        fun getInstance(): NastranSettings = service()
    }
}

class NastranConfigurable : BoundConfigurable("Nastran") {
    private val settings get() = NastranSettings.getInstance().state

    override fun createPanel(): DialogPanel = panel {
        group("Running") {
            row("Nastran executable:") {
                textField()
                    .bindText({ settings.executable ?: "" }, { settings.executable = it })
                    .align(AlignX.FILL)
                    .comment("Command or full path used to run decks, e.g. nastran or /msc/bin/nast20224")
            }
            row("Default keywords:") {
                textField()
                    .bindText({ settings.keywords ?: "" }, { settings.keywords = it })
                    .align(AlignX.FILL)
                    .comment("Appended to the command line of new run configurations, e.g. mem=8gb scr=yes")
            }
        }
        group("Field Rulers") {
            row("Short field:") {
                textField()
                    .bindText({ settings.shortRuler ?: "" }, { settings.shortRuler = it })
                    .align(AlignX.FILL)
            }
            row("Long field:") {
                textField()
                    .bindText({ settings.longRuler ?: "" }, { settings.longRuler = it })
                    .align(AlignX.FILL)
                    .comment("Leave empty to use the default ruler")
            }
        }
    }
}
