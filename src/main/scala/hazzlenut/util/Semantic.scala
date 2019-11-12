package hazzlenut.util

object Semantic {
  implicit class Ops[A](instance: A) {
    def tap(f: A => Unit): A = {
      f(instance)
      instance
    }
  }
}
