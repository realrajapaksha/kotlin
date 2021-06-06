/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.lightTree.fir.modifier

import org.jetbrains.kotlin.types.Variance

class TypeParameterModifier(varianceOrReificationModifiers: Long = ModifierFlag.NONE.value) : Modifier(varianceOrReificationModifiers) {
    fun getVariance(): Variance {
        return when {
            hasFlag(ModifierFlag.VARIANCE_OUT) -> Variance.OUT_VARIANCE
            hasFlag(ModifierFlag.VARIANCE_IN) -> Variance.IN_VARIANCE
            else -> Variance.INVARIANT
        }
    }

    fun hasReified(): Boolean = hasFlag(ModifierFlag.REIFICATION_REIFIED)
}
