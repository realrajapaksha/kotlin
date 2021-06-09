/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "ThreadRegistry.hpp"

#include "GlobalData.hpp"
#include "ThreadData.hpp"

using namespace kotlin;

// static
mm::ThreadRegistry& mm::ThreadRegistry::Instance() noexcept {
    return mm::GlobalData::Instance().threadRegistry();
}

mm::ThreadRegistry::Node* mm::ThreadRegistry::RegisterCurrentThread() noexcept {
    auto* threadDataNode = list_.Emplace(pthread_self());
    Node*& currentDataNode = currentThreadDataNode_;
    RuntimeAssert(!IsCurrentThreadRegistered(), "This thread already had some data assigned to it.");
    currentDataNode = threadDataNode;
    return threadDataNode;
}

void mm::ThreadRegistry::Unregister(Node* threadDataNode) noexcept {
    list_.Erase(threadDataNode);
    // Touch `currentThreadData_` only in main thread as otherwise TLS may already have been deallocated.
    if (pthread_main_np()) {
        currentThreadDataNode_ = nullptr;
    }
}

mm::ThreadRegistry::Iterable mm::ThreadRegistry::Iter() noexcept {
    return list_.Iter();
}

ALWAYS_INLINE mm::ThreadData* mm::ThreadRegistry::CurrentThreadData() const noexcept {
    return CurrentThreadDataNode()->Get();
}

mm::ThreadRegistry::ThreadRegistry() = default;
mm::ThreadRegistry::~ThreadRegistry() = default;

// static
THREAD_LOCAL_VARIABLE mm::ThreadRegistry::Node* mm::ThreadRegistry::currentThreadDataNode_ = nullptr;
