package com.nastran.jetbrains.lang

import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class NastranColorSettingsPage : ColorSettingsPage {
    override fun getIcon(): Icon = NastranIcons.FILE

    override fun getHighlighter() = NastranSyntaxHighlighter()

    override fun getDemoText(): String = """
        |${'$'} Linear static analysis
        |SOL 101
        |CEND
        |TITLE = Plate under pressure
        |SUBCASE 1
        |  SPC = 1
        |  LOAD = 2
        |  DISPLACEMENT(PLOT) = ALL
        |BEGIN BULK
        |PARAM   <field>POST    </field>-1
        |${'$'}---1---${'$'}---2---${'$'}---3---${'$'}---4---${'$'}---5---${'$'}---6---${'$'}---7---${'$'}---8---${'$'}---9---${'$'}---10--${'$'}
        |GRID    1       <field>0       </field>0.0     <field>0.0     </field>0.0
        |CQUAD4  1       <field>1       </field>1       <field>2       </field>3       <field>4       </field>
        |INCLUDE 'mesh.bdf'
        |${'$'} Results: <link>file:///models/plate.bdf#12</link>
        |ENDDATA
        |""".trimMargin()

    override fun getAdditionalHighlightingTagToDescriptorMap() = mapOf(
        "field" to NastranColors.FIELD_ALT,
        "link" to NastranColors.FILE_LINK,
    )

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = arrayOf(
        AttributesDescriptor("Comment", NastranColors.COMMENT),
        AttributesDescriptor("Section delimiter (CEND, BEGIN BULK, ...)", NastranColors.SECTION_MARKER),
        AttributesDescriptor("NASTRAN statement", NastranColors.NASTRAN_STATEMENT),
        AttributesDescriptor("File Management statement", NastranColors.FMS_KEYWORD),
        AttributesDescriptor("Executive Control statement", NastranColors.EXEC_KEYWORD),
        AttributesDescriptor("Case Control command", NastranColors.CASE_KEYWORD),
        AttributesDescriptor("Bulk Data entry", NastranColors.BULK_CARD),
        AttributesDescriptor("PARAM name", NastranColors.PARAM_NAME),
        AttributesDescriptor("String", NastranColors.STRING),
        AttributesDescriptor("Field value", NastranColors.TEXT),
        AttributesDescriptor("Alternate bulk data field", NastranColors.FIELD_ALT),
        AttributesDescriptor("File link", NastranColors.FILE_LINK),
    )

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "Nastran"
}
