
trait RNG {
  def nextInt: (Int, RNG)
}

case class SimpleRNG(seed: Long) extends RNG {
  def nextInt: (Int, RNG) = {
    val newSeed = (seed * 0x5DEECE66DL + 0xBL) & 0xFFFFFFFFFFFFL
    val nextRNG = SimpleRNG(newSeed)
    val n = (newSeed >>> 16).toInt
    (n, nextRNG)
  }
}

/**
  * Exercise: 6.1
  *
  * Write a function that uses RNG.nextInt to generate a random integer
  * between 0 and Int.maxValue (inclusive). Make sure to handle the corner
  * case when nextInt returns Int.MinValue, which doesn’t have a
  * non-negative counterpart.
  *
  * def nonNegativeInt(rng: RNG): (Int, RNG)
  */

def nonNegativeInt(rng: RNG): (Int, RNG) = {
  val (i, rng1) = rng.nextInt

  i match {
    case Int.MinValue => (0, rng1)
    case i if i < 0 => (i.abs, rng1)
    case _ => (i, rng1)
  }
}

/**
  * Exercise: 6.2
  *
  * Write a function to generate a Double between 0 and 1, not including 1.
  * Note: You can use Int.MaxValue to obtain the maximum positive integer
  * value, and you can use x.toDouble to convert an x: Int to a Double.
  *
  * def double(rng: RNG): (Double, RNG)
  */


def double(rng: RNG): (Double, RNG) = {
  val (i, rng1) = nonNegativeInt(rng)
  (i/(Int.MaxValue.toDouble + 1), rng1)
}

/**
  * Exercise: 6.3
  *
  * Write functions to generate an (Int, Double) pair, a (Double, Int) pair,
  * and a (Double, Double, Double) 3-tuple. You should be able to reuse
  * the functions you’ve already written.
  *
  * def intDouble(rng: RNG): ((Int,Double), RNG)
  * def doubleInt(rng: RNG): ((Double,Int), RNG)
  * def double3(rng: RNG): ((Double,Double,Double), RNG)
  *
  */

def intDouble(rng: RNG): ((Int,Double), RNG) = {
  val (i, rng1) = rng.nextInt
  val (d, rng2) = double(rng1)
  ((i, d), rng2)
}

def doubleInt(rng: RNG): ((Double,Int), RNG) = {
  val ((i, d), rng1) = intDouble(rng)
  ((d, i), rng1)
}

def double3(rng: RNG): ((Double,Double,Double), RNG) = {
  val (d1, rng1) = double(rng)
  val (d2, rng2) = double(rng1)
  val (d3, rng3) = double(rng2)
  ((d1, d2, d3), rng3)
}

/**
  * Exercise: 6.4
  *
  * Write a function to generate a list of random integers.
  *
  * def ints(count: Int)(rng: RNG): (List[Int], RNG)
  */

def ints(count: Int)(rng: RNG): (List[Int], RNG) = {
  count match {
    case c if c <= 0 =>
      (List.empty, rng)
    case _ =>
      val (i, rng1) = rng.nextInt
      val (l, rng2) = ints(count - 1)(rng1)
      (i :: l, rng2)
  }
}

/**
  * Exercise: 6.5
  *
  * Use map to reimplement double in a more elegant way. See exercise 6.2.
  *
  */


/**
  * Exercise: 6.6
  *
  * Write the implementation of map2 based on the following signature.
  * This function takes two actions, ra and rb, and a function f for
  * combining their results, and returns a new action that combines them:
  *
  * def map2[A,B,C](ra: Rand[A], rb: Rand[B])(f: (A, B) => C): Rand[C]
  *
  */



/**
  * Exercise: 6.7
  *
  * Hard: If you can combine two RNG transitions, you should be able to
  * combine a whole list of them. Implement sequence for combining a List
  * of transitions into a single transition. Use it to reimplement the
  * ints function you wrote before. For the latter, you can use the
  * standard library function List.fill(n)(x) to make a list with x
  * repeated n times.
  *
  * def sequence[A](fs: List[Rand[A]]): Rand[List[A]]
  */



