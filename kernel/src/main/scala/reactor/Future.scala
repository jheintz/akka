/**
 * Copyright (C) 2009 Scalable Solutions.
 */

/**
 * Based on code from the actorom actor framework by Sergio Bossa [http://code.google.com/p/actorom/].
 */
package se.scalablesolutions.akka.kernel.reactor

import java.util.concurrent.locks.{Lock, Condition, ReentrantLock}
import java.util.concurrent.TimeUnit

class FutureTimeoutException(message: String) extends RuntimeException(message)

sealed trait FutureResult {
  def await
  def awaitBlocking
  def isCompleted: Boolean
  def isExpired: Boolean
  def timeoutInNanos: Long
  def result: Option[AnyRef]
  def exception: Option[Tuple2[AnyRef, Throwable]]
}

trait CompletableFutureResult extends FutureResult {
  def completeWithResult(result: AnyRef)
  def completeWithException(toBlame: AnyRef, exception: Throwable)
}

class DefaultCompletableFutureResult(timeout: Long) extends CompletableFutureResult {
  private val TIME_UNIT = TimeUnit.MILLISECONDS
  def this() = this(0)

  val timeoutInNanos = TIME_UNIT.toNanos(timeout)
  private val _startTimeInNanos = currentTimeInNanos
  private val _lock = new ReentrantLock
  private val _signal = _lock.newCondition
  private var _completed: Boolean = _
  private var _result: Option[AnyRef] = None
  private var _exception: Option[Tuple2[AnyRef, Throwable]] = None

  def await = try {
    _lock.lock
    var wait = timeoutInNanos - (currentTimeInNanos - _startTimeInNanos)
    while (!_completed && wait > 0) {
      var start = currentTimeInNanos
      try {
        wait = _signal.awaitNanos(wait)
        if (wait <= 0) throw new FutureTimeoutException("Future timed out after [" + timeout + "] milliseconds") 
      } catch {
        case e: InterruptedException =>
          wait = wait - (currentTimeInNanos - start)
      }
    }
  } finally {
    _lock.unlock
  }

  def awaitBlocking = try {
    _lock.lock
    while (!_completed) {
      _signal.await
    }
  } finally {
    _lock.unlock
  }

  def isCompleted: Boolean = try {
    _lock.lock
    _completed
  } finally {
    _lock.unlock
  }

  def isExpired: Boolean = try {
    _lock.lock
    timeoutInNanos - (currentTimeInNanos - _startTimeInNanos) <= 0
  } finally {
    _lock.unlock
  }

  def result: Option[AnyRef] = try {
    _lock.lock
    _result
  } finally {
    _lock.unlock
  }

  def exception: Option[Tuple2[AnyRef, Throwable]] = try {
    _lock.lock
    _exception
  } finally {
    _lock.unlock
  }

  def completeWithResult(result: AnyRef) = try {
    _lock.lock
    if (!_completed) {
      _completed = true
      _result = Some(result)
    }
  } finally {
    _signal.signalAll
    _lock.unlock
  }

  def completeWithException(toBlame: AnyRef, exception: Throwable) = try {
    _lock.lock
    if (!_completed) {
      _completed = true
      _exception = Some((toBlame, exception))
    }
  } finally {
    _signal.signalAll
    _lock.unlock
  }

  private def currentTimeInNanos: Long = TIME_UNIT.toNanos(System.currentTimeMillis)
}
