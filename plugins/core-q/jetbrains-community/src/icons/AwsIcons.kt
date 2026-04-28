// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Lives in `icons` package due to that is how [com.intellij.openapi.util.IconLoader.getReflectiveIcon] works
 */
@Deprecated("Plugin-specific icons should not be declared in shared icons")
object AwsIcons {
    object Logos {
        @JvmField val AWS = load("/icons/logos/AWS.svg") // 13x13

        @JvmField val AWS_SMILE_SMALL = load("/icons/logos/AWS_smile.svg") // 16x16

        @JvmField val AWS_SMILE_LARGE = load("/icons/logos/AWS_smile_Large.svg") // 64x64

        @JvmField val AWS_Q = load("/icons/logos/AWS_Q.svg") // 13x13

        @JvmField val AWS_Q_GREY = load("/icons/logos/Amazon_Q_grey.svg") // 16x16

        @JvmField val AWS_Q_GRADIENT = load("/icons/logos/Amazon-Q-Icon_Gradient_Large.svg") // 54x54

        @JvmField val AWS_Q_GRADIENT_SMALL = load("/icons/logos/Amazon-Q-Icon_Gradient_Medium.svg") // 54x54
    }

    object Misc {
        @JvmField val SMILE = load("/icons/misc/smile.svg") // 16x16

        @JvmField val SMILE_GREY = load("/icons/misc/smile_grey.svg") // 16x16

        @JvmField val FROWN = load("/icons/misc/frown.svg") // 16x16

        @JvmField val LEARN = load("/icons/misc/learn.svg") // 16x16

        @JvmField val JAVA = load("/icons/misc/java.svg") // 16x16

        @JvmField val PYTHON = load("/icons/misc/python.svg") // 16x16

        @JvmField val JAVASCRIPT = load("/icons/misc/javaScript.svg") // 16x16

        @JvmField val TYPESCRIPT = load("/icons/misc/typeScript.svg") // 16x16

        @JvmField val CSHARP = load("/icons/misc/csharp.svg") // 16x16

        @JvmField val NEW = load("/icons/misc/new.svg") // 16x16
    }

    object Resources {
        object CodeWhisperer {
            @JvmField val CUSTOM = load("icons/resources/CodewhispererCustom.svg") // 16 * 16

            // Icons with full severity string

            @JvmField val SEVERITY_INFO = load("/icons/resources/codewhisperer/severity-info.svg")

            @JvmField val SEVERITY_LOW = load("/icons/resources/codewhisperer/severity-low.svg")

            @JvmField val SEVERITY_MEDIUM = load("/icons/resources/codewhisperer/severity-medium.svg")

            @JvmField val SEVERITY_HIGH = load("/icons/resources/codewhisperer/severity-high.svg")

            @JvmField val SEVERITY_CRITICAL = load("/icons/resources/codewhisperer/severity-critical.svg")

            // Icons with severity initials

            @JvmField val SEVERITY_INITIAL_INFO = load("/icons/resources/codewhisperer/severity-initial-info.svg")

            @JvmField val SEVERITY_INITIAL_LOW = load("/icons/resources/codewhisperer/severity-initial-low.svg")

            @JvmField val SEVERITY_INITIAL_MEDIUM = load("/icons/resources/codewhisperer/severity-initial-medium.svg")

            @JvmField val SEVERITY_INITIAL_HIGH = load("/icons/resources/codewhisperer/severity-initial-high.svg")

            @JvmField val SEVERITY_INITIAL_CRITICAL = load("/icons/resources/codewhisperer/severity-initial-critical.svg")
        }
    }

    object CodeTransform {
        @JvmField val TIMELINE_STEP_DARK = load("/icons/resources/codetransform/transform-timeline-step-done.svg") // 16 * 16

        @JvmField val TIMELINE_STEP_LIGHT = load("/icons/resources/codetransform/transform-timeline-step-done-light.svg") // 16 * 16

        @JvmField val CHECKMARK_GREEN = load("/icons/resources/codetransform/greenCheckmark.svg")

        @JvmField val CHECKMARK_GRAY = load("/icons/resources/codetransform/checkmark.svg")

        @JvmField val TIMELINE_STEP = load("/icons/resources/codetransform/transform-timeline-step-done.svg") // 16 * 16

        @JvmField val PLAN_VARIABLES_LIGHT = load("/icons/resources/codetransform/transform-variables-light.svg")

        @JvmField val PLAN_VARIABLES_DARK = load("/icons/resources/codetransform/transform-variables-dark.svg")

        @JvmField val PLAN_STEP_INTO_LIGHT = load("/icons/resources/codetransform/transform-step-into-light.svg")

        @JvmField val PLAN_STEP_INTO_DARK = load("/icons/resources/codetransform/transform-step-into-dark.svg")

        @JvmField val PLAN_DEPENDENCIES_LIGHT = load("/icons/resources/codetransform/transform-dependencies-light.svg")

        @JvmField val PLAN_DEPENDENCIES_DARK = load("/icons/resources/codetransform/transform-dependencies-dark.svg")

        @JvmField val PLAN_FILE_LIGHT = load("/icons/resources/codetransform/transform-file-light.svg")

        @JvmField val PLAN_FILE_DARK = load("/icons/resources/codetransform/transform-file-dark.svg")

        @JvmField val PLAN_ARROW_LIGHT = load("/icons/resources/codetransform/transform-arrow-light.svg")

        @JvmField val PLAN_ARROW_DARK = load("/icons/resources/codetransform/transform-arrow-dark.svg")

        @JvmField val PLAN_DEFAULT_LIGHT = load("/icons/resources/codetransform/transform-default-light.svg")

        @JvmField val PLAN_DEFAULT_DARK = load("/icons/resources/codetransform/transform-default-dark.svg")
    }

    private fun load(path: String): Icon = IconLoader.getIcon(path, AwsIcons::class.java)
}
