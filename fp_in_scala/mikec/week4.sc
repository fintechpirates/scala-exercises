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

def nonNegativeInt(rng: RNG): (Int, RNG) = {
  val (randomInt, nextRNG) = rng.nextInt
  val normalized = if (randomInt < 0) math.abs(randomInt + 1) else randomInt
  (normalized, nextRNG)
}

def double(rng: RNG): (Double, RNG) = {
  val (randomNonNegInt, nextRNG) = nonNegativeInt(rng)
  val max = Int.MaxValue + 1
  val randomDouble = (randomNonNegInt.toDouble / max.toDouble)
  (randomDouble, nextRNG)
}

def intDouble(rng: RNG): ((Int,Double), RNG) = {
  val (randomInt, next1) = rng.nextInt
  val (randomDouble, next2) = double(next1)
  ((randomInt, randomDouble), next2)
}
def doubleInt(rng: RNG): ((Double,Int), RNG) = {
  val ((randomInt, randomDouble), nextRNG) = intDouble(rng)
  ((randomDouble, randomInt), nextRNG)
}

def double3(rng: RNG): ((Double,Double,Double), RNG) = {
  val (r1, next1) = double(rng)
  val (r2, next2) = double(next1)
  val (r3, next3) = double(next2)
  ((r1, r2, r3), next3)
}

def ints(count: Int)(rng: RNG): (List[Int], RNG) = {
  def recursiveRando(count: Int, rng: RNG, accumulated: List[Int] = List.empty[Int]): (List[Int], RNG) =
    count match {
      case count if count < 0 =>
        (accumulated, rng)
      case _ =>
        val (rand, next) = rng.nextInt
        recursiveRando(count-1, next, rand::accumulated)
    }
  recursiveRando(count, rng)
}

type Rand[+A] = RNG => (A, RNG)

def unit[A](a: A): Rand[A] = rng => (a, rng)

def map[A,B](s: Rand[A])(f: A => B): Rand[B] =
  rng => {
    val (a, rng2) = s(rng)
    (f(a), rng2)
  }

def doubleWithMap(rng: RNG): (Double, RNG) =
  map(nonNegativeInt)(_ / (Int.MaxValue.toDouble + 1.0))(rng)

// applicative
def map2[A,B,C](ra: Rand[A], rb: Rand[B])(f: (A, B) => C): Rand[C] =
  rng0 => {
    val (a, rng1) = ra(rng0)
    val (b, rng2) = rb(rng1)
    (f(a,b), rng2)
  }

def both[A,B](ra: Rand[A], rb: Rand[B]): Rand[(A,B)] = map2(ra, rb)((_, _))

def sequence[A](fs: List[Rand[A]]): Rand[List[A]] = {
  def append[A,B](head: Rand[A], accumulated: Rand[List[A]]): Rand[List[A]] =
    map2(head, accumulated)(_ :: _)

  fs.foldRight(unit(List.empty[A]))(append)
}

def flatMap[A,B](getRandomNumFrom: Rand[A])(nextRandGenerator: A => Rand[B]): Rand[B] =
  rng => {
    val (randomNum, nextRng) = getRandomNumFrom(rng)
    nextRandGenerator(randomNum)(nextRng)
  }

def nonNegativeLessThan(n: Int): Rand[Int] =
  flatMap(nonNegativeInt){
    case i if i + (n-1) - (i % n) >= 0 => unit(i % n)
    case _ => nonNegativeLessThan(n)
  }

def mapFlatMap[A,B](s: Rand[A])(f: A => B): Rand[B] =
  flatMap(s)(a => unit(f(a)))

def doubleWithMap2(rng: RNG): (Double, RNG) =
  mapFlatMap(nonNegativeInt)(_ / (Int.MaxValue.toDouble + 1.0))(rng)

def map2FlatMap[A,B,C](ra: Rand[A], rb: Rand[B])(f: (A, B) => C): Rand[C] = {
  flatMap(ra){ a =>
    flatMap(rb) { b =>
      unit(f(a, b))
    }
  }
}

def both2[A,B](ra: Rand[A], rb: Rand[B]): Rand[(A,B)] = map2FlatMap(ra, rb)((_, _))

