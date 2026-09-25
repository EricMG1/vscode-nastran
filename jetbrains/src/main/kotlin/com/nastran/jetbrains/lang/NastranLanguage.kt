package com.nastran.jetbrains.lang

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object NastranLanguage : Language("Nastran") {
    private fun readResolve(): Any = NastranLanguage

    override fun getDisplayName(): String = "MSC Nastran"

    override fun isCaseSensitive(): Boolean = false
}

object NastranFileType : LanguageFileType(NastranLanguage) {
    override fun getName(): String = "Nastran"

    override fun getDescription(): String = "MSC Nastran input deck"

    override fun getDefaultExtension(): String = "bdf"

    override fun getIcon(): Icon = NastranIcons.FILE
}

object NastranIcons {
    @JvmField
    val FILE: Icon = IconLoader.getIcon("/icons/logo.png", NastranIcons::class.java)

    @JvmField
    val INCLUDE: Icon = IconLoader.getIcon("/icons/mesh.png", NastranIcons::class.java)

    @JvmField
    val INCLUDE_MISSING: Icon = IconLoader.getIcon("/icons/mesh_missing.png", NastranIcons::class.java)
}