/**
  * Exercise: 6.8
  *
  * Implement flatMap, and then use it to implement nonNegativeLessThan.
  *
  * def flatMap[A,B](f: Rand[A])(g: A => Rand[B]): Rand[B]
  *
  */

/**
  * Exercise: 6.9
  *
  * Reimplement map and map2 in terms of flatMap. The fact that this is
  * possible is what we’re referring to when we say that flatMap is more
  * powerful than map and map2)
  */



/**
  * Exercise: 6.10
  *
  * Generalize the functions unit, map, map2, flatMap, and sequence.
  * Add them as meth- ods on the State case class where possible.
  * Otherwise you should put them in a State companion object.
  */



/**
  * Exercise: 6.11
  *
  * Hard: To gain experience with the use of State, implement a finite
  * state automaton that models a simple candy dispenser. The machine
  * has two types of input: you can insert a coin, or you can turn the
  * knob to dispense candy. It can be in one of two states: locked or
  * unlocked. It also tracks how many candies are left and how many
  * coins it contains.
  *
  * sealed trait Input
  *
  * case object Coin extends Input case object Turn extends Input
  * case class Machine(locked: Boolean, candies: Int, coins: Int)
  *
  * The rules of the machine are as follows:
  * - Inserting a coin into a locked machine will cause it to unlock if
  * there’s any candy left.
  * - Turning the knob on an unlocked machine will cause it to dispense
  * candy and become locked.
  * - Turning the knob on a locked machine or inserting a coin into an unlocked machine does nothing.
  * - A machine that’s out of candy ignores all inputs.
  *
  * The method simulateMachine should operate the machine based on the
  * list of inputs and return the number of coins and candies left in the
  * machine at the end. For exam- ple, if the input Machine has 10 coins
  * and 5 candies, and a total of 4 candies are suc- cessfully bought,
  * the output should be (14, 1).
  *
  * def simulateMachine(inputs: List[Input]): State[Machine, (Int, Int)]
  *
  */

type State[S,+A] = S => (A,S)

sealed trait Input

case object Coin extends Input
case object Turn extends Input
case class Machine(locked: Boolean, candies: Int, coins: Int)

def simulateMachine(inputs: List[Input]): State[Machine, (Int, Int)] = ???



val testRng = SimpleRNG(50)
val testRngNegative = SimpleRNG(-1000)
val testRngMax = SimpleRNG(Int.MaxValue)
val testRngMin = SimpleRNG(Int.MinValue)

// nonNegativeInt test
reportTestResult("nonNegativeInt should return a non-negative value", nonNegativeInt(testRng)._1 >= 0)
reportTestResult("nonNegativeInt should return a non-negative value", nonNegativeInt(testRngNegative)._1 >= 0)
reportTestResult("nonNegativeInt should return a non-negative value", nonNegativeInt(testRngMax)._1 >= 0)
reportTestResult("nonNegativeInt should return a non-negative value", nonNegativeInt(testRngMin)._1 >= 0)

// double test
reportTestResult("double should return a double between 0 and 1 not including 1", double(testRng)._1 >= 0 && double(testRng)._1 < 1)
reportTestResult("double should work with negative numbers", double(testRngNegative)._1 >= 0 && double(testRngNegative)._1 < 1)

// ints test
reportTestResult("ints should return list of random ints", ints(10)(testRng)._1.length == 10)
reportTestResult("ints should return list of random ints", ints(0)(testRng)._1.length == 0)
reportTestResult("ints should return list of random ints", ints(-10)(testRngNegative)._1.length == 0)


def reportTestResult(testName: String, assertion: Boolean): Unit = assertion match {
  case true =>
    println(s"PASS: $testName")
  case _ =>
    println(s"FAIL: $testName")
}
