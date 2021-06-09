/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include <cerrno>
#include <string_view>
#include <cstring>
#include <cstdio>
#include <unistd.h>

#include "KAssert.h"
#include "Memory.h"
#include "Porting.h"
#include "ThreadData.hpp"
#include "ThreadRegistry.hpp"
#include "ThreadState.hpp"
#include "ExecFormat.h"

using namespace kotlin;

namespace {

constexpr const char* goodFunctionNames[] = {
        "\x01_close",
        "\x01_mprotect",

        "_ZL15_objc_terminatev", // _objc_terminate()
        "_ZNSt13exception_ptrC1ERKS_", // std::exception_ptr::exception_ptr(std::exception_ptr const&)
        "_ZNSt13exception_ptrD1Ev", // std::exception_ptr::~exception_ptr()
        "_ZNSt3__112__next_primeEm", // std::__1::__next_prime(unsigned long)
        "_ZNSt3__112basic_stringIcNS_11char_traitsIcEENS_9allocatorIcEEED1Ev", // std::__1::basic_string<char, std::__1::char_traits<char>, std::__1::allocator<char> >::~basic_string()
        "_ZNSt3__16chrono12steady_clock3nowEv", // std::__1::chrono::steady_clock::now()
        "_ZNSt3__19to_stringEi", // std::__1::to_string(int)
        "_ZNSt9exceptionD2Ev", // std::exception::~exception()
        "_ZSt17current_exceptionv", // std::current_exception()
        "_ZSt17rethrow_exceptionSt13exception_ptr", // std::rethrow_exception(std::exception_ptr)
        "_ZSt9terminatev", // std::terminate()

        "__cxa_allocate_exception",
        "__cxa_begin_catch",
        "__cxa_end_catch",
        "__cxa_throw",
        "__memset_chk",

        "abort",
        "acos",
        "acosf",
        "acosh",
        "acoshf",
        "asin",
        "asinf",
        "asinh",
        "asinhf",
        "atan",
        "atan2",
        "atan2f",
        "atanf",
        "atanh",
        "atanhf",
        "calloc",
        "clock_gettime",
        "cosh",
        "coshf",
        "exit",
        "expm1",
        "expm1f",
        "free",
        "hypot",
        "hypotf",
        "log1p",
        "log1pf",
        "malloc",
        "memcmp",
        "memmem",
        "nextafter",
        "nextafterf",
        "remainder",
        "remainderf",
        "sinh",
        "sinhf",
        "strcmp",
        "strlen",
        "tan",
        "tanf",
        "tanh",
        "tanhf",
        "vsnprintf",

        "dispatch_once",
        "\x01_pthread_cond_init",
        "_pthread_cond_init",
        "pthread_cond_destroy",
        "pthread_cond_signal",
        "pthread_equal",
        "pthread_main_np",
        "pthread_mutex_destroy",
        "pthread_mutex_init",
        "pthread_mutex_unlock",
        "pthread_self",

        "+[NSError errorWithDomain:code:userInfo:]",
        "+[NSMethodSignature signatureWithObjCTypes:]",
        "+[NSNull null]",
        "+[NSObject class]",
        "+[NSObject new]",
        "+[NSString stringWithUTF8String:]",
        "+[NSValue valueWithPointer:]",
        "-[NSDictionary objectForKeyedSubscript:]",
        "-[NSError localizedDescription]",
        "-[NSError userInfo]",
        "-[NSMethodSignature getArgumentTypeAtIndex:]",
        "-[NSMethodSignature methodReturnType]",
        "-[NSMethodSignature numberOfArguments]",
        "-[NSObject init]",
        "-[NSPlaceholderString initWithBytes:length:encoding:]",
        "-[NSPlaceholderString initWithBytesNoCopy:length:encoding:freeWhenDone:]",
        "-[NSValue pointerValue]",
        "-[__NSArrayI count]",
        "-[__NSArrayI objectAtIndex:]",
        "-[__NSCFNumber doubleValue]",
        "-[__NSCFNumber floatValue]",
        "-[__NSCFNumber intValue]",
        "-[__NSCFNumber longLongValue]",
        "-[__NSCFNumber objCType]",
        "-[__NSCFString isEqual:]",
        "-[__NSDictionaryM setObject:forKeyedSubscript:]",
        "-[__NSFrozenDictionaryM objectForKeyedSubscript:]",
        "-[__SwiftNativeNSError userInfo]",
        "CFStringCreateCopy",
        "CFStringGetCharacters",
        "CFStringGetLength",
        "class_addMethod",
        "class_addProtocol",
        "class_copyMethodList",
        "class_copyProtocolList",
        "class_getClassMethod",
        "class_getName",
        "class_getSuperclass",
        "method_getName",
        "method_getTypeEncoding",
        "objc_alloc",
        "objc_allocateClassPair",
        "objc_autorelease",
        "objc_autoreleasePoolPush",
        "objc_autoreleaseReturnValue",
        "objc_getAssociatedObject",
        "objc_getClass",
        "objc_getProtocol",
        "objc_registerClassPair",
        "objc_retain",
        "objc_retainAutoreleaseReturnValue",
        "objc_retainBlock",
        "objc_setAssociatedObject",
        "object_getClass",
        "protocol_copyProtocolList",
        "protocol_getName",
        "sel_registerName",

        // @objc Swift._ContiguousArrayStorage.count.getter : Swift.Int
        "$ss23_ContiguousArrayStorageC5countSivgTo",
        // reabstraction thunk helper from @escaping @callee_guaranteed (@guaranteed __C.KtKotlinUnit?, @guaranteed Swift.Error?) -> () to @escaping @callee_unowned @convention(block) (@unowned __C.KtKotlinUnit?, @unowned __C.NSError?) -> ()
        "$sSo12KtKotlinUnitCSgs5Error_pSgIeggg_ACSo7NSErrorCSgIeyByy_TR",
        // reabstraction thunk helper from @escaping @callee_guaranteed (@guaranteed __C.KtInt?, @guaranteed Swift.Error?) -> () to @escaping @callee_unowned @convention(block) (@unowned __C.KtInt?, @unowned __C.NSError?) -> ()
        "$sSo5KtIntCSgs5Error_pSgIeggg_ACSo7NSErrorCSgIeyByy_TR",
        // @objc Swift.__SwiftDeferredNSArray.count.getter : Swift.Int
        "$ss22__SwiftDeferredNSArrayC5countSivgTo",
        // @objc Swift._ContiguousArrayStorage.objectAt(Swift.Int) -> Swift.Unmanaged<Swift.AnyObject>
        "$ss23_ContiguousArrayStorageC8objectAtys9UnmanagedVyyXlGSiFTo",
        //   @objc Swift.__SwiftNativeNSArrayWithContiguousStorage.objectAt(Swift.Int) -> Swift.Unmanaged<Swift.AnyObject>
        "$ss41__SwiftNativeNSArrayWithContiguousStorageC8objectAtys9UnmanagedVyyXlGSiFTo",
        // @objc Swift.__EmptyDictionarySingleton.count.getter : Swift.Int
        "$ss26__EmptyDictionarySingletonC5countSivgTo",
        // @objc Swift._SwiftDeferredNSDictionary.count.getter : Swift.Int
        "$ss26_SwiftDeferredNSDictionaryC5countSivgTo",
        // @objc Swift._SwiftDeferredNSDictionary.keyEnumerator() -> Swift._NSEnumerator
        "$ss26_SwiftDeferredNSDictionaryC13keyEnumerators13_NSEnumerator_pyFTo",
        // @objc Swift._SwiftDictionaryNSEnumerator.nextObject() -> Swift.AnyObject?
        "$ss28_SwiftDictionaryNSEnumeratorC10nextObjectyXlSgyFTo",
        // reabstraction thunk helper from @escaping @callee_guaranteed (@in_guaranteed Any?, @guaranteed Swift.Error?) -> () to @escaping @callee_unowned @convention(block) (@unowned Swift.AnyObject?, @unowned __C.NSError?) -> ()
        "$sypSgs5Error_pSgIegng_yXlSgSo7NSErrorCSgIeyByy_TR",
};

class KnownFunctionChecker {
    int read_pipe = -1;
    int write_pipe = -1;
    std::string_view good_names_copy[sizeof(goodFunctionNames) / sizeof(goodFunctionNames[0])];

