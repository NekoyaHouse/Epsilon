package com.github.epsilon.kernel.interfaces

interface Describable {
    val description: CharSequence
    val descString
        get() = description.toString()
}