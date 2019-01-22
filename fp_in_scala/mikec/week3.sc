sealed trait Stream[+A] {
  def headOption: Option[A]
  def toList: List[A]
  def take(n :Int): Stream[A]
  def drop(n :Int): Stream[A]
  def takeWhile(p: A => Boolean): Stream[A]
  def forAll(p: A => Boolean): Boolean

  def foldRight[B](z: => B)(f: (A, => B) => B): B

  def takeWhileFoldR(p: A => Boolean): Stream[A]
  def headOptionFoldR: Option[A]
  def mapFoldR[B](f: A => B): Stream[B]
  def filterFoldR(f: A => Boolean): Stream[A]
  def appendFoldR[B >: A](b: => Stream[B]): Stream[B]
  def flatMapFoldR[B](f: A => Stream[B]): Stream[B]
}

case object Empty extends Stream[Nothing] {
  override def headOption: Option[Nothing] = None
  override def toList: List[Nothing] = List.empty[Nothing]
  override def take(n :Int): Stream[Nothing] = Empty
  override def drop(n :Int): Stream[Nothing] = Empty
  override def takeWhile(p: Nothing => Boolean): Stream[Nothing] = Empty
  override def forAll(p: Nothing => Boolean): Boolean = true

  override def foldRight[B](z: => B)(f: (Nothing, => B) => B): B = z

  override def takeWhileFoldR(p: Nothing => Boolean): Stream[Nothing] = Empty
  override def headOptionFoldR: Option[Nothing] = None
  override def mapFoldR[B](f: Nothing => B): Stream[B] = Empty
  override def filterFoldR(f: Nothing => Boolean): Stream[Nothing] = Empty
  override def appendFoldR[B >: Nothing](b: => Stream[B]): Stream[B] = b
  override def flatMapFoldR[B](f: Nothing => Stream[B]): Stream[B] = Empty
}

case class Cons[+A](h: () => A, t: () => Stream[A]) extends Stream[A] {
  override def headOption: Option[A] = Some(h())
  override def toList: List[A] = h() :: t().toList
  override def take(n :Int): Stream[A] =
    n match {
      case 0 => Empty
      case _ => Cons(h, () => t().take(n-1))
    }
  override def drop(n :Int): Stream[A] =
    n match {
      case 0 => this
      case _ => t().drop(n-1)
    }
  override def takeWhile(p: A => Boolean): Stream[A] =
    if(p(h())) Cons(h, () => t().takeWhile(p))
    else Empty
  override def forAll(p: A => Boolean): Boolean =
    if(p(h())) t().forAll(p)
    else false

  override def foldRight[B](z: => B)(f: (A, => B) => B): B =
    f(h(), t().foldRight(z)(f))

  override def takeWhileFoldR(p: A => Boolean): Stream[A] =
    foldRight(Stream.empty[A]){
      case (element, taken) if p(element) => Cons(() => element, () => taken)
      case _ => Stream.empty[A]
    }
  override def headOptionFoldR: Option[A] =
    foldRight(Option.empty[A]){ case (a, _) => Some(a) }
  override def mapFoldR[B](f: A => B): Stream[B] =
    foldRight(Stream.empty[B])((element, result) => Cons(() => f(element), () => result))
  override def filterFoldR(f: A => Boolean): Stream[A] =
    foldRight(Stream.empty[A]) {
      case (element, taken) if f(element) => Cons(() => element, () => taken)
      case (_, taken) => taken
    }
  override def appendFoldR[B >: A](b: => Stream[B]): Stream[B] =
    foldRight(b)((element, result) => Cons(() => element, () => result))
  override def flatMapFoldR[B](f: A => Stream[B]): Stream[B] =
    mapFoldR(f).foldRight(Stream.empty[B])((element, result) => element.appendFoldR(result))
}

object Stream {
  def cons[A](hd: => A, tl: => Stream[A]): Stream[A] = {
    lazy val head = hd
    lazy val tail = tl
    Cons(() => head, () => tail)
  }
  def empty[A]: Stream[A] = Empty
  def apply[A](as: A*): Stream[A] =
    if (as.isEmpty) empty else cons(as.head, apply(as.tail: _*))

  def constant[A](a: A): Stream[A] = Stream.cons(a, constant(a))
  def from(n: Int): Stream[Int] = Stream.cons(n, from(n+1))
  def fib(a: Int = 0, b: Int = 1): Stream[Int] = Stream.cons(a, fib(b, a+b))

