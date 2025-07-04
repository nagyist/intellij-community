// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.search

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolService
import com.intellij.navigation.SymbolNavigationService
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.polySymbols.PolySymbol
import com.intellij.psi.PsiElement

/**
 * Should be implemented by [PolySymbol] if its declaration is a regular [com.intellij.psi.PsiElement], e.g. a variable or a declared type.
 * Once a symbol implements this interface it can be searched and refactored together with the PSI element declaration.
 * If your symbol is part of a [com.intellij.psi.PsiElement] (e.g. part of a string literal), or spans multiple PSI elements,
 * or does not relate 1-1 with a PSI element, instead of implementing this interface you should contribute
 * dedicated declaration provider.
 *
 * To properly support search and rename refactoring for symbols, which names can be modified
 * by [com.intellij.polySymbols.query.PolySymbolNameConversionRules], a `PsiSourcedPolySymbolProvider`
 * should be implemented to allow the framework to search for alternative names.
 *
 * See also: [Declarations, References, Search, Refactoring](https://plugins.jetbrains.com/docs/intellij/websymbols-implementation.html#declarations-references-search-refactoring)
 */
interface PsiSourcedPolySymbol : PolySymbol {

  override val psiContext: PsiElement?
    get() = source

  /**
   * The [PsiElement], which is the symbol declaration.
   */
  val source: PsiElement?
    get() = null

  override fun createPointer(): Pointer<out PsiSourcedPolySymbol>

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
    source?.let { listOf(SymbolNavigationService.getInstance().psiElementNavigationTarget(it)) } ?: emptyList()

  override fun isEquivalentTo(symbol: Symbol): Boolean {
    if (this == symbol) return true
    val source = this.source ?: return false
    val target = PsiSymbolService.getInstance().extractElementFromSymbol(symbol)
    return when {
      target != null -> target.manager.areElementsEquivalent(source, target)
      symbol is PsiSourcedPolySymbol -> source == symbol.source
      else -> false
    }
  }

}