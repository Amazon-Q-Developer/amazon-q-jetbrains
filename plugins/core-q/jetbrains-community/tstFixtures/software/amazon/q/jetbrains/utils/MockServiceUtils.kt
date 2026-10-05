// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.amazon.q.jetbrains.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.testFramework.registerOrReplaceServiceInstance

/**
 * Returns the [serviceInterface] service as a [Mock], registering [factory]'s result if it isn't already present.
 *
 * 2026.2 runs unit tests in a bare application/project that no longer loads plugin.xml, so the
 * `testServiceImplementation=` registrations our mock rules rely on are never applied and `service<X>()`
 * fails with "Cannot find service". Registering the mock on demand keeps the rules working on 2026.2 while
 * remaining a no-op on 2025.x–2026.1 (where the descriptor already provides the mock).
 */
inline fun <reified Mock : Service, Service : Any> ComponentManager.getOrRegisterMockService(
    serviceInterface: Class<Service>,
    factory: () -> Mock,
): Mock {
    val existing = getServiceIfCreated(serviceInterface)
    if (existing is Mock) {
        return existing
    }

    return factory().also {
        // scope to the application: the test framework tears the application down between suites, matching
        // the lifetime the descriptor-registered service previously had
        registerOrReplaceServiceInstance(serviceInterface, it, ApplicationManager.getApplication())
    }
}
