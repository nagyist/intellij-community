// "Replace with 'newFun(p4, p3, p2, p1, p0)'" "true"

@Deprecated("", ReplaceWith("newFun(p4, p3, p2, p1, p0)"))
fun oldFun(p0: Int, p1: Int, p2: Int, p3: Int, p4: Int){}

fun newFun(p4: Int, p3: Int, p2: Int, p1: Int, p0: Int){}


fun foo() {
    <caret>oldFun(/* 0 */ 0,
                  /* 1 */ 1,
                  2, // 2
                  3, /* 4 */ 4)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix