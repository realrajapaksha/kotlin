/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.refactoring.move.moveDeclarations

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.core.getOrCreateCompanionObject
import org.jetbrains.kotlin.idea.util.projectStructure.getModule
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.getOrPutNullable
import java.util.*

/**
 * [targetFileOrDir] could be [VirtualFile.isDirectory] or a file. Nullable when...
 * [targetScope] what is it?
 */
interface KotlinMoveTarget {
    val targetContainerFqName: FqName?
    val targetFileOrDir: VirtualFile?

    fun getOrCreateTargetPsi(originalPsi: PsiElement): KtElement?
    fun getTargetPsiIfExists(originalPsi: PsiElement): KtElement?

    // Check possible errors and return corresponding message, or null if no errors are detected
    fun verify(file: PsiFile): String?

}

/**
 * [directory] is nullable when ??? why targetFile is insufficient
 */
interface KotlinDirectoryBasedMoveTarget : KotlinMoveTarget

object EmptyKotlinMoveTarget : KotlinMoveTarget {
    override val targetContainerFqName: FqName? = null
    override val targetFileOrDir: VirtualFile? = null

    override fun getOrCreateTargetPsi(originalPsi: PsiElement): KtElement? = null
    override fun getTargetPsiIfExists(originalPsi: PsiElement): KtElement? = null
    override fun verify(file: PsiFile): String? = null
}

class KotlinMoveTargetForExistingElement(val targetElement: KtElement) : KotlinMoveTarget {
    override val targetContainerFqName = targetElement.containingKtFile.packageFqName

    override val targetFileOrDir: VirtualFile? = targetElement.containingKtFile.virtualFile

    override fun getOrCreateTargetPsi(originalPsi: PsiElement) = targetElement

    override fun getTargetPsiIfExists(originalPsi: PsiElement) = targetElement

    // No additional verification is needed
    override fun verify(file: PsiFile): String? = null
}

class KotlinMoveTargetForCompanion(val targetClass: KtClass) : KotlinMoveTarget {
    override val targetContainerFqName = targetClass.companionObjects.firstOrNull()?.fqName
        ?: targetClass.fqName!!.child(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)

    override val targetFileOrDir: VirtualFile? = targetClass.containingKtFile.virtualFile

    override fun getOrCreateTargetPsi(originalPsi: PsiElement) = targetClass.getOrCreateCompanionObject()

    override fun getTargetPsiIfExists(originalPsi: PsiElement) = targetClass.companionObjects.firstOrNull()

    // No additional verification is needed
    override fun verify(file: PsiFile): String? = null
}

/**
 * Assumes that ([targetFileOrDir]) does not yet exist? Why deferred?
 * [targetFileOrDir]
 * [createFile] is called when? always? or when targetFileOrDir is null?
 */
class KotlinMoveTargetForDeferredFile(
    override val targetContainerFqName: FqName,
    override val targetFileOrDir: VirtualFile?, //todo can it be null?
    private val createFile: (KtFile) -> KtFile? // todo why KtFile?
) : KotlinDirectoryBasedMoveTarget {
    private val createdFiles = HashMap<KtFile, KtFile?>()

    override fun getOrCreateTargetPsi(originalPsi: PsiElement): KtElement? {
        val originalFile = originalPsi.containingFile as? KtFile ?: return null
        return createdFiles.getOrPutNullable(originalFile) { createFile(originalFile) }
    }

    override fun getTargetPsiIfExists(originalPsi: PsiElement): KtElement? = null

    // No additional verification is needed
    override fun verify(file: PsiFile): String? = null
}

class KotlinDirectoryMoveTarget(
    override val targetContainerFqName: FqName,
    override val targetFileOrDir: VirtualFile
) : KotlinDirectoryBasedMoveTarget {

    override fun getOrCreateTargetPsi(originalPsi: PsiElement) = originalPsi.containingFile as? KtFile

    override fun getTargetPsiIfExists(originalPsi: PsiElement): KtElement? = null

    override fun verify(file: PsiFile): String? = null
}

fun KotlinMoveTarget.getTargetModule(project: Project) = targetFileOrDir?.getModule(project)