    static constexpr int64_t PREFIX_MAGIC = 0x2ff68e62079bd1ac; // duplicated in compiler
public:
    /**
     * If checking mode is enabled, each non-external function have prefix
     * equal to PREFIX_MAGIC
     *
     * Function pointers received by this function can be very strange,
     * in particular, memory before them may by even not readable.
     *
     * To avoid SIGSEGV on reading function prefix data, it's written to pipe,
     * which can correctly handle unreadable memory. If it's succeed, memory
     * can be read from other pipe end. If it's failed --- there is no good
     * prefix before this function
     */
    bool isKnownByPrefix(const void* fun) noexcept {
        if (read_pipe == -1) {
            int ps[2];
            int ret = pipe(ps);
            RuntimeCheck(ret == 0, "failed to create pipes for checking functions: %s, error_code = %d", strerror(errno), ret);
            read_pipe = ps[0];
            write_pipe = ps[1];
        }

        int64_t data;
        if (write(write_pipe, reinterpret_cast<const decltype(data)*>(fun) - 1, sizeof(data)) != sizeof(data)) {
            return false;
        }
        ssize_t bytesRead = read(read_pipe, &data, sizeof(data));
        RuntimeCheck(bytesRead == sizeof(data), "Failed to read data back from pipe");

        return data == PREFIX_MAGIC;
    }

