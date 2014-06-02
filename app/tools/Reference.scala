package tools

import java.util.concurrent.atomic.AtomicReference

class Reference[T](name: String) {

  private[this] val ref = new AtomicReference[Option[T]](None)

  def set(value: T): Option[T] = {
    ref.getAndSet(Option(value))
  }

  def cleanup(): Option[T] = {
    ref.getAndSet(None)
  }

  def apply(): T = {
    ref.get().getOrElse(throw new RuntimeException(s"Reference to ${name} was not properly initialized ..."))
  }

  def get() = apply()

  def isEmpty: Boolean = ref.get.isEmpty
  def isDefined: Boolean = ref.get.isDefined
  def getOrElse[B >: T](default: => B): B = ref.get.getOrElse(default)
  def orNull[A1 >: T](implicit ev: <:<[Null, A1]): A1 = ref.get.orNull(ev)
  def map[B](f: (T) => B): Option[B] = ref.get.map(f)
  def fold[B](ifEmpty: => B)(f: (T) => B): B = ref.get.fold(ifEmpty)(f)
  def flatMap[B](f: (T) => Option[B]): Option[B] = ref.get.flatMap(f)
  def filter(p: (T) => Boolean): Option[T] = ref.get.filter(p)
  def flatten[B](implicit ev: <:<[T, Option[B]]): Option[B] = ref.get.flatten(ev)
  def filterNot(p: (T) => Boolean): Option[T] = ref.get.filterNot(p)
  def nonEmpty: Boolean = ref.get.nonEmpty
  def contains[A1 >: T](elem: A1): Boolean = ref.get.contains(elem)
  def exists(p: (T) => Boolean): Boolean = ref.get.exists(p)
  def forall(p: (T) => Boolean): Boolean = ref.get.forall(p)
  def foreach[U](f: (T) => U): Unit = ref.get.foreach(f)
  def collect[B](pf: PartialFunction[T, B]): Option[B] = ref.get.collect(pf)
  def orElse[B >: T](alternative: => Option[B]): Option[B] = ref.get.orElse(alternative)
}

object Reference {
  def apply[T](name: String, value: T): Reference[T] = {
    val ref = new Reference[T](name)
    ref.set(value)
    ref
  }
  def apply[T](name: String): Reference[T] = new Reference[T](name)
}