  def unfold[A, S](z: S)(f: S => Option[(A, S)]): Stream[A] =
    f(z) match {
      case Some((a,s)) => Stream.cons(a, unfold(s)(f))
      case None => Empty
    }

  lazy val onesUnfold: Stream[Int] = unfold(())(_ => Some((1, ())))
  def constantUnfold[A](a: A): Stream[A] = unfold(())(_ => Some((a, ())))
  def fromUnfold(n: Int): Stream[Int] = unfold(n)(n => Some((n, n+1)))
}

assert(Stream().headOption.isEmpty)
assert(Stream(1,2,3).headOption.contains(1))

assert(Stream().toList == List())
assert(Stream(1,2,3).toList == List(1,2,3))

assert(Stream(1,2,3).take(0).toList == Stream().toList)
assert(Stream(1,2,3).take(1).toList == Stream(1).toList)
assert(Stream(1,2,3).take(2).toList == Stream(1,2).toList)
assert(Stream(1,2,3).take(3).toList == Stream(1,2,3).toList)
assert(Stream(1,2,3).take(4).toList == Stream(1,2,3).toList)

assert(Stream(1,2,3).drop(0).toList == Stream(1,2,3).toList)
assert(Stream(1,2,3).drop(1).toList == Stream(2,3).toList)
assert(Stream(1,2,3).drop(2).toList == Stream(3).toList)
assert(Stream(1,2,3).drop(3).toList == Stream().toList)
assert(Stream(1,2,3).drop(4).toList == Stream().toList)

assert(Stream(1,2,3).takeWhile(a => a < 1).toList == Stream().toList)
assert(Stream(1,2,3).takeWhile(a => a < 2).toList == Stream(1).toList)
assert(Stream(1,2,3).takeWhile(a => a < 3).toList == Stream(1,2).toList)
assert(Stream(1,2,3).takeWhile(a => a < 4).toList == Stream(1,2,3).toList)

assert(Stream(1,2,3).forAll(a => a < 1) == false)
assert(Stream(1,2,3).forAll(a => a < 2) == false)
assert(Stream(1,2,3).forAll(a => a < 3) == false)
assert(Stream(1,2,3).forAll(a => a < 4) == true)

assert(Stream(1,2,3).takeWhile(a => a < 1).toList == Stream(1,2,3).takeWhileFoldR(a => a < 1).toList)
assert(Stream(1,2,3).takeWhile(a => a < 2).toList == Stream(1,2,3).takeWhileFoldR(a => a < 2).toList)
assert(Stream(1,2,3).takeWhile(a => a < 3).toList == Stream(1,2,3).takeWhileFoldR(a => a < 3).toList)
assert(Stream(1,2,3).takeWhile(a => a < 4).toList == Stream(1,2,3).takeWhileFoldR(a => a < 4).toList)

assert(Stream().headOptionFoldR.isEmpty)
assert(Stream(1,2,3).headOptionFoldR.contains(1))

assert(Stream(1,2,3).mapFoldR(_ * 2).toList == Stream(2,4,6).toList)

assert(Stream(1,2,3,4).filterFoldR(_ % 2 == 0).toList == Stream(2,4).toList)

assert(Stream(1,2,3).appendFoldR(Stream(4,5,6)).toList == Stream(1,2,3,4,5,6).toList)
assert(Stream(1,2,3).appendFoldR(Stream()).toList == Stream(1,2,3).toList)
assert(Stream().appendFoldR(Stream(4,5,6)).toList == Stream(4,5,6).toList)

assert(Stream(1,2,3,4).flatMapFoldR(a => Stream(a, a + 1)).toList == Stream(1,2,2,3,3,4,4,5).toList)

assert(Stream.constant(2).take(4).toList == Stream(2,2,2,2).toList)
assert(Stream.from(1).take(4).toList == Stream(1,2,3,4).toList)
assert(Stream.fib().take(7).toList == Stream(0,1,1,2,3,5,8).toList)

val countdown = Stream.unfold(10) { x => if (x == 0) None else Some(x, x - 1) }
assert(countdown.toList == Stream(10,9,8,7,6,5,4,3,2,1).toList)

assert(Stream.onesUnfold.take(4).toList == Stream(1,1,1,1).toList)
assert(Stream.constantUnfold(2).take(4).toList == Stream(2,2,2,2).toList)
assert(Stream.fromUnfold(1).take(4).toList == Stream(1,2,3,4).toList)

// 5.13
// 5.14
// 5.15
// 5.16