package com.github.epsilon.kernel.interfaces

import com.github.epsilon.kernel.extensions.normalizeCase

interface Displayable {
    val displayName: CharSequence
        get() = this.toString().split("_").joinToString(" ") { it.normalizeCase() }

    val displayString: String
        get() = displayName.toString()
}