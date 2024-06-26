// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows marking some elements as implicitly used.
 * The "Unused declaration" inspection will treat <b>all code</b> reachable from these elements as used.
 * <p>
 * 
 * Entry points can be configured by the user. Common examples are main methods, tests, etc.
 * 
 * @see com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase
 * @see com.intellij.codeInsight.daemon.ImplicitUsageProvider
 */
public abstract class EntryPoint implements JDOMExternalizable , Cloneable {
  private static final Logger LOG = Logger.getInstance(EntryPoint.class);

  /**
   * @return presentable name to be shown at unused declaration's settings page, under the "Entry Points" tab 
   */
  public abstract @NotNull @Nls String getDisplayName();

  /**
   * @param refElement element in ref graph
   * @param psiElement corresponding psi element, 
   * @return true if the specified element should be treated as an entry point
   */
  public abstract boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement);
  public abstract boolean isEntryPoint(@NotNull PsiElement psiElement);

  /**
   * if entry point is enabled by user or not
   */
  public abstract boolean isSelected();
  public abstract void setSelected(boolean selected);

  public boolean showUI() {
    return true;
  }

  /**
   * @return annotations which signal that element is used. 
   *         In batch mode, elements with these annotations are added to the entry points of the run
   */
  public String @Nullable [] getIgnoreAnnotations() {
    return null;
  }

  @Override
  public EntryPoint clone() throws CloneNotSupportedException {
    final EntryPoint clone = (EntryPoint)super.clone();
    final Element element = new Element("root");
    try {
      writeExternal(element);
      clone.readExternal(element);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return clone;
  }
}
