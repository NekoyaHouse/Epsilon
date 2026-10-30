package com.github.epsilon.elements.impl.island.pattern.impl

import com.github.epsilon.elements.impl.island.pattern.LCPattern
import java.util.function.Supplier

/**
 * 由外部条件决定何时关闭的条件。
 */
class CheckPattern(val supplier: Supplier<Boolean>) : LCPattern()
