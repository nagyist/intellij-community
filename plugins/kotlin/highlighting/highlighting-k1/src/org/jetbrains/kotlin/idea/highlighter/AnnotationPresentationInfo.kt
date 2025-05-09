// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.MultiMap
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.base.fe10.highlighting.KotlinBaseFe10HighlightingBundle
import org.jetbrains.kotlin.idea.inspections.suppress.CompilerWarningIntentionAction
import org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressableWarningProblemGroup
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode

class AnnotationPresentationInfo(
    val ranges: List<TextRange>,
    @Nls val nonDefaultMessage: String? = null,
    val highlightType: ProblemHighlightType? = null,
    val textAttributes: TextAttributesKey? = null
) {
    companion object {
        private const val KOTLIN_COMPILER_WARNING_ID = "KotlinCompilerWarningOptions"
    }

    fun processDiagnostics(
        holder: HighlightInfoHolder,
        diagnostics: Collection<Diagnostic>,
        highlightInfoByDiagnostic: MutableMap<Diagnostic, HighlightInfo>?,
        fixesMap: MultiMap<Diagnostic, IntentionAction>,
        calculatingInProgress: Boolean
    ) {
        for (range in ranges) {
            for (diagnostic in diagnostics) {
                val group = if (diagnostic.severity == Severity.WARNING) {
                    KotlinSuppressableWarningProblemGroup(diagnostic.factory.name)
                } else {
                    null
                }
                val existingInfo = highlightInfoByDiagnostic?.get(diagnostic)
                if (existingInfo != null) {
                    if (!calculatingInProgress) {
                        applyFixes(fixesMap, diagnostic, range, builder = null, highlightInfo = existingInfo)
                    }
                } else {
                    val builder = create(diagnostic, range, group)
                    if (!calculatingInProgress || !fixesMap.isEmpty) {
                        applyFixes(fixesMap, diagnostic, range, builder = builder, highlightInfo = null)
                    }
                    val highlightInfo = builder.createUnconditionally()
                    holder.add(highlightInfo)
                    highlightInfoByDiagnostic?.put(diagnostic, highlightInfo)
                }
            }
        }
    }

    private fun create(
        diagnostic: Diagnostic,
        range: TextRange,
        group: KotlinSuppressableWarningProblemGroup?
    ): HighlightInfo.Builder {
        val message = nonDefaultMessage ?: getDefaultMessage(diagnostic)
        val textAttributesToApply = textAttributes ?: convertSeverityTextAttributes(highlightType, diagnostic.severity)
        return HighlightInfo
            .newHighlightInfo(toHighlightInfoType(highlightType, diagnostic.severity))
            .range(range)
            .description(message)
            .escapedToolTip(getMessage(diagnostic))
            .also {
                if (textAttributesToApply != null) {
                    it.textAttributes(textAttributesToApply)
                }
            }
            .also {
                if (group != null) {
                    it.problemGroup(group)
                }
            }
    }

    private fun applyFixes(
        quickFixes: MultiMap<Diagnostic, IntentionAction>,
        diagnostic: Diagnostic,
        range: TextRange,
        builder: HighlightInfo.Builder?,
        highlightInfo: HighlightInfo?,
    ) {
        val isWarning = diagnostic.severity == Severity.WARNING

        val element = diagnostic.psiElement

        val fixes = quickFixes[diagnostic].takeIf { it.isNotEmpty() }
            ?: if (isWarning) listOf(CompilerWarningIntentionAction(diagnostic.factory.name)) else emptyList()

        val keyForSuppressOptions = if (isWarning) {
            HighlightDisplayKey.findOrRegister(
                KOTLIN_COMPILER_WARNING_ID,
                KotlinBaseFe10HighlightingBundle.message("kotlin.compiler.warning")
            )
        } else null

        for (fix in fixes) {
            if (fix !is IntentionAction) {
                continue
            }

            if (fix == RegisterQuickFixesLaterIntentionAction) {
                if (builder != null) {
                    element.reference?.let {
                        UnresolvedReferenceQuickFixProvider.registerUnresolvedReferenceLazyQuickFixes(it, builder)
                    }
                    continue
                }
            }

            val isError = diagnostic.severity == Severity.ERROR
            val message = KotlinBaseFe10HighlightingBundle.message(if (isError) "kotlin.compiler.error" else "kotlin.compiler.warning")
            builder?.registerFix(fix, null, message, range, keyForSuppressOptions)
            highlightInfo?.registerFix(fix, null, message, range, keyForSuppressOptions)
        }
    }

    @NlsContexts.Tooltip
    private fun getMessage(diagnostic: Diagnostic): String {
        var message = IdeErrorMessages.render(diagnostic)
        if (isApplicationInternalMode() || isUnitTestMode()) {
            val factoryName = diagnostic.factory.name
            message = if (message.startsWith("<html>")) {
                @Suppress("HardCodedStringLiteral")
                "<html>[$factoryName] ${message.substring("<html>".length)}"
            } else {
                "[$factoryName] $message"
            }
        }
        if (!message.startsWith("<html>")) {
            message = "<html><body>${XmlStringUtil.escapeString(message)}</body></html>"
        }
        return message
    }

    private fun getDefaultMessage(diagnostic: Diagnostic): String {
        val message = DefaultErrorMessages.render(diagnostic)
        return if (isApplicationInternalMode() || isUnitTestMode()) {
            "[${diagnostic.factory.name}] $message"
        } else {
            message
        }
    }

    private fun toHighlightInfoType(highlightType: ProblemHighlightType?, severity: Severity): HighlightInfoType =
        when (highlightType) {
            ProblemHighlightType.LIKE_UNUSED_SYMBOL -> HighlightInfoType.UNUSED_SYMBOL
            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL -> HighlightInfoType.WRONG_REF
            ProblemHighlightType.LIKE_DEPRECATED -> HighlightInfoType.DEPRECATED
            ProblemHighlightType.LIKE_MARKED_FOR_REMOVAL -> HighlightInfoType.MARKED_FOR_REMOVAL
            else -> convertSeverity(highlightType, severity)
        }

    private fun convertSeverity(highlightType: ProblemHighlightType?, severity: Severity): HighlightInfoType =
        when (severity) {
            Severity.ERROR -> HighlightInfoType.ERROR
            Severity.WARNING, Severity.FIXED_WARNING -> {
                if (highlightType == ProblemHighlightType.WEAK_WARNING) {
                    HighlightInfoType.WEAK_WARNING
                } else HighlightInfoType.WARNING
            }
            Severity.INFO -> HighlightInfoType.WEAK_WARNING
        }

    private fun convertSeverityTextAttributes(highlightType: ProblemHighlightType?, severity: Severity): TextAttributesKey? =
        when (highlightType) {
            null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING ->
                when (severity) {
                    Severity.ERROR -> CodeInsightColors.ERRORS_ATTRIBUTES
                    Severity.WARNING, Severity.FIXED_WARNING -> CodeInsightColors.WARNINGS_ATTRIBUTES
                    Severity.INFO -> CodeInsightColors.WARNINGS_ATTRIBUTES
                }
            ProblemHighlightType.GENERIC_ERROR -> CodeInsightColors.ERRORS_ATTRIBUTES
            else -> null
        }

}
