sealed trait Stream[+A] {

  def headOption: Option[A] = this match {
    case Empty => None
    case Cons(h, t) => Some(h())
  }

  /**
    * Exercise: 5.1
    *
    * Write a function to convert a Stream to a List, which will force its evaluation
    * and let you look at it in the REPL. You can convert to the regular List type
    * in the standard library. You can place this and other functions that operate on
    * a Stream inside the Stream trait.
    *
    * def toList: List[A]

    */

  // QUES: Why does this have to be defined in the trait?
  def toList: List[A] = this match {
    case Empty => List.empty
    case Cons(h, t) => h() :: t().toList
  }

  /**
    * Exercise: 5.2
    *
    * Write the function take(n) for returning the first n elements of a Stream, and drop(n)
    * for skipping the first n elements of a Stream.
    *
    */

  def take(n: Int): Stream[A] = ???

  def drop(n: Int): List[A] = ???

}

case object Empty extends Stream[Nothing]
case class Cons[+A](h: () => A, t: () => Stream[A]) extends Stream[A]

object Stream {

  def cons[A](hd: => A, tl: => Stream[A]): Stream[A] = {
    lazy val head = hd
    lazy val tail = tl
    Cons(() => head, () => tail)
  }

  def empty[A]: Stream[A] = Empty

  def apply[A](as: A*): Stream[A] =
    if (as.isEmpty) empty else cons(as.head, apply(as.tail: _*))


  /**
    * Exercise: 5.3
    *
    * Write the function takeWhile for returning all starting
    * elements of a Stream that match the given predicate.
    *
    * def takeWhile(p: A => Boolean): Stream[A]
    *
    */

  def takeWhile[A](p: A => Boolean): Stream[A] = ???

  /**
    * Exercise: 5.4
    *
    * Implement forAll, which checks that all elements in the Stream match a given predicate.
    * Your implementation should terminate the traversal as soon as it encounters
    * a nonmatching value.
    *
    * def forAll(p: A => Boolean): Boolean
    *
    */


  /**
    * Exercise: 5.5
    *
    * Use foldRight to implement takeWhile..
    *
    */

  def takeWhile = ???

  /**
    * Exercise: 5.6
    *
    * Hard: Implement headOption using foldRight.
    *
    */

  /**
    * Exercise: 5.7
    *
    * Implement map, filter, append, and flatMap using foldRight.
    * The append method should be non-strict in its argument.
    *
    */


  /**
    * Exercise: 5.8
    *
    * Generalize ones slightly to the function constant,
    * which returns an infinite Stream of a given value.
    *
    * def constant[A](a: A): Stream[A]
    *
    */

  def constant[A](a: A): Stream[A] = ???

  /**
    * Exercise: 5.9
    *
    * Write a function that generates an infinite stream of integers,
    * starting from n, then n + 1, n + 2, and so on.
    *
    * def from(n: Int): Stream[Int]
    *
    */

  def from(n: Int): Stream[Int] = ???

  /**
    * Exercise: 5.10
    *
    * Write a function fibs that generates the infinite stream of
    * Fibonacci numbers: 0, 1, 1, 2, 3, 5, 8, and so on.
    *
    */


  /**
    * Exercise: 5.11
    *
    * Write a more general stream-building function called unfold.
    * It takes an initial state, and a function for producing both the next state
    * and the next value in the generated stream.
    *
    * def unfold[A, S](z: S)(f: S => Option[(A, S)]): Stream[A]
    *
    */

  def unfold[A, S](z: S)(f: S => Option[(A, S)]): Stream[A] = ???

  /**
    * Exercise: 5.12
    *
    * Write fibs, from, constant, and ones in terms of unfold.
    *
    */

  def fibsUF = ???
  def fromUF = ???
  def constantUF = ???
  def onesUF = ???

  /**
    * Exercise: 5.13
    *
    * Use unfold to implement map, take, takeWhile, zipWith (as in chapter 3),
    * and zipAll. The zipAll function should continue the traversal as
    * long as either stream has more elements—it uses Option to indicate whether
    * each stream has been exhausted.
    *
    * def zipAll[B](s2: Stream[B]): Stream[(Option[A],Option[B])]
    *
    */

  def zipAll[A, B](s2: Stream[B]): Stream[(Option[A],Option[B])] = ???


  /**
    * Exercise: 5.14
    *
    * Hard: Implement startsWith using functions you’ve written.
    * It should check if one Stream is a prefix of another.
    * For instance, Stream(1,2,3) startsWith Stream(1,2) would be true.
    *
    * def startsWith[A](s: Stream[A]): Boolean
    *
    */

  def startsWith[A](s: Stream[A]): Boolean = ???

  /**
    * Exercise: 5.15
    *
    * Implement tails using unfold. For a given Stream, tails returns the Stream of
    * suffixes of the input sequence, starting with the original Stream. For example,
    * given Stream(1,2,3), it would return
    * Stream(Stream(1,2,3), Stream(2,3), Stream(3), Stream()).
    *
    * def tails: Stream[Stream[A]]
    *
    */

  def tails[A]: Stream[Stream[A]] = ???

  /**
    * Exercise: 5.16
    *
    * Hard: Generalize tails to the function scanRight, which is like a foldRight that
    * returns a stream of the intermediate results. For example:
    *     scala> Stream(1,2,3).scanRight(0)(_ + _).toList
    *     res0: List[Int] = List(6,5,3,0)
    * This example should be equivalent to the expression List(1+2+3+0, 2+3+0, 3+0, 0).
    * Your function should reuse intermediate results so that traversing a Stream
    * with n elements always takes time linear in n. Can it be implemented using unfold?
    * How, or why not?
    * Could it be implemented using another function we’ve written?
    *
    */

}

val testStream = Stream(1, 2, 3, 4)
val testStream2 = Stream(1)
val testEmptyStream = Stream.empty

// toList tests
reportTestResult("toList convert a stream to a list", testStream.toList == List(1, 2, 3, 4))
reportTestResult("toList convert a stream to a list", testStream2.toList == List(1))
reportTestResult("toList convert a stream to a list", testEmptyStream.toList == List.empty)


def reportTestResult(testName: String, assertion: Boolean): Unit = assertion match {
  case true =>
    println(s"PASS: $testName")
  case _ =>
    println(s"FAIL: $testName")
}
