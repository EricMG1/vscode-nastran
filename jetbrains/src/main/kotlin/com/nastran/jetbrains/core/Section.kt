package com.nastran.jetbrains.core

/** The parts of a Nastran input deck, in file order. */
enum class Section {
    FMS, EXEC, CASE, BULK;

    /** Folder name under docs/ holding this section's pages. */
    val docsFolder: String get() = name
}
