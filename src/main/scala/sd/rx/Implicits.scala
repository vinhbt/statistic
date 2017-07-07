package sd.rx

import rx.{ Observer, Observable }

import scala.concurrent.{ Promise, Future }

object Implicits {
  //  private final class FutureObserver[T](p: Promise[Seq[T]]) extends Observer[T] {
  //    def onCompleted(): Unit = {}
  //    def onNext(t: T): Unit = p success t
  //    def onError(e: Throwable): Unit = p failure e
  //  }
  //
  //  implicit class ScalaObservable[T](val underlying: Observable[T]) extends AnyVal {
  //    /** @note if `underlying`:
  //      *       + is empty then `toFuture` will fail with NoSuchElementException("Sequence contains no elements")
  //      *       + emit more than one values then `toFuture` will fail with IllegalArgumentException("Sequence contains too many elements") */
  //    def toFutureOfSeq: Future[Seq[T]] = {
  //      val p = Promise[Seq[T]]()
  //      underlying.tosubscribe(new FutureObserver(p))
  //      p.future
  //    }
  //  }
}
