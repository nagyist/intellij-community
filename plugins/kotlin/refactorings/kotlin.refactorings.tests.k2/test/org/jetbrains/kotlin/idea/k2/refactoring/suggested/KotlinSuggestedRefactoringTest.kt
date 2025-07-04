// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.suggested

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.psi.PsiDocumentManager
import com.intellij.refactoring.suggested.BaseSuggestedRefactoringTest
import com.intellij.refactoring.suggested.SuggestedRefactoringExecution
import com.intellij.refactoring.suggested.SuggestedRefactoringProviderImpl
import com.intellij.refactoring.suggested._suggestedChangeSignatureNewParameterValuesForTests
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath

class KotlinSuggestedRefactoringTest : BaseSuggestedRefactoringTest(), ExpectedPluginModeProvider {
    override val fileType: LanguageFileType
        get() = KotlinFileType.INSTANCE

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun setUp() {
        setUpWithKotlinPlugin {
            super.setUp()
            _suggestedChangeSignatureNewParameterValuesForTests = {
                SuggestedRefactoringExecution.NewParameterValue.Expression(KtPsiFactory(project).createExpression("default$it"))
            }
        }
    }

    override fun tearDown() {
        _suggestedChangeSignatureNewParameterValuesForTests = { SuggestedRefactoringExecution.NewParameterValue.None }
        super.tearDown()
    }

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    private fun doTestContextParameters(initialText: String,
                                        expectedTextAfter: String,
                                        usagesName: String,
                                        expectedPresentation: String? = null,
                                        editingActions: () -> Unit,) {
        withCustomCompilerOptions("// COMPILER_ARGUMENTS: -Xcontext-parameters", project, module) {
            doTestChangeSignature(initialText, expectedTextAfter, usagesName, expectedPresentation, editingActions)
        }
    }

    fun testAddParameter() {
        ignoreErrorsAfter = true
        doTestChangeSignature(
            """
                fun foo(p1: Int<caret>) {
                    foo(1)
                }
                
                fun bar() {
                    foo(2)
                }
            """.trimIndent(),
            """
                fun foo(p1: Int, p2: Any<caret>) {
                    foo(1, default0)
                }
                
                fun bar() {
                    foo(2, default0)
                }
            """.trimIndent(),
            "usages",
        ) {
            type(", p2: Any")
        }
    }

    fun testAddParameterWithContexts() {
        ignoreErrorsAfter = true
        doTestContextParameters(
            """
                context(a: String, b: Int, c: Double)
                fun foo(p1: Int<caret>) {
                    foo(1)
                }
                context(a: String, b: Int, c: Double)
                fun bar() {
                    foo(2)
                }
            """.trimIndent(),
            """
                context(a: String, b: Int, c: Double)
                fun foo(p1: Int, p2: Any<caret>) {
                    foo(1, default0)
                }
                context(a: String, b: Int, c: Double)
                fun bar() {
                    foo(2, default0)
                }
            """.trimIndent(),
            "usages",
        ) {
            type(", p2: Any")
        }
    }

    fun testAddContextParameter() {
        ignoreErrorsAfter = true
        doTestContextParameters(
            """
                context(p1: Int<caret>)
                fun foo() {
                    with(1) {
                      foo()
                    }
                }
                context(p1: Int)
                fun bar() {
                    foo()
                }
            """.trimIndent(),
            """
                context(p1: Int, p2: Any)
                fun foo() {
                    with(1) {
                        with(default0) {
                            foo()
                        }
                    }
                }
                context(p1: Int)
                fun bar() {
                    with(default0) {
                        foo()
                    }
                }
            """.trimIndent(),
            "usages",
        ) {
            type(", p2: Any")
        }
    }

    fun testRemoveContextParameter() {
        ignoreErrorsAfter = true
        doTestContextParameters(
            """
                context(p1: Int, p2: Any<caret>)
                fun foo() {
                    with(1) {
                        with("any") {
                            foo()
                        }
                    }
                }
                context(p1: Int)
                fun bar() {
                    with("any") {
                        foo()
                    }
                }
            """.trimIndent(),
            """
                context(p1: Int)
                fun foo() {
                    with(1) {
                        with("any") {
                            foo()
                        }
                    }
                }
                context(p1: Int)
                fun bar() {
                    with("any") {
                        foo()
                    }
                }
            """.trimIndent(),
            "usages",
        ) {
            deleteTextBeforeCaret(", p2: Any")
        }
    }

