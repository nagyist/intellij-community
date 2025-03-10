// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nullable;

/**
 * EP to register counter collector in plugin.xml
 * <br/><br/>
 *
 * To use new style API define "implementationClass" field using {@code com.intellij.statistics.counterUsagesCollector} extension point.
 * See fus-collectors.md for more information.<br/>
 *
 * "groupId" and "version" fields are deprecated.
 */
public final class CounterUsageCollectorEP {
  /**
   * @deprecated Please use {@link CounterUsageCollectorEP#implementationClass} and {@link CounterUsagesCollector#getGroup()}.
   */
  @Deprecated(forRemoval = true)
  @Attribute("groupId")
  public String groupID;

  /**
   * @deprecated Please use {@link CounterUsageCollectorEP#implementationClass} and {@link CounterUsagesCollector#getGroup()}.
   */
  @Deprecated(forRemoval = true)
  @Attribute("version")
  public int version = 1;

  @Attribute("implementationClass")
  public String implementationClass;

  public @Nullable String getGroupId() {
    return groupID;
  }
}
