// CHOSEN_OPTION: CONSTRUCTOR_PARAMETER|Add use-site target 'param'

annotation class A

class Constructor(@A<caret> val foo: String)