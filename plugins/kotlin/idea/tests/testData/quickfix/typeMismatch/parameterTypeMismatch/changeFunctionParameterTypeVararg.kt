// "Change parameter 'args' type of function 'bar' to 'Array<String>'" "true"
// WITH_STDLIB
fun foo(list: List<String>) {
    bar(list.toType<caret>dArray())
}

fun bar(vararg args: String) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeParameterTypeFix