import java.util.concurrent._

/*
class ExecutorService {
  def submit[A](a: Callable[A]): Future[A]
}
trait Callable[A] { def call: A } trait Future[A] {
  def get: A
  def get(timeout: Long, unit: TimeUnit): A
  def cancel(evenIfRunning: Boolean): Boolean
  def isDone: Boolean
  def isCancelled: Boolean
}
*/

type Par[A] = ExecutorService => Future[A]

object Par{

  // 7.1
  def map2[A, B, C](a: Par[A], b: Par[B])(f: (A,B) => C): Par[C] = { s: ExecutorService =>
    val fa = a(s)
    val fb = b(s)
    UnitFuture(f(fa.get, fb.get))
  }

  // TODO: ???

  // 7.3
  def map2Timeout[A, B, C](a: Par[A], b: Par[B])(f: (A,B) => C)(timeout: Long): Par[C] = { s: ExecutorService =>
    val fa = a(s)
    val fb = b(s)
    UnitFuture(f(fa.get(timeout, TimeUnit.MILLISECONDS), fb.get(timeout, TimeUnit.MILLISECONDS)))
  }

  // 7.4
  def asyncF[A,B](f: A => B): A => Par[B] = { a: A => lazyUnit(f(a)) }

  def map[A,B](pa: Par[A])(f: A => B): Par[B] = map2(pa, unit(()))((a,_) => f(a))

  def sortPar(parList: Par[List[Int]]) = map(parList)(_.sorted)

  // 7.5
  def sequence[A](ps: List[Par[A]]): Par[List[A]] = ???


  def parMap[A,B](ps: List[A])(f: A => B): Par[List[B]] = fork {
    val fbs: List[Par[B]] = ps.map(asyncF(f))
    sequence(fbs)
  }

  // 7.6
  //def parFilter[A](as: List[A])(f: A => Boolean): Par[List[A]]


  private case class UnitFuture[A](get: A) extends Future[A] { def isDone = true
    def get(timeout: Long, units: TimeUnit) = get
    def isCancelled = false
    def cancel(evenIfRunning: Boolean): Boolean = false
  }

  def unit[A](a: => A): Par[A] = (e: ExecutorService) => UnitFuture(a)
  def fork[A](a: => Par[A]): Par[A] = es => es.submit(new Callable[A] {
    def call = a(es).get
  })
  def lazyUnit[A](a: => A): Par[A] = fork(unit(a))
  def run[A](s: ExecutorService)(a: Par[A]): Future[A] = a(s)
}