    fun testRemoveParameter() {
        doTestChangeSignature(
            """
                fun foo(p1: Any?, p2: Int<caret>) {
                    foo(null, 1)
                }
                
                fun bar() {
                    foo(1, 2)
                }
            """.trimIndent(),
            """
                fun foo(p1: Any?<caret>) {
                    foo(null)
                }
                
                fun bar() {
                    foo(1)
                }
            """.trimIndent(),
            "usages"
        ) {
            deleteTextBeforeCaret(", p2: Int")
        }
    }

    fun testRemoveParameterWithContext() {
        doTestContextParameters(
            """
                context(a: String, b: Int, c: Double)
                fun foo(p1: Any?, p2: Int<caret>) {
                    foo(null, 1)
                }
                context(a: String, b: Int, c: Double)
                fun bar() {
                    foo(1, 2)
                }
            """.trimIndent(),
            """
                context(a: String, b: Int, c: Double)
                fun foo(p1: Any?<caret>) {
                    foo(null)
                }
                context(a: String, b: Int, c: Double)
                fun bar() {
                    foo(1)
                }
            """.trimIndent(),
            "usages"
        ) {
            deleteTextBeforeCaret(", p2: Int")
        }
    }

    fun testReorderParameters1() {
        doTestChangeSignature(
            """
                fun foo(p1: Any?, p2: Int, p3: Boolean<caret>) {
                    foo(null, 1, true)
                }
                
                fun bar() {
                    foo(1, 2, false)
                }
            """.trimIndent(),
            """
                fun foo(p3: Boolean, p1: Any?, p2: Int) {
                    foo(true, null, 1)
                }
                
                fun bar() {
                    foo(false, 1, 2)
                }
            """.trimIndent(),
            "usages",
        ) {
            performAction(IdeActions.MOVE_ELEMENT_LEFT)
            performAction(IdeActions.MOVE_ELEMENT_LEFT)
        }
    }

