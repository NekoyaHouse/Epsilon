package com.github.epsilon.kernel.interfaces

interface Nameable {
    val name: CharSequence
    val aliases get() = listOf(name)
    val nameAsString get() = name.toString()
}