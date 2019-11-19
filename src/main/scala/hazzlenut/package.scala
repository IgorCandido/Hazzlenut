import hazzlenut.errors.HazzlenutError
import zio.ZIO

package object hazzlenut {
  type HazzleNutZIO[A] = ZIO[Any, HazzlenutError, A]
}