    fun testReorderParameters2() {
        doTestChangeSignature(
            """
                fun foo(p1: Any?, <caret>p2: Int, p3: Boolean) {
                    foo(null, 1, true)
                }
                
                fun bar() {
                    foo(1, 2, false)
                }
            """.trimIndent(),
            """
                fun foo(p2: Int, p1: Any?, p3: Boolean) {
                    foo(1, null, true)
                }
                
                fun bar() {
                    foo(2, 1, false)
                }
            """.trimIndent(),
            "usages",
        ) {
            performAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)
            performAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)
            performAction(IdeActions.ACTION_EDITOR_CUT)
            editor.caretModel.moveToOffset(editor.caretModel.offset - 10)
            performAction(IdeActions.ACTION_EDITOR_PASTE)
            type(", ")
            editor.caretModel.moveToOffset(editor.caretModel.offset + 10)
            performAction(IdeActions.ACTION_EDITOR_DELETE)
            performAction(IdeActions.ACTION_EDITOR_DELETE)
        }
    }

    fun testChangeParameterType() {
        doTestChangeSignature(
            """
                interface I {
                    fun foo(p: <caret>String)
                }
                
                class C : I {
                    override fun foo(p: String) {
                    }
                }
            """.trimIndent(),
            """
                interface I {
                    fun foo(p: <caret>Any)
                }
                
                class C : I {
                    override fun foo(p: Any) {
                    }
                }
            """.trimIndent(),
            "implementations",
        ) {
            replaceTextAtCaret("String", "Any")
        }
    }

    fun testChangeParameterTypeExpectFunction() {
        ignoreErrorsBefore = true
        ignoreErrorsAfter = true
        doTestChangeSignature(
            "expect fun foo(p: <caret>String)",
            "expect fun foo(p: <caret>Any)",
            "actual declarations",

        ) {
            replaceTextAtCaret("String", "Any")
        }
    }

    fun testUnresolvedParameterType() {
        ignoreErrorsAfter = true
        doTestChangeSignature(
            """
                interface I {
                    fun foo(p: <caret>String)
                }
                
                class C : I {
                    override fun foo(p: String) {
                    }
                }
            """.trimIndent(),
            """
                interface I {
                    fun foo(p: XXX)
                }
                
                class C : I {
                    override fun foo(p: XXX) {
                    }
                }
            """.trimIndent(),
            "implementations",
        ) {
            replaceTextAtCaret("String", "XXX")
        }
    }

    fun testChangeParameterTypeWithImportInsertion() {
        myFixture.addFileToProject(
            "X.kt",
            """
                package xxx
                class X
            """.trimIndent()
        )

        val otherFile = myFixture.addFileToProject(
            "Other.kt",
            """
                class D : I {
                    override fun foo(p: String) {
                    }
                }
            """.trimIndent()
        )

        doTestChangeSignature(
            """
                interface I {
                    fun foo(p: <caret>String)
                }
                
                class C : I {
                    override fun foo(p: String) {
                    }
                }
            """.trimIndent(),
            """
                import xxx.X
                
                interface I {
                    fun foo(p: <caret>X)
                }
                
                class C : I {
                    override fun foo(p: X) {
                    }
                }
            """.trimIndent(),
            "implementations",
        ) {
            replaceTextAtCaret("String", "X")
            addImport("xxx.X")
        }

        assertEquals(
            """
            import xxx.X
            
            class D : I {
                override fun foo(p: X) {
                }
            }
            """.trimIndent(),
            otherFile.text
        )
    }

    fun testChangeReturnType() {
        doTestChangeSignature(
            """
                interface I {
                    fun foo(): <caret>String
                }
                
                class C : I {
                    override fun foo(): String = ""
                }
            """.trimIndent(),
            """
                interface I {
                    fun foo(): <caret>Any
                }
                
                class C : I {
                    override fun foo(): Any = ""
                }
            """.trimIndent(),
            "implementations",
        ) {
            replaceTextAtCaret("String", "Any")
        }
    }

    fun testAddReturnType() {
        ignoreErrorsAfter = true
        doTestChangeSignature(
            """
                interface I {
                    fun foo()<caret>
                }
                
                class C : I {
                    override fun foo() {}
                }
            """.trimIndent(),
            """
                interface I {
                    fun foo(): String<caret>
                }
                
                class C : I {
                    override fun foo(): String {}
                }
            """.trimIndent(),
            "implementations",
        ) {
            type(": String")
        }
    }

    fun testRemoveReturnType() {
        ignoreErrorsAfter = true
        doTestChangeSignature(
            """
                interface I {
                    fun foo()<caret>: String
                }
                
                class C : I {
                    override fun foo(): String = ""
                }
            """.trimIndent(),
            """
                interface I {
                    fun foo()<caret>
                }
                
                class C : I {
                    override fun foo() = ""
                }
            """.trimIndent(),
            "implementations",
        ) {
            deleteTextAtCaret(": String")
        }
    }

    fun testRenameAndAddReturnType() {
        ignoreErrorsAfter = true
        doTestChangeSignature(
            """
                interface I {
                    fun foo<caret>()
                }
                
                class C : I {
                    override fun foo() {}
                }
            """.trimIndent(),
            """
                interface I {
                    fun foo<caret>New(): String
                }
                
                class C : I {
                    override fun fooNew(): String {}
                }
            """.trimIndent(),
            "usages",
        ) {
            editAction {
                val offset = editor.caretModel.offset
                editor.document.insertString(offset, "New")
            }
            editAction {
                val offset = editor.caretModel.offset
                editor.document.insertString(offset + "New()".length, ": String")
            }
        }
    }

    fun testChangeParameterTypeWithImportReplaced() {
        myFixture.addFileToProject(
            "XY.kt",
            """
                package xxx
                class X
                class Y
            """.trimIndent()
        )

        val otherFile = myFixture.addFileToProject(
            "Other.kt",
            """
                import xxx.X
                
                class D : I {
                    override fun foo(p: X) {
                    }
                }
            """.trimIndent()
        )

        doTestChangeSignature(
            """
                import xxx.X

                interface I {
                    fun foo(p: <caret>X)
                }
            """.trimIndent(),
            """
                import xxx.Y
                
                interface I {
                    fun foo(p: <caret>Y)
                }
            """.trimIndent(),
            "implementations",
        ) {
            editAction {
                val offset = editor.caretModel.offset
                editor.document.replaceString(offset, offset + "X".length, "Y")
            }
            addImport("xxx.Y")
            removeImport("xxx.X")
        }

        assertEquals(
            """
            import xxx.Y
            
            class D : I {
                override fun foo(p: Y) {
                }
            }
            """.trimIndent(),
            otherFile.text
        )
    }

    fun testPreserveCommentsAndFormatting() {
        doTestChangeSignature(
            """
                class A : I {
                    override fun foo() {
                    }
                }

                interface I {
                    fun foo(<caret>)
                }     
            """.trimIndent(),
            """
                class A : I {
                    override fun foo(p1: Int, p2: Long, p3: Any?) {
                    }
                }

                interface I {
                    fun foo(p1: Int/*comment 1*/, p2: Long/*comment 2*/,
                            p3: Any?/*comment 3*/<caret>)
                }     
            """.trimIndent(),
            "usages",
        ) {
            type("p1: Int/*comment 1*/")
            type(", p2: Long/*comment 2*/")
            type(",")
            performAction(IdeActions.ACTION_EDITOR_ENTER)
            type("p3: Any?/*comment 3*/")
        }
    }

    fun testParameterCompletion() {
        ignoreErrorsAfter = true
        doTestChangeSignature(
            "package ppp\nclass Abcdef\nfun foo(<caret>p: Int) { }\nfun bar() { foo(1) }",
            "package ppp\nclass Abcdef\nfun foo(abcdef: Abcdef, <caret>p: Int) { }\nfun bar() { foo(default0, 1) }",
            "usages",
        ) {
            type("abcde")
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            myFixture.completeBasic()
            myFixture.finishLookup('\n')
            type(", ")
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
    }

    fun testRenameTwoParameters() {
        doTestChangeSignature(
            """
                fun foo(<caret>p1: Int, p2: String) {
                    p1.hashCode()
                    p2.hashCode()
                }
            """.trimIndent(),
            """
                fun foo(<caret>p1New: Int, p2New: String) {
                    p1New.hashCode()
                    p2New.hashCode()
                }
            """.trimIndent(),
            "usages",
        ) {
            editAction {
                val function = (file as KtFile).declarations.single() as KtFunction
                function.valueParameters[0].setName("p1New")
                function.valueParameters[1].setName("p2New")
            }
        }
    }

    fun testChangePropertyType() {
        doTestChangeSignature(
            """
                interface I {
                    var v: <caret>String
                }
                
                class C : I {
                    override var v: String = ""
                }
            """.trimIndent(),
            """
                interface I {
                    var v: <caret>Any
                }
                
                class C : I {
                    override var v: Any = ""
                }
            """.trimIndent(),
            "implementations",
            expectedPresentation = """
                Old:
                  'var '
                  'v'
                  ': '
                  'String' (modified)
                New:
                  'var '
                  'v'
                  ': '
                  'Any' (modified)
            """.trimIndent()
        ) {
            replaceTextAtCaret("String", "Any")
        }
    }

    fun testRenameClass() {
        doTestRename(
            """
                var v: C? = null

                interface C<caret> 
            """.trimIndent(),
            """
                var v: CNew? = null

                interface CNew<caret> 
            """.trimIndent(),
            "C",
            "CNew",
        ) {
            type("New")
        }
    }

    fun testRenameLocalVar() {
        doTestRename(
            """
                fun foo() {
                    var v<caret> = 0
                    v++
                }
            """.trimIndent(),
            """
                fun foo() {
                    var vNew<caret> = 0
                    vNew++
                }
            """.trimIndent(),
            "v",
            "vNew",
        ) {
            type("New")
        }
    }

    fun testRenameComponentVar() {
        doTestRename(
            """
                data class Pair<T1, T2>(val t1: T1, val t2: T2)
                
                fun f(): Pair<String, Int> = Pair("a", 1)
                
                fun g() {
                    val (a, b<caret>) = f()
                    b.hashCode()
                }
            """.trimIndent(),
            """
                data class Pair<T1, T2>(val t1: T1, val t2: T2)

                fun f(): Pair<String, Int> = Pair("a", 1)
                
                fun g() {
                    val (a, b1<caret>) = f()
                    b1.hashCode()
                }
            """.trimIndent(),
            "b",
            "b1",
        ) {
            type("1")
        }
    }

    fun testRenameLoopVar() {
        doTestRename(
            """
                fun f(list: List<String>) {
                    for (s<caret> in list) {
                        s.hashCode()
                    }
                }
            """.trimIndent(),
            """
                fun f(list: List<String>) {
                    for (s1<caret> in list) {
                        s1.hashCode()
                    }
                }
            """.trimIndent(),
            "s",
            "s1",
        ) {
            type("1")
        }
    }

    fun testRenameParameter() {
        doTestRename(
            """
                fun foo(p<caret>: String) {
                    p.hashCode()
                }
            """.trimIndent(),
            """
                fun foo(pNew<caret>: String) {
                    pNew.hashCode()
                }
            """.trimIndent(),
            "p",
            "pNew",
        ) {
            type("New")
        }
    }

    fun testRenameParametersInOverrides() {
        doTestRename(
            """
                interface I {
                    fun foo(p<caret>: String)
                }
                    
                class C : I {
                    override fun foo(p: String) {
                        p.hashCode()
                    }    
                }    
            """.trimIndent(),
            """
                interface I {
                    fun foo(pNew<caret>: String)
                }
                    
                class C : I {
                    override fun foo(pNew: String) {
                        pNew.hashCode()
                    }    
                }    
            """.trimIndent(),
            "p",
            "pNew",
        ) {
            type("New")
        }
    }

    fun testAddPrimaryConstructorParameter() {
        ignoreErrorsAfter = true
        doTestChangeSignature(
            """
                class C(private val x: String, y: Int<caret>)

                fun foo() {
                    val c = C("a", 1)
                }    
            """.trimIndent(),
            """
                class C(private val x: String, y: Int, z: Any<caret>)

                fun foo() {
                    val c = C("a", 1, default0)
                }    
            """.trimIndent(),
            "usages",
            expectedPresentation = """
                Old:
                  'C'
                  '('
                  LineBreak('', true)
                  Group:
                    'x'
                    ': '
                    'String'
                  ','
                  LineBreak(' ', true)
                  Group:
                    'y'
                    ': '
                    'Int'
                  LineBreak('', false)
                  ')'
                New:
                  'C'
                  '('
                  LineBreak('', true)
                  Group:
                    'x'
                    ': '
                    'String'
                  ','
                  LineBreak(' ', true)
                  Group:
                    'y'
                    ': '
                    'Int'
                  ','
                  LineBreak(' ', true)
                  Group (added):
                    'z'
                    ': '
                    'Any'
                  LineBreak('', false)
                  ')'
              """.trimIndent()
        ) {
            type(", z: Any")
        }
    }

    fun testAddSecondaryConstructorParameter() {
        ignoreErrorsAfter = true
        doTestChangeSignature(
            """
                class C {
                    constructor(x: String<caret>)
                }

                fun foo() {
                    val c = C("a")
                }    
            """.trimIndent(),
            """
                class C {
                    constructor(x: String, y: Int<caret>)
                }

                fun foo() {
                    val c = C("a", default0)
                }    
            """.trimIndent(),
            "usages",
            expectedPresentation = """
                Old:
                  'constructor'
                  '('
                  LineBreak('', true)
                  Group:
                    'x'
                    ': '
                    'String'
                  LineBreak('', false)
                  ')'
                New:
                  'constructor'
                  '('
                  LineBreak('', true)
                  Group:
                    'x'
                    ': '
                    'String'
                  ','
                  LineBreak(' ', true)
                  Group (added):
                    'y'
                    ': '
                    'Int'
                  LineBreak('', false)
                  ')'
            """.trimIndent()
        ) {
            type(", y: Int")
        }
    }

    fun testAddReceiver() {
        doTestChangeSignature(
            """
                interface I {
                    fun <caret>foo()
                }
                
                class C : I {
                    override fun foo() {
                    }
                }
            """.trimIndent(),
            """
                interface I {
                    fun Int.<caret>foo()
                }
                
                class C : I {
                    override fun Int.foo() {
                    }
                }
            """.trimIndent(),
            "usages",
            expectedPresentation = """
                Old:
                  'fun '
                  'foo'
                  '('
                  LineBreak('', false)
                  ')'
                New:
                  'fun '
                  'Int.' (added)
                  'foo'
                  '('
                  LineBreak('', false)
                  ')'
            """.trimIndent()
        )
        {
            type("Int.")
        }
    }

    fun testAddReceiverAndParameter() {
        ignoreErrorsAfter = true
        doTestChangeSignature(
            """
                fun <caret>foo() {
                }
                
                fun bar() {
                    foo()
                }
            """.trimIndent(),
            """
                fun Int.foo(o: Any<caret>) {
                }
                
                fun bar() {
                    default0.foo(default1)
                }
            """.trimIndent(),
            "usages",
        ) {
            type("Int.")
            repeat("foo(".length) {
                performAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT)
            }
            type("o: Any")
        }
    }

    fun testRemoveReceiver() {
        doTestChangeSignature(
            """
                fun <caret>Int.foo() {
                }
                
                fun bar() {
                    1.foo()
                }
            """.trimIndent(),
            """
                fun <caret>foo() {
                }
                
                fun bar() {
                    foo()
                }
            """.trimIndent(),
            "usages",
        ) {
            repeat(4) {
                performAction(IdeActions.ACTION_EDITOR_DELETE)
            }
        }
    }

    fun testChangeReceiverType() {
        doTestChangeSignature(
            """
                interface I {
                    fun <caret>Int.foo()
                }
                
                class C : I {
                    override fun Int.foo() {
                    }
                }
                
                fun I.f() {
                    1.foo()
                }
            """.trimIndent(),
            """
                interface I {
                    fun Any<caret>.foo()
                }
                
                class C : I {
                    override fun Any.foo() {
                    }
                }
                
                fun I.f() {
                    1.foo()
                }
            """.trimIndent(),
            "implementations",
            expectedPresentation = """
                Old:
                  'fun '
                  'Int' (modified)
                  '.'
                  'foo'
                  '('
                  LineBreak('', false)
                  ')'
                New:
                  'fun '
                  'Any' (modified)
                  '.'
                  'foo'
                  '('
                  LineBreak('', false)
                  ')'
            """.trimIndent()
        ) {
            repeat("Int".length) {
                performAction(IdeActions.ACTION_EDITOR_DELETE)
            }
            type("Any")
        }
    }

    fun testChangeReceiverTypeAndRemoveParameter() {
        doTestChangeSignature(
            """
                fun <caret>Int.foo(p: Any) {}
                
                fun bar() {
                    1.foo("a")
                }
            """.trimIndent(),
            """
                fun Any.foo() {}
                
                fun bar() {
                    1.foo()
                }
            """.trimIndent(),
            "usages",
        ) {
            repeat("Int".length) {
                performAction(IdeActions.ACTION_EDITOR_DELETE)
            }
            type("Any")
            editor.caretModel.moveToOffset(editor.caretModel.offset + ".foo(".length)
            repeat("p: Any".length) {
                performAction(IdeActions.ACTION_EDITOR_DELETE)
            }
        }
    }

    fun testAddVarargParameter() {
        doTestChangeSignature(
            """
                interface I {
                    fun foo(p: Int<caret>)
                }
                    
                class C : I {
                    override fun foo(p: Int) {
                    }
                }
            """.trimIndent(),
            """
                interface I {
                    fun foo(p: Int, vararg s: String<caret>)
                }
                    
                class C : I {
                    override fun foo(p: Int, vararg s: String) {
                    }
                }
            """.trimIndent(),
            "usages",
            expectedPresentation = """
                Old:
                  'fun '
                  'foo'
                  '('
                  LineBreak('', true)
                  Group:
                    'p'
                    ': '
                    'Int'
                  LineBreak('', false)
                  ')'
                New:
                  'fun '
                  'foo'
                  '('
                  LineBreak('', true)
                  Group:
                    'p'
                    ': '
                    'Int'
                  ','
                  LineBreak(' ', true)
                  Group (added):
                    'vararg'
                    ' '
                    's'
                    ': '
                    'String'
                  LineBreak('', false)
                  ')'
            """.trimIndent()
        ) {
            type(", vararg s: String")
        }
    }

    //TODO