case class State[S,+A](run: S => (A,S)) {
  def map[B](transformation: A => B): State[S,B] = {
    def nextRun(state: S): (B, S) = {
      val (result, nextState) = run(state)
      (transformation(result), nextState)
    }
    State(nextRun)
  }

  def map2[B,C](otherStateMonad: State[S,B])(combinedTransform: (A, B) => C): State[S,C] = {
    def combinedNextRun(state: S): (C, S) = {
      val (result1, nextState1) = run(state)
      val (result2, nextState2) = otherStateMonad.run(nextState1)
      (combinedTransform(result1, result2), nextState2)
    }
    State(combinedNextRun)
  }

  def flatMap[B](nextStateGenerator: A => State[S,B]): State[S,B] = {
    def nextRun(state: S): (B, S) = {
      val (result, nextState) = run(state)
      nextStateGenerator(result).run(nextState)
    }
    State(nextRun)
  }
}

object State {
  def unit[S,A](a: A): State[S,A] = State(s => (a, s))
}

sealed trait Input
case object Coin extends Input
case object Turn extends Input
case class Machine(locked: Boolean, candies: Int, coins: Int) {
  // TODO: Implement machine
}

val seed1 = SimpleRNG(1)
println(seed1.nextInt)

val seed999 = SimpleRNG(999)
println(seed999.nextInt)

println("6.1")
println(nonNegativeInt(seed1))
println(nonNegativeInt(seed999))

println("6.2")
println(double(seed1))
println(double(seed999))

println("6.3")
println(intDouble(seed1))
println(intDouble(seed999))

println(doubleInt(seed1))
println(doubleInt(seed999))

println(double3(seed1))
println(double3(seed999))

println("6.4")
println(ints(10)(seed1))
println(ints(10)(seed999))

println("6.5")
println(doubleWithMap(seed1))
println(doubleWithMap(seed999))

println("6.6")
val paired = both(nonNegativeInt _, double _)
println(paired(seed1))
println(paired(seed999))

def intString(rng: RNG): (String, RNG) = {
  val (randomInt, nextRNG) = rng.nextInt
  (randomInt.toString, nextRNG)
}

println("6.7")
val sequenced = sequence(List(nonNegativeInt _, double _, intString _))
println(sequenced(seed1))
println(sequenced(seed999))

println("6.8")
println(nonNegativeLessThan(3)(seed1))
println(nonNegativeLessThan(3)(seed999))

println("6.9")
println(doubleWithMap(seed1))
println(doubleWithMap2(seed1))
println(doubleWithMap(seed999))
println(doubleWithMap2(seed999))
val paired2 = both2(nonNegativeInt _, double _)
println(paired(seed1))
println(paired2(seed1))
println(paired(seed999))
println(paired2(seed999))

println("6.10")
def doubleWithStateMap(rng: RNG): (Double, RNG) =
  State[RNG, Int](nonNegativeInt).map(_ / (Int.MaxValue.toDouble + 1.0)).run(rng)

println(doubleWithMap(seed1))
println(doubleWithMap(seed999))
println(doubleWithStateMap(seed1))
println(doubleWithStateMap(seed999))

def both3[A,B](ra: State[RNG, A], rb: State[RNG, B]): State[RNG, (A,B)] =
  ra.map2(rb)((_, _))

val paired3 = both3(State[RNG, Int](nonNegativeInt), State[RNG, Double](double))
println(paired(seed1))
println(paired3.run(seed1))
println(paired(seed999))
println(paired3.run(seed999))

def nonNegativeLessThan2(n: Int): State[RNG, Int] =
  State[RNG, Int](nonNegativeInt).flatMap {
    case i if i + (n-1) - (i % n) >= 0 => State.unit(i % n)
    case _ => nonNegativeLessThan2(n)
  }
println(nonNegativeLessThan(3)(seed1))
println(nonNegativeLessThan2(3).run(seed1))
println(nonNegativeLessThan(3)(seed999))
println(nonNegativeLessThan2(3).run(seed999))

//test generalized state monad by using it to produce same results as rand

println("6.11")
// TODO
