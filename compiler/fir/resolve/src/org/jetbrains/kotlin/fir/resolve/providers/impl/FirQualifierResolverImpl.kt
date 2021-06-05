/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.providers.impl

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.NoMutableState
import org.jetbrains.kotlin.fir.ROOT_PREFIX_FOR_IDE_RESOLUTION_MODE_NAME
import org.jetbrains.kotlin.fir.resolve.FirQualifierResolver
import org.jetbrains.kotlin.fir.resolve.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassifierSymbol
import org.jetbrains.kotlin.fir.types.FirQualifierPart
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@NoMutableState
class FirQualifierResolverImpl(val session: FirSession) : FirQualifierResolver() {

    override fun resolveSymbolWithPrefix(parts: List<FirQualifierPart>, prefix: ClassId): FirClassifierSymbol<*>? {
        val symbolProvider = session.symbolProvider

        val fqName = ClassId(
            prefix.packageFqName,
            parts.subList(1, parts.size).fold(prefix.relativeClassName) { result, suffix -> result.child(suffix.name) },
            false
        )
        return symbolProvider.getClassLikeSymbolByFqName(fqName)
    }

    override fun resolveSymbol(parts: List<FirQualifierPart>): FirClassifierSymbol<*>? {
        val actualParts = if (parts.firstOrNull()?.name == ROOT_PREFIX_FOR_IDE_RESOLUTION_MODE_NAME) {
            parts.subList(1, parts.size)
        } else {
            parts
        }

        if (actualParts.isNotEmpty()) {
            var firstFqName = FqName.ROOT
            for (index in actualParts.indices) {
                firstFqName = firstFqName.child(actualParts[index].name)
            }
            val lastPart = ArrayList<Name>(actualParts.size)

            while (!firstFqName.isRoot) {
                lastPart.add(firstFqName.shortName())
                firstFqName = firstFqName.parent()

                var lastFqName = FqName.ROOT
                for (index in lastPart.size - 1 downTo 0) {
                    lastFqName = lastFqName.child(lastPart[index])
                }

                val fqName = ClassId(firstFqName, lastFqName, false)
                val foundSymbol = session.symbolProvider.getClassLikeSymbolByFqName(fqName)
                if (foundSymbol != null) {
                    return foundSymbol
                }
            }
        }
        return null
    }
}