/*
    fun testAddVarargModifier() {
        doTestChangeSignature(
            """
                interface I {
                    fun foo(<caret>p: Int)
                }
                    
                class C : I {
                    override fun foo(p: Int) {
                    }
                }
            """.trimIndent(),
            """
                interface I {
                    fun foo(vararg <caret>p: Int)
                }
                    
                class C : I {
                    override fun foo(vararg p: Int) {
                    }
                }
            """.trimIndent(),
            { type("vararg ") },
            expectedPresentation = """
                Old:
                  'fun '
                  'foo'
                  '('
                  LineBreak('', true)
                  Group:
                    'p'
                    ': '
                    'Int'
                  LineBreak('', false)
                  ')'
                New:
                  'fun '
                  'foo'
                  '('
                  LineBreak('', true)
                  Group:
                    'vararg' (added)
                    ' '
                    'p'
                    ': '
                    'Int'
                  LineBreak('', false)
                  ')'
            """.trimIndent()
        )
    }

    fun testRemoveVarargModifier() {
        doTestChangeSignature(
            """
                interface I {
                    fun foo(<caret>vararg p: Int)
                }
                    
                class C : I {
                    override fun foo(vararg p: Int) {
                    }
                }
            """.trimIndent(),
            """
                interface I {
                    fun foo(<caret>p: Int)
                }
                    
                class C : I {
                    override fun foo(p: Int) {
                    }
                }
            """.trimIndent(),
            {
                deleteStringAtCaret("vararg ")
            },
            expectedPresentation = """
                Old:
                  'fun '
                  'foo'
                  '('
                  LineBreak('', true)
                  Group:
                    'vararg' (removed)
                    ' '
                    'p'
                    ': '
                    'Int'
                  LineBreak('', false)
                  ')'
                New:
                  'fun '
                  'foo'
                  '('
                  LineBreak('', true)
                  Group:
                    'p'
                    ': '
                    'Int'
                  LineBreak('', false)
                  ')'
            """.trimIndent()
        )
    }
*/

    fun testSwapConstructorParameters() {
        doTestChangeSignature(
            """
                class C(
                    p1: Int,
                    p2: String<caret>
                )
                
                fun foo() {
                    C(1, "")
                }
            """.trimIndent(),
            """
                class C(
                    p2: String,
                    p1: Int
                )
                
                fun foo() {
                    C("", 1)
                }
            """.trimIndent(),
            "usages",
        ) {
            performAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION)
        }
    }

    fun testChangeParameterTypeOfVirtualExtensionMethod() {
        doTestChangeSignature(
            """
                abstract class Base {
                    protected abstract fun Int.foo(p: <caret>String)
                    
                    fun bar() {
                        1.foo("a")
                    }
                }
                
                class Derived : Base() {
                    override fun Int.foo(p: String) {
                    }
                }
            """.trimIndent(),
            """
                abstract class Base {
                    protected abstract fun Int.foo(p: <caret>Any)
                    
                    fun bar() {
                        1.foo("a")
                    }
                }
                
                class Derived : Base() {
                    override fun Int.foo(p: Any) {
                    }
                }
            """.trimIndent(),
            "implementations",
        ) {
            replaceTextAtCaret("String", "Any")
        }
    }

    fun testAddParameterToVirtualExtensionMethod() {
        ignoreErrorsAfter = true
        doTestChangeSignature(
            """
                abstract class Base {
                    protected abstract fun Int.foo(p: String<caret>)
                    
                    fun bar() {
                        1.foo("a")
                    }
                }
                
                class Derived : Base() {
                    override fun Int.foo(p: String) {
                    }
                }
            """.trimIndent(),
            """
                abstract class Base {
                    protected abstract fun Int.foo(p: String, p1: Int<caret>)
                    
                    fun bar() {
                        1.foo("a", default0)
                    }
                }
                
                class Derived : Base() {
                    override fun Int.foo(p: String, p1: Int) {
                    }
                }
            """.trimIndent(),
            "usages",
        ) {
            type(", p1: Int")
        }
    }
    
    fun testAddParameterWithFullyQualifiedType() {
        doTestChangeSignature(
            """
                interface I {
                    fun foo(<caret>) 
                }
                
                class C : I {
                    override fun foo() {
                    }
                }
            """.trimIndent(),
            """
                import java.io.InputStream
                
                interface I {
                    fun foo(p: java.io.InputStream) 
                }
                
                class C : I {
                    override fun foo(p: InputStream) {
                    }
                }
            """.trimIndent(),
            "usages",
        ) {
            type("p: java.io.InputStream")
        }
    }

    fun testAddOptionalParameter() {
        doTestChangeSignature(
            """
                interface I {
                    fun foo(p1: Int<caret>)
                }
                
                class C : I {
                    override fun foo(p1: Int) {
                    }
                }
                
                fun f(i: I) {
                    i.foo(1)
                }
            """.trimIndent(),
            """
                interface I {
                    fun foo(p1: Int, p2: Int = 10<caret>)
                }
                
                class C : I {
                    override fun foo(p1: Int, p2: Int) {
                    }
                }
                
                fun f(i: I) {
                    i.foo(1)
                }
            """.trimIndent(),
            "implementations",
            expectedPresentation = """
                Old:
                  'fun '
                  'foo'
                  '('
                  LineBreak('', true)
                  Group:
                    'p1'
                    ': '
                    'Int'
                  LineBreak('', false)
                  ')'
                New:
                  'fun '
                  'foo'
                  '('
                  LineBreak('', true)
                  Group:
                    'p1'
                    ': '
                    'Int'
                  ','
                  LineBreak(' ', true)
                  Group (added):
                    'p2'
                    ': '
                    'Int'
                    ' = '
                    '10'
                  LineBreak('', false)
                  ')'
            """.trimIndent()
        ) {
            type(", p2: Int = 10")
        }
    }

    fun testReorderOptionalParameter() {
        doTestChangeSignature(
            """
                interface I {
                    fun foo(<caret>p1: Int, p2: Int = 10)
                }
                
                class C : I {
                    override fun foo(p1: Int, p2: Int) {
                    }
                }
                
                fun f(i: I) {
                    i.foo(2)
                }
            """.trimIndent(),
            """
                interface I {
                    fun foo(p2: Int = 10, <caret>p1: Int)
                }
                
                class C : I {
                    override fun foo(p2: Int, p1: Int) {
                    }
                }
                
                fun f(i: I) {
                    i.foo(p1 = 2)
                }
            """.trimIndent(),
            "usages",
            expectedPresentation = """
                Old:
                  'fun '
                  'foo'
                  '('
                  LineBreak('', true)
                  Group (moved):
                    'p1'
                    ': '
                    'Int'
                  ','
                  LineBreak(' ', true)
                  Group:
                    'p2'
                    ': '
                    'Int'
                    ' = '
                    '10'
                  LineBreak('', false)
                  ')'
                New:
                  'fun '
                  'foo'
                  '('
                  LineBreak('', true)
                  Group:
                    'p2'
                    ': '
                    'Int'
                    ' = '
                    '10'
                  ','
                  LineBreak(' ', true)
                  Group (moved):
                    'p1'
                    ': '
                    'Int'
                  LineBreak('', false)
                  ')'
            """.trimIndent()
        ) {
            performAction(IdeActions.MOVE_ELEMENT_RIGHT)
        }
    }

    fun testReplaceTypeWithItsAlias() {
        doTestChangeSignature(
            """
                typealias StringToUnit = (String) -> Unit
                
                interface I {
                    fun foo(): <caret>(String) -> Unit
                }
                
                class C : I {
                    override fun foo(): (String) -> Unit {
                        throw UnsupportedOperationException()
                    }
                }
            """.trimIndent(),
            """
                typealias StringToUnit = (String) -> Unit
                
                interface I {
                    fun foo(): <caret>StringToUnit
                }
                
                class C : I {
                    override fun foo(): StringToUnit {
                        throw UnsupportedOperationException()
                    }
                }
            """.trimIndent(),
            "implementations",
            expectedPresentation = """
                Old:
                  'fun '
                  'foo'
                  '('
                  LineBreak('', false)
                  ')'
                  ': '
                  '(String) -> Unit' (modified)
                New:
                  'fun '
                  'foo'
                  '('
                  LineBreak('', false)
                  ')'
                  ': '
                  'StringToUnit' (modified)
            """.trimIndent()
        ) {
            replaceTextAtCaret("(String) -> Unit", "StringToUnit")
        }
    }

    fun testNewParameterValueReferencesAnotherParameter() {
        _suggestedChangeSignatureNewParameterValuesForTests = {
            val declaration = SuggestedRefactoringProviderImpl.getInstance(project).state!!.declaration
            val codeFragment = KtPsiFactory(project).createExpressionCodeFragment("p1 * p1", declaration)
            SuggestedRefactoringExecution.NewParameterValue.Expression(codeFragment.getContentElement()!!)
        }
        doTestChangeSignature(
            """
                fun foo(p1: Int<caret>) {
                }
                
                fun bar() {
                    foo(1)
                    foo(2)
                }
            """.trimIndent(),
            """
                fun foo(p1: Int, p2: Int<caret>) {
                }
                
                fun bar() {
                    foo(1, 1 * 1)
                    foo(2, 2 * 2)
                }
            """.trimIndent(),
            "usages")
        {
            type(", p2: Int")
        }
    }

    private fun addImport(fqName: String) = editAction {
        (file as KtFile).importList!!.add(KtPsiFactory(project).createImportDirective(ImportPath.fromString(fqName)))
    }

    private fun removeImport(fqName: String) = editAction {
        (file as KtFile).importList!!.imports.first { it.importedFqName?.asString() == fqName }.delete()
    }
}