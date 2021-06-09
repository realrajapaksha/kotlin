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
import java.util.*

/**
 * [targetFileOrDir] could be [VirtualFile.isDirectory] or a file. Nullable when...
 */
interface KotlinMoveTarget {
    val targetContainerFqName: FqName?
    val targetFileOrDir: VirtualFile?

    fun getOrCreateTargetPsi(originalPsi: PsiElement): KtElement
    fun getTargetPsiIfExists(originalPsi: PsiElement): KtElement?

    // Check possible errors and return corresponding message, or null if no errors are detected
    fun verify(file: PsiFile): String?

}

object EmptyKotlinMoveTarget : KotlinMoveTarget {
    override val targetContainerFqName: FqName? = null
    override val targetFileOrDir: VirtualFile? = null

    override fun getOrCreateTargetPsi(originalPsi: PsiElement): KtElement = throw UnsupportedOperationException()
    override fun getTargetPsiIfExists(originalPsi: PsiElement): KtElement? = null
    override fun verify(file: PsiFile): String? = null
}

class KotlinMoveTargetForExistingElement(val targetElement: KtElement) : KotlinMoveTarget {
    override val targetContainerFqName = targetElement.containingKtFile.packageFqName

    override val targetFileOrDir: VirtualFile = targetElement.containingKtFile.virtualFile

    override fun getOrCreateTargetPsi(originalPsi: PsiElement) = targetElement

    override fun getTargetPsiIfExists(originalPsi: PsiElement) = targetElement

    // No additional verification is needed
    override fun verify(file: PsiFile): String? = null
}

class KotlinMoveTargetForCompanion(val targetClass: KtClass) : KotlinMoveTarget {
    override val targetContainerFqName = targetClass.companionObjects.firstOrNull()?.fqName
        ?: targetClass.fqName!!.child(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)

    override val targetFileOrDir: VirtualFile = targetClass.containingKtFile.virtualFile

    override fun getOrCreateTargetPsi(originalPsi: PsiElement) = targetClass.getOrCreateCompanionObject()

    override fun getTargetPsiIfExists(originalPsi: PsiElement) = targetClass.companionObjects.firstOrNull()

    // No additional verification is needed
    override fun verify(file: PsiFile): String? = null
}

/**
 * Assumes that a target of move is another file (existent or not).
 * [targetFileOrDir] is always a directory where a target file exists or to be created
 * [createFile] is called to create a new file, should return null if file already exists (strange!)
 */
class KotlinMoveTargetForDeferredFile(
    override val targetContainerFqName: FqName,
    override val targetFileOrDir: VirtualFile?, //todo can it be null?
    private val createFile: (KtFile) -> KtFile
) : KotlinMoveTarget {
    private val createdFiles = HashMap<KtFile, KtFile>()

    override fun getOrCreateTargetPsi(originalPsi: PsiElement): KtElement {
        val file = originalPsi.containingFile ?: throw IllegalStateException("PSI element in not contained in any file: $originalPsi")
        val originalFile = file as KtFile
        return createdFiles.getOrPut(originalFile) { createFile(originalFile) }
    }

    override fun getTargetPsiIfExists(originalPsi: PsiElement): KtElement? = null

    // No additional verification is needed
    override fun verify(file: PsiFile): String? = null
}

class KotlinDirectoryMoveTarget(
    override val targetContainerFqName: FqName,
    override val targetFileOrDir: VirtualFile
) : KotlinMoveTarget {

    override fun getOrCreateTargetPsi(originalPsi: PsiElement): KtFile {
        val file = originalPsi.containingFile ?: throw IllegalStateException("PSI element in not contained in any file: $originalPsi")
        return file as KtFile
    }

    override fun getTargetPsiIfExists(originalPsi: PsiElement): KtElement? = null

    override fun verify(file: PsiFile): String? = null
}

fun KotlinMoveTarget.getTargetModule(project: Project) = targetFileOrDir?.getModule(project)