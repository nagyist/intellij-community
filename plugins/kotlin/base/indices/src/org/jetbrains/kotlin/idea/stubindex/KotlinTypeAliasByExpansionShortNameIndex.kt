// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtTypeAlias

class KotlinTypeAliasByExpansionShortNameIndex internal constructor() : StringStubIndexExtension<KtTypeAlias>() {
    companion object Helper : KotlinStringStubIndexHelper<KtTypeAlias>(KtTypeAlias::class.java) {
        @JvmField
        @ApiStatus.ScheduledForRemoval
        @Deprecated("Use the Helper object instead", level = DeprecationLevel.ERROR)
        val INSTANCE: KotlinTypeAliasByExpansionShortNameIndex = KotlinTypeAliasByExpansionShortNameIndex()

        override val indexKey: StubIndexKey<String, KtTypeAlias> =
            StubIndexKey.createIndexKey(KotlinTypeAliasByExpansionShortNameIndex::class.java.simpleName)
    }

    override fun getKey(): StubIndexKey<String, KtTypeAlias> = indexKey

    @Deprecated("Base method is deprecated", ReplaceWith("KotlinTypeAliasByExpansionShortNameIndex[key, project, scope]"))
    override fun get(key: String, project: Project, scope: GlobalSearchScope): Collection<KtTypeAlias> {
        return Helper[key, project, scope]
    }
}