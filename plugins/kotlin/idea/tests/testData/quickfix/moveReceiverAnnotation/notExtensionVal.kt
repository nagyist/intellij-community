// "Move annotation to receiver type" "false"
// ERROR: This annotation is not applicable to target 'declaration' and use site target '@receiver'
// ACTION: Add annotation target
// ACTION: Enable option 'Property types' for 'Types' inlay hints
// ACTION: Introduce import alias
// ACTION: Make internal
// ACTION: Make private
// ACTION: Specify type explicitly

annotation class Ann

@receiver:Ann<caret>
val bar get() = ""