    bool isSafeByName(std::string_view name) noexcept {
        if (good_names_copy[0].empty()) {
            std::copy(std::begin(goodFunctionNames), std::end(goodFunctionNames), std::begin(good_names_copy));
            std::sort(std::begin(good_names_copy), std::end(good_names_copy));
        }
        return std::binary_search(std::begin(good_names_copy), std::end(good_names_copy), name);
    }

    ~KnownFunctionChecker() {
        if (read_pipe != -1) {
            close(read_pipe);
            read_pipe = -1;
        }
        if (write_pipe != -1) {
            close(write_pipe);
            write_pipe = -1;
        }
    }
};

thread_local bool recursiveCallGuard = false;
thread_local KnownFunctionChecker checker;

}
/**
 * This function calls is inserted to llvm bitcode automatically, so it can be called almost anywhre.
 *
 * Although, function itself is excluded, it can call itself indirectly, from other called functions.
 * Because of this, thread_local guard is used to avoid recursive calls.
 *
 * Unfortunately, function can be called in thread constructors or destructors, where thread local data
 * should not be accessed. So before guard checking we need to check is thread destructor is running,
 * which requires special handling of recursive calls from this check.
 */
extern "C" RUNTIME_NOTHROW void Kotlin_mm_checkStateAtExternalFunctionCall(const char* caller, const char *callee, const void *calleePtr) noexcept {
    if (reinterpret_cast<int64_t>(calleePtr) == -1) return; // objc_sendMsg called on nil, it does nothing, so it's ok
    if (!strcmp(caller, "_ZN5konan33isThreadSpecificDestructorRunningEv")) return;
    if (konan::isThreadSpecificDestructorRunning()) return;
    if (recursiveCallGuard) return;
    if (!mm::ThreadRegistry::Instance().IsCurrentThreadRegistered()) return;
    struct unlockGuard {
        unlockGuard() { recursiveCallGuard = true; }
        ~unlockGuard() { recursiveCallGuard = false; }
    } guard;


    auto actualState = GetThreadState();
    if (actualState == ThreadState::kNative) {
        return;
    }
    if (checker.isKnownByPrefix(calleePtr)) {
        return;
    }

    char buf[200];
    if (callee == nullptr) {
        if (AddressToSymbol(calleePtr, buf, sizeof(buf))) {
            callee = buf;
        } else {
            callee = "unknown function";
        }
    }

    if (checker.isSafeByName(callee)) {
        return;
    }

    RuntimeCheck(
            actualState == ThreadState::kNative, "Expected kNative thread state at call of function %s by function %s",
            callee, caller);
}

