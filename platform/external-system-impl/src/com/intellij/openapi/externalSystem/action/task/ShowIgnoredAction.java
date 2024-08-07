/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.action.task;

import com.intellij.openapi.externalSystem.action.ExternalSystemViewGearAction;
import com.intellij.openapi.externalSystem.view.ExternalProjectsViewImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public class ShowIgnoredAction extends ExternalSystemViewGearAction {
  @Override
  protected boolean isSelected(@NotNull ExternalProjectsViewImpl view) {
    return view.getShowIgnored();
  }

  @Override
  protected void setSelected(@NotNull ExternalProjectsViewImpl view, boolean value) {
    view.setShowIgnored(value);
  }
}
