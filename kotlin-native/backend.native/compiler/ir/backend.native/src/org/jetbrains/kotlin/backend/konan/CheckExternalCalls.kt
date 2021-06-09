/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan

import kotlinx.cinterop.toCValues
import llvm.*
import org.jetbrains.kotlin.backend.konan.llvm.*

private fun getBasicBlocks(function: LLVMValueRef) =
        generateSequence(LLVMGetFirstBasicBlock(function)) { LLVMGetNextBasicBlock(it) }

private fun getInstructions(function: LLVMBasicBlockRef) =
        generateSequence(LLVMGetFirstInstruction(function)) { LLVMGetNextInstruction(it) }

private fun LLVMValueRef.isFunctionCall() = LLVMIsACallInst(this) != null || LLVMIsAInvokeInst(this) != null

private fun LLVMValueRef.isExternalFunction() = LLVMGetFirstBasicBlock(this) == null


private fun LLVMValueRef.isLLVMBuiltin(): Boolean {
    val name = this.name ?: return false
    return name.startsWith("llvm.")
}


private class CallsChecker(val context: Context) {
    private fun externalFunction(name: String, type: LLVMTypeRef) =
            context.llvm.externalFunction(name, type, context.stdlibModule.llvmSymbolOrigin)

    private fun moduleFunction(name: String) =
            LLVMGetNamedFunction(context.llvmModule, name) ?: throw IllegalStateException("$name function is not available")

    val getMethodImpl = externalFunction("class_getMethodImplementation", functionType(int8TypePtr, false, int8TypePtr, int8TypePtr))
    val getClass = externalFunction("object_getClass", functionType(int8TypePtr, false, int8TypePtr))
    val checkerFunction = moduleFunction("Kotlin_mm_checkStateAtExternalFunctionCall")

    private data class ExternalCallInfo(val name: String?, val calledPtr: LLVMValueRef)

    private fun LLVMValueRef.getCalledFunction(): ExternalCallInfo? {
        fun isIndirectCallArgument(value: LLVMValueRef) = LLVMIsALoadInst(value) != null || LLVMIsAArgument(value) != null ||
                LLVMIsAPHINode(value) != null || LLVMIsASelectInst(value) != null || LLVMIsACallInst(value) != null

        fun cleanCalledFunction(value: LLVMValueRef): ExternalCallInfo? {
            return when {
                LLVMIsAFunction(value) != null -> {
                    ExternalCallInfo(value.name!!, value).takeIf { value.isExternalFunction() && !value.isLLVMBuiltin() }
                }
                LLVMIsACastInst(value) != null -> cleanCalledFunction(LLVMGetOperand(value, 0)!!)
                isIndirectCallArgument(value) -> ExternalCallInfo(null, value) // this is a callback call
                LLVMIsAInlineAsm(value) != null -> null // this is inline assembly call
                LLVMIsAConstantExpr(value) != null -> {
                    when (LLVMGetConstOpcode(value)) {
                        LLVMOpcode.LLVMBitCast -> cleanCalledFunction(LLVMGetOperand(value, 0)!!)
                        else -> TODO("not implemented constant type in call")
                    }
                }
                LLVMIsAGlobalAlias(value) != null -> cleanCalledFunction(LLVMAliasGetAliasee(value)!!)
                else -> {
                    LLVMDumpValue(this)
                    println()
                    LLVMDumpValue(value)
                    println()
                    TODO("not implemented call argument")
                }
            }
        }

        return cleanCalledFunction(LLVMGetCalledValue(this)!!)
    }

    private fun processBasicBlock(functionName: String, block: LLVMBasicBlockRef) {
        val calls = getInstructions(block)
                .filter { it.isFunctionCall() }
                .toList()
        val builder = LLVMCreateBuilderInContext(llvmContext)

        for (call in calls) {
            val calleeInfo = call.getCalledFunction() ?: continue
            if (LLVMIsAInlineAsm(LLVMGetCalledValue(call)) != null) continue
            LLVMPositionBuilderBefore(builder, call)
            val functionNameLlvm: LLVMValueRef?
            val calledNameLlvm: LLVMValueRef?
            val calledPtrLlvm: LLVMValueRef?
            when (calleeInfo.name) {
                "objc_msgSend" -> {
                    functionNameLlvm = context.llvm.staticData.cStringLiteral("$functionName(over objc_msgSend)").llvm
                    calledNameLlvm = LLVMConstNull(int8TypePtr)
                    val firstArgI8Ptr = LLVMBuildBitCast(builder, LLVMGetArgOperand(call, 0), int8TypePtr, "")
                    val firstArgClassPtr = LLVMBuildCall(builder, getClass, listOf(firstArgI8Ptr).toCValues(), 1, "")
                    val isNil = LLVMBuildICmp(builder, LLVMIntPredicate.LLVMIntEQ, LLVMBuildPtrToInt(builder, firstArgI8Ptr, int64Type, ""), Int64(0).llvm, "")
                    val calledPtrLlvmIfNotNil = LLVMBuildCall(builder, getMethodImpl, listOf(firstArgClassPtr, LLVMGetArgOperand(call, 1)).toCValues(), 2, "")
                    val calledPtrLlvmIfNil = LLVMConstIntToPtr(Int64(-1).llvm, int8TypePtr)
                    calledPtrLlvm = LLVMBuildSelect(builder, isNil, calledPtrLlvmIfNil, calledPtrLlvmIfNotNil, "")
                }
                "objc_msgSendSuper2" -> {
                    functionNameLlvm = context.llvm.staticData.cStringLiteral("$functionName(over objc_msgSendSuper2)").llvm
                    calledNameLlvm = LLVMConstNull(int8TypePtr)
                    val superStruct = LLVMGetArgOperand(call, 0)
                    val classPtrPtr = LLVMBuildGEP(builder, superStruct, listOf(Int32(0).llvm, Int32(1).llvm).toCValues(), 2, "")
                    val classPtr = LLVMBuildLoad(builder, classPtrPtr, "")
                    calledPtrLlvm = LLVMBuildCall(builder, getMethodImpl, listOf(classPtr, LLVMGetArgOperand(call, 1)).toCValues(), 2, "")
                }
                else -> {
                    functionNameLlvm = context.llvm.staticData.cStringLiteral(functionName).llvm
                    calledNameLlvm = if (calleeInfo.name == null) LLVMConstNull(int8TypePtr) else context.llvm.staticData.cStringLiteral(calleeInfo.name).llvm
                    calledPtrLlvm = LLVMBuildBitCast(builder, calleeInfo.calledPtr, int8TypePtr, "")
                }
            }
            LLVMBuildCall(builder, checkerFunction, listOf(functionNameLlvm, calledNameLlvm, calledPtrLlvm).toCValues(), 3, "")
        }
        LLVMDisposeBuilder(builder)
    }

    fun processFunction(function: LLVMValueRef) {
        if (function == checkerFunction) return
        getBasicBlocks(function).forEach {
            processBasicBlock(function.name!!, it)
        }
    }
}

internal fun checkLlvmModuleExternalCalls(context: Context) {
    val checker = CallsChecker(context)
    val magicValue = Int64(0x2ff68e62079bd1ac) // this constant in duplicated in runtime
    getFunctions(context.llvmModule!!).forEach {
        checker.processFunction(it)
        if (LLVMGetFirstBasicBlock(it) != null) {
            LLVMSetPrefixData(it, magicValue.llvm)
        }
    }
}