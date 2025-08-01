/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface TreeCopyHandler {
  ExtensionPointName<TreeCopyHandler> EP_NAME = ExtensionPointName.create("com.intellij.treeCopyHandler");

  void encodeInformation(@NotNull TreeElement element, @NotNull ASTNode original, @NotNull Map<Object, Object> encodingState);
  TreeElement decodeInformation(@NotNull TreeElement element, @NotNull Map<Object, Object> decodingState);
}