/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.debugger.values

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ArrayValue : ObjectValue {
  /**
   * Be aware - it is not equals to java array length.
   * In case of sparse array `var sparseArray = [3, 4];
   * sparseArray[45] = 34;
   * sparseArray[40999995] = &quot;foo&quot;;
  ` *
   * length will be equal to 40999995.
   */
  val length: Int
}