// "Create class 'A'" "true"
package p

class B {

}

class Foo: <caret>A(abc = 1, ghi = "2", def = B()) {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction