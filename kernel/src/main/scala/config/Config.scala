/**
 * Copyright (C) 2009 Scalable Solutions.
 */

package se.scalablesolutions.akka.kernel.config

import reflect.BeanProperty

import kernel.actor.Actor
import kernel.reactor.MessageDispatcher

/**
 * Configuration classes - not to be used as messages.
 *
 * @author <a href="http://jonasboner.com">Jonas Bon&#233;r</a>
 */
object ScalaConfig {
  sealed abstract class ConfigElement

  abstract class Server extends ConfigElement
  abstract class FailOverScheme extends ConfigElement
  abstract class Scope extends ConfigElement

  case class SupervisorConfig(restartStrategy: RestartStrategy, worker: List[Server]) extends Server
  case class Supervise(actor: Actor, lifeCycle: LifeCycle) extends Server

  case class RestartStrategy(scheme: FailOverScheme, maxNrOfRetries: Int, withinTimeRange: Int) extends ConfigElement

  case object AllForOne extends FailOverScheme
  case object OneForOne extends FailOverScheme

  case class LifeCycle(scope: Scope,
                       shutdownTime: Int,
                       callbacks: Option[RestartCallbacks]  // optional
          ) extends ConfigElement
  object LifeCycle {
    def apply(scope: Scope, shutdownTime: Int) = new LifeCycle(scope, shutdownTime, None)
    def apply(scope: Scope) = new LifeCycle(scope, 0, None)
  }
  case class RestartCallbacks(preRestart: String, postRestart: String) {
    if (preRestart == null || postRestart == null) throw new IllegalArgumentException("Restart callback methods can't be null")
  }

  case object Permanent extends Scope
  case object Transient extends Scope
  case object Temporary extends Scope

  case class RemoteAddress(hostname: String, port: Int)

  class Component(_intf: Class[_],
                  val target: Class[_],
                  val lifeCycle: LifeCycle,
                  val timeout: Int,
                  _dispatcher: MessageDispatcher, // optional
                  _remoteAddress: RemoteAddress   // optional
          ) extends Server {
    val intf: Option[Class[_]] = if (_intf == null) None else Some(_intf)
    val dispatcher: Option[MessageDispatcher] = if (_dispatcher == null) None else Some(_dispatcher)
    val remoteAddress: Option[RemoteAddress] = if (_remoteAddress == null) None else Some(_remoteAddress)
  }
  object Component {
    def apply(intf: Class[_], target: Class[_], lifeCycle: LifeCycle, timeout: Int) =
      new Component(intf, target, lifeCycle, timeout, null, null)

    def apply(target: Class[_], lifeCycle: LifeCycle, timeout: Int) =
      new Component(null, target, lifeCycle, timeout, null, null)

    def apply(intf: Class[_], target: Class[_], lifeCycle: LifeCycle, timeout: Int, dispatcher: MessageDispatcher) =
      new Component(intf, target, lifeCycle, timeout, dispatcher, null)

    def apply(target: Class[_], lifeCycle: LifeCycle, timeout: Int, dispatcher: MessageDispatcher) =
      new Component(null, target, lifeCycle, timeout, dispatcher, null)

    def apply(intf: Class[_], target: Class[_], lifeCycle: LifeCycle, timeout: Int, remoteAddress: RemoteAddress) =
      new Component(intf, target, lifeCycle, timeout, null, remoteAddress)

    def apply(target: Class[_], lifeCycle: LifeCycle, timeout: Int, remoteAddress: RemoteAddress) =
      new Component(null, target, lifeCycle, timeout, null, remoteAddress)

    def apply(intf: Class[_], target: Class[_], lifeCycle: LifeCycle, timeout: Int, dispatcher: MessageDispatcher, remoteAddress: RemoteAddress) =
      new Component(intf, target, lifeCycle, timeout, dispatcher, remoteAddress)

    def apply(target: Class[_], lifeCycle: LifeCycle, timeout: Int, dispatcher: MessageDispatcher, remoteAddress: RemoteAddress) =
      new Component(null, target, lifeCycle, timeout, dispatcher, remoteAddress)
  }
}

/**
 * @author <a href="http://jonasboner.com">Jonas Bon&#233;r</a>
 */
object JavaConfig {
  sealed abstract class ConfigElement

  class RestartStrategy(
      @BeanProperty val scheme: FailOverScheme,
      @BeanProperty val maxNrOfRetries: Int,
      @BeanProperty val withinTimeRange: Int) extends ConfigElement {
    def transform = se.scalablesolutions.akka.kernel.config.ScalaConfig.RestartStrategy(
      scheme.transform, maxNrOfRetries, withinTimeRange)
  }
  
  class LifeCycle(@BeanProperty val scope: Scope, @BeanProperty val shutdownTime: Int,  @BeanProperty val callbacks: RestartCallbacks) extends ConfigElement {
    def this(scope: Scope, shutdownTime: Int) = this(scope, shutdownTime, null)
    def transform = {
      val callbackOption = if (callbacks == null) None else Some(callbacks.transform)
      se.scalablesolutions.akka.kernel.config.ScalaConfig.LifeCycle(scope.transform, shutdownTime, callbackOption)
    }
  }

  class RestartCallbacks(@BeanProperty val preRestart: String, @BeanProperty val postRestart: String) {
    def transform = se.scalablesolutions.akka.kernel.config.ScalaConfig.RestartCallbacks(preRestart, postRestart)
  }

  abstract class Scope extends ConfigElement {
    def transform: se.scalablesolutions.akka.kernel.config.ScalaConfig.Scope
  }
  class Permanent extends Scope {
    override def transform = se.scalablesolutions.akka.kernel.config.ScalaConfig.Permanent
  }
  class Transient extends Scope {
    override def transform = se.scalablesolutions.akka.kernel.config.ScalaConfig.Transient
  }
  class Temporary extends Scope {
    override def transform = se.scalablesolutions.akka.kernel.config.ScalaConfig.Temporary
  }

  abstract class FailOverScheme extends ConfigElement {
    def transform: se.scalablesolutions.akka.kernel.config.ScalaConfig.FailOverScheme
  }
  class AllForOne extends FailOverScheme {
    override def transform = se.scalablesolutions.akka.kernel.config.ScalaConfig.AllForOne
  }
  class OneForOne extends FailOverScheme {
    override def transform = se.scalablesolutions.akka.kernel.config.ScalaConfig.OneForOne
  }

  class RemoteAddress(@BeanProperty val hostname: String, @BeanProperty val port: Int)

  abstract class Server extends ConfigElement
  class Component(@BeanProperty val intf: Class[_],
                  @BeanProperty val target: Class[_],
                  @BeanProperty val lifeCycle: LifeCycle,
                  @BeanProperty val timeout: Int,
                  @BeanProperty val dispatcher: MessageDispatcher, // optional
                  @BeanProperty val remoteAddress: RemoteAddress   // optional
          ) extends Server {

    def this(intf: Class[_], target: Class[_], lifeCycle: LifeCycle, timeout: Int) =
      this(intf, target, lifeCycle, timeout, null, null)

    def this(target: Class[_], lifeCycle: LifeCycle, timeout: Int) =
      this(null, target, lifeCycle, timeout, null, null)

    def this(intf: Class[_], target: Class[_], lifeCycle: LifeCycle, timeout: Int, remoteAddress: RemoteAddress) =
      this(intf, target, lifeCycle, timeout, null, remoteAddress)

    def this(target: Class[_], lifeCycle: LifeCycle, timeout: Int, remoteAddress: RemoteAddress) =
      this(null, target, lifeCycle, timeout, null, remoteAddress)

    def this(intf: Class[_], target: Class[_], lifeCycle: LifeCycle, timeout: Int, dispatcher: MessageDispatcher) =
      this(intf, target, lifeCycle, timeout, dispatcher, null)

    def this(target: Class[_], lifeCycle: LifeCycle, timeout: Int, dispatcher: MessageDispatcher) =
      this(null, target, lifeCycle, timeout, dispatcher, null)

    def this(target: Class[_], lifeCycle: LifeCycle, timeout: Int, dispatcher: MessageDispatcher, remoteAddress: RemoteAddress) =
      this(null, target, lifeCycle, timeout, dispatcher, remoteAddress)

    def transform =
      se.scalablesolutions.akka.kernel.config.ScalaConfig.Component(intf, target, lifeCycle.transform, timeout, dispatcher,
        if (remoteAddress != null) se.scalablesolutions.akka.kernel.config.ScalaConfig.RemoteAddress(remoteAddress.hostname, remoteAddress.port) else null)

    def newSupervised(actor: Actor) =
      se.scalablesolutions.akka.kernel.config.ScalaConfig.Supervise(actor, lifeCycle.transform)
  }
  
}