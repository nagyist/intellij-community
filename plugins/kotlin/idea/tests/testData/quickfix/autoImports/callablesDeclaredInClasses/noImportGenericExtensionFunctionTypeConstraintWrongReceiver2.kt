// "Import" "false"
// ACTION: Create extension function 'A.bar'
// ACTION: Create function 'bar'
// ACTION: Create member function 'A.bar'
// ACTION: Rename reference
// ERROR: Unresolved reference: bar
// K2_AFTER_ERROR: Unresolved reference 'bar'.
package p

open class A
open class B : A()

fun A.foo() {
    <caret>bar()
}

open class C {
    fun <T : B> T.bar() {}
}

object CObject : C()
