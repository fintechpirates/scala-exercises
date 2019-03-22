import java.util.concurrent.{Callable, ExecutorService, Future, TimeUnit}

type Par[A] = ExecutorService => Future[A]

object Par {

  private case class UnitFuture[A](get: A) extends Future[A] {
    def isDone: Boolean = true
    def get(timeout: Long, units: TimeUnit): A = get
    def isCancelled: Boolean = false
    def cancel(evenIfRunning: Boolean): Boolean = false
  }

  def unit[A](a: A): Par[A] = (_: ExecutorService) => UnitFuture(a)

  def fork[A](a: => Par[A]): Par[A] =
    es => es.submit(new Callable[A] { def call: A = a(es). get})

  def lazyUnit[A](a: => A): Par[A] = fork(unit(a))

  def map2[A,B,C](a: Par[A], b: Par[B])(f: (A,B) => C): Par[C] =
    (es: ExecutorService) => {
      val af = a(es)
      val bf = b(es)
      UnitFuture(f(af.get, bf.get))
    }

  // 7.1
  //def map2[A,B,C](a: Par[A], b: Par[B])(f: (A,B) => C): Par[C]

  // 7.2
  /*
    Before continuing, try to come up with representations for Par that make it possible
    to implement the functions of our API.
   */

  // 7.3
  private case class TimeoutFuture[A,B,C](
    aFuture: Future[A],
    bFuture: Future[B],
    f: (A, B) => C
  ) extends Future[C] {

    def isDone: Boolean = aFuture.isDone && bFuture.isDone
    def isCancelled: Boolean = aFuture.isCancelled || bFuture.isCancelled
    def cancel(evenIfRunning: Boolean): Boolean = aFuture.cancel(evenIfRunning) || bFuture.cancel(evenIfRunning)

    def get: C = f(aFuture.get, bFuture.get)

    def get(timeout: Long, units: TimeUnit): C = {
      val timeoutMS = TimeUnit.MILLISECONDS.convert(timeout, units)
      val start = System.currentTimeMillis
      val aResult = aFuture.get(timeout, units)
      val end = System.currentTimeMillis

      val duration = end - start
      val remaining = timeoutMS - duration
      val bResult = bFuture.get(remaining, TimeUnit.MILLISECONDS)

      f(aResult, bResult)
    }
  }

  def map2Timeouts[A,B,C](a: Par[A], b: Par[B])(f: (A,B) => C): Par[C] =
    (es: ExecutorService) => {
      val aFuture = a(es)
      val bFuture = b(es)
      TimeoutFuture(aFuture, bFuture, f)
    }

  def map[A,B](pa: Par[A])(f: A => B): Par[B] =
    map2Timeouts(pa, unit(()))((a,_) => f(a))

  // 7.4
  def asyncF[A,B](f: A => B): A => Par[B] =
    a => lazyUnit(f(a))

  // 7.5
  def sequence[A](ps: List[Par[A]]): Par[List[A]] = {
    def append[A,B](head: Par[A], accumulated: Par[List[A]]): Par[List[A]] =
      map2Timeouts(head, accumulated)(_ :: _)

    fork(ps.foldRight(unit(List.empty[A]))(append))
  }

  // 7.6
  def parFilter[A](as: List[A])(f: A => Boolean): Par[List[A]] = {
    def filter(a: A): Option[A] = Some(a).filter(f)
    val asyncFilter = asyncF(filter)
    val filtered = sequence(as.map(asyncFilter))
    map(filtered)(_.flatten)
  }

  // 7.7
  // 7.8
  // 7.9

  // 7.14
}