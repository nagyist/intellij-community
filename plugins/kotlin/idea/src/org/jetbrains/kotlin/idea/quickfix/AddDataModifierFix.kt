// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.core.isVisible
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall

internal class AddDataModifierFix(element: KtClass, private val fqName: String) : AddModifierFix(element, KtTokens.DATA_KEYWORD) {

    override fun getPresentation(context: ActionContext, element: KtModifierListOwner): Presentation =
        Presentation.of(KotlinBundle.message("fix.make.data.class", fqName))

    companion object : KotlinSingleIntentionActionFactory() {

        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val element = diagnostic.psiElement as? KtExpression ?: return null
            val context = element.analyze()

            val callableDescriptor = if (element is KtDestructuringDeclarationEntry) {
                context[BindingContext.DECLARATION_TO_DESCRIPTOR, element.parent.parent] as? CallableDescriptor
            } else {
                element.getResolvedCall(context)?.resultingDescriptor
            }

            val constructor = callableDescriptor?.returnType?.arguments?.firstOrNull()?.type?.constructor
                ?: callableDescriptor?.returnType?.constructor

            val classDescriptor = constructor?.declarationDescriptor as? ClassDescriptor ?: return null

            val modality = classDescriptor.modality
            if (modality != Modality.FINAL || classDescriptor.isInner) return null
            val ctorParams = classDescriptor.constructors.firstOrNull { it.isPrimary }?.valueParameters ?: return null
            if (ctorParams.isEmpty()) return null

            if (!ctorParams.all {
                    if (it.varargElementType != null) return@all false
                    val property = context[BindingContext.VALUE_PARAMETER_AS_PROPERTY, it] ?: return@all false
                    // NB: we use element as receiver because element is a constructor call
                    // which is effectively used as receiver by destructuring declaration
                    property.isVisible(element, element, context, element.getResolutionFacade())
                }
            ) return null

            val klass = DescriptorToSourceUtils.descriptorToDeclaration(classDescriptor) as? KtClass ?: return null
            val fqName = DescriptorUtils.getFqName(classDescriptor).asString()
            return AddDataModifierFix(klass, fqName).asIntention()
        }

    }

}
