// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Referencing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.ActionOrGroupPresentationProvider;
import org.jetbrains.idea.devkit.dom.impl.ActionOrGroupReferencingConverter;

import java.util.Collection;
import java.util.List;

@Presentation(provider = ActionOrGroupPresentationProvider.ForReference.class)
public interface Reference extends DomElement {

  @NotNull
  @Referencing(ActionOrGroupReferencingConverter.class)
  GenericAttributeValue<String> getRef();

  /**
   * @deprecated use {@link #getRef()} instead
   */
  @Deprecated
  @NotNull
  @Referencing(ActionOrGroupReferencingConverter.class)
  GenericAttributeValue<String> getId();

  @NotNull
  Collection<AddToGroup> getAddToGroups();

  @NotNull
  List<Synonym> getSynonyms();

  Synonym addSynonym();
}
