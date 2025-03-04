import java.util.function.*;

class Test {
  void test1(Object o, int mode) {
    switch (o) {
      case Integer i when i == <error descr="Variable used in guard expression should be final or effectively final">mode</error> -> System.out.println();
      default -> {}
    }

    switch (o) {
      case Integer i when (switch (o) {
        case Integer ii when ii != <error descr="Variable used in guard expression should be final or effectively final">mode</error> -> 2;
        default -> 1;
      }) == <error descr="Variable used in guard expression should be final or effectively final">mode</error> -> System.out.println();
      default -> {}
    }

    switch (o) {
      case Integer i when (i = <error descr="Variable used in guard expression should be final or effectively final">mode</error>) > 0 -> System.out.println();
      default -> {}
    }
    mode = 0;
  }

  void test2(Object o, final int mode) {
    switch (o) {
      case Integer i when (switch (<error descr="Variable used in guard expression should be final or effectively final">o</error>) {
        case Integer ii when ii != mode -> 2;
        default -> 1;
      }) == mode -> o = null;
        default -> {}
    }
    switch (o) {
      case Integer i when (<error descr="Cannot assign a value to variable 'i', because it is declared outside the guard">i</error> = mode) > 0 -> System.out.println();
      default -> {}
    }
  }

  void test3(Object o, int mode) {
    switch (o) {
      case Integer i when i == mode -> System.out.println();
      default -> {}
    }
    switch (o) {
      case Integer i when (switch (o) {
        case Integer ii when ii != mode -> 2;
        default -> 1;
      }) == mode -> System.out.println();
      default -> {}
    }
  }

  void testInstanceofPatterns(Object o, int mode) {
    if (o instanceof Integer i && (i = mode) > 0) {
    }
    mode = 0;
  }

  void testNested(Object o, Integer in, AutoCloseable in1) {
    switch (o) {
      case Integer mode when (<error descr="Cannot assign a value to variable 'mode', because it is declared outside the guard">mode</error> = 42) > 9:
        switch (o) {
          case Integer i when (i = <error descr="Variable used in guard expression should be final or effectively final">mode</error>) > 0 -> System.out.println();
          default -> System.out.println();
        }
      default : break;
    }
    String str;
    str = switch (o) {
      case Integer mode when (<error descr="Cannot assign a value to variable 'mode', because it is declared outside the guard">mode</error> = 42) > 9 ->
        switch (o) {
          case Integer i when (i = <error descr="Variable used in guard expression should be final or effectively final">mode</error>) > 0 -> "";
          default -> "";
        };
      default -> "";
    };
    str = switch (o) {
      case Integer mode when (<error descr="Cannot assign a value to variable 'mode', because it is declared outside the guard">mode</error> = 42) > 9:
        yield switch (o) {
          case Integer i when (i = <error descr="Variable used in guard expression should be final or effectively final">mode</error>) > 0 -> "";
          default -> "";
        };
      default: yield "";
    };
    // lambdas
    str = switch (o) {
      case Integer i when (i = <error descr="Variable used in guard expression should be final or effectively final">in</error>) > 0:
        yield ((Function<Integer, String>)(x) -> (<error descr="Variable used in lambda expression should be final or effectively final">in</error> = 5) > 0 ? "" : null).apply(in);
      default:
        yield "";
    };
    Consumer<Integer> c = (mode) -> {
      switch (o) {
        case Integer i when (i = <error descr="Variable used in guard expression should be final or effectively final">in</error>) > 0 -> System.out.println();
        default -> System.out.println();
      }
      <error descr="Variable used in lambda expression should be final or effectively final">in</error> = 1;
    };
    // try-with-resources
    try (<error descr="Variable used as a try-with-resources resource should be final or effectively final">in1</error>) {
      switch (o) {
        case AutoCloseable ii when (<error descr="Cannot assign a value to variable 'in1', because it is declared outside the guard">in1</error> = ii) != null: break;
        default: break;
      }
    } catch (Exception e) {
    }
    // double nested
    switch (o) {
      case Integer mode when (<error descr="Cannot assign a value to variable 'mode', because it is declared outside the guard">mode</error> = 42) > 9:
        switch (o) {
          case Integer i -> {
            switch (o) {
              case Integer ii when ii > <error descr="Variable used in guard expression should be final or effectively final">mode</error>:
                break;
              default:
                break;
            }
          }
          default -> System.out.println();
        }
      default:
        break;
    }
    str = switch (o) {
      case Integer mode when (mode) > 9:
        yield switch (o) {
          case Integer i -> {
            yield switch (o) {
              case Integer ii when ii > mode: yield "";
              default: yield "";
            };
          }
          default -> "";
        };
      default: yield "";
    };
  }

  void declaredInWhenExpression(Object obj) {
    switch (obj) {
      case Integer i when new Function<Integer, Boolean>() {
        @Override
        public Boolean apply(Integer integer) {
          System.out.println(integer++);
          int num = 0;
          System.out.println(++num);
          return true;
        }
      }.apply(42) -> {}
      default -> {}
    }

    switch (obj) {
      case Integer i when switch (i) {
        case 1 -> {
          int num = 0;
          ++num;
          yield num;
        }
        default -> 42;
      } == 42 -> {}
      default -> {}
    }
  }

  void testNested(Object o, Integer in) {
    switch (o) {
      case Integer mode when (<error descr="Cannot assign a value to variable 'mode', because it is declared outside the guard">mode</error>--) > 9:
        break;
      default:
        break;
    }
  }
  public static void testWhenReassigned() {
    Object object = "1234";
    switch (object) {
      case String s when <error descr="Variable used in guard expression should be final or effectively final">s</error>.length()==2 -> {
        s = null;
      }
      default -> {
      }
    }
  }

  void testUnary(Object o, Integer in) {
    switch (o) {
      case Integer mode when -in == 1 && -mode == 2:
        break;
      default:
        break;
    }
  }
}