package com.meijisoftware.supervisor

import java.util.concurrent.TimeUnit

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.meijisoftware.assistant.SimulatedOrderAssistant
import com.meijisoftware.courier.SimulatedCourier
import com.meijisoftware.kitchen.SimulatedKitchen
import com.meijisoftware.stocker.{SimulatedStocker, StockerProtocol}
import com.meijisoftware.supervisor.SupervisorProtocol.{Command, GetNextOrder, OrderCompleted}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.FiniteDuration

/**
 * Factory for generating a supervisor actor
 */
object Supervisor {

  var confFile = "application.conf"

  /**
   * Generates a supervisor actor
   * @param requestReader a reader that allows the supervisor to read requests
   */
  def apply(requestReader: RequestReader = FileRequestReader("orders.json")): Behavior[Command] = Behaviors.setup { context =>
    context.log.info("Starting the supervisor")
    val config = ConfigFactory.load(confFile)
    Seq.range(0, config.getInt("supervisor.courier_count"))
      .foreach(id => context.spawn(SimulatedCourier(), "courier:" + id.toString))
    Seq.range(0, config.getInt("supervisor.stocker_count"))
      .foreach(id => context.spawn(SimulatedStocker(), "stocker:" + id.toString))
    Seq.range(0, config.getInt("supervisor.kitchen_count"))
      .foreach(id => context.spawn(SimulatedKitchen(), "kitchen:" + id.toString))
    scheduleNextRequest(context)
    receiveMessage(requestReader, Set.empty)
  }

  /**
   * Dispatches general supervisor messages to relevant handlers
   */
  private def receiveMessage(requestReader: RequestReader, currentOrders: Set[String]): Behavior[Command] = Behaviors.receive {
    (context, message) => message match {
      case _: GetNextOrder => receiveGetNextOrder(requestReader, currentOrders, context)
      case orderComplete: OrderCompleted => receiveOrderComplete(requestReader, currentOrders, context, orderComplete)
      case _ => Behaviors.unhandled
    }
  }

  /**
   * Schedule the next time when the supervisor should read a new request from the request reader
   */
  private def scheduleNextRequest(context: ActorContext[Command]) = {
    val intervalSeconds = ConfigFactory.load(confFile).getInt("supervisor.request_interval_millis")
    val requestInterval: FiniteDuration = FiniteDuration(intervalSeconds, TimeUnit.MILLISECONDS)
    context.scheduleOnce(requestInterval, context.self, GetNextOrder())
  }

  /**
   * Reads a new request from the request reader. The supervisor will create a new assistant for it
   * and then add it to its list of orders in flight before scheduling the next time the supervisor
   * should read a ne request
   *
   * If no new requests are found and all orders have been completed it will terminate itself
   */
  private def receiveGetNextOrder(requestReader: RequestReader, orders: Set[String], context: ActorContext[Command]): Behavior[SupervisorProtocol.Command] = {
    val newOrder = requestReader.nextRequest()
    newOrder match {
      case Some(order) => {
        context.log.info("Creating new assistant for order: {}", order)
        context.spawn(SimulatedOrderAssistant(order, context.self), name="assistant:" + order.orderId)
        scheduleNextRequest(context)
        receiveMessage(requestReader, orders.incl(order.orderId))
      }
      case None => {
        orders.isEmpty match{
          case true => {
            context.log.info("No more orders found. Shutting down")
            Behaviors.stopped
          }
          case false => {
            context.log.info("No more orders found but some orders still in flight")
            scheduleNextRequest(context)
            receiveMessage(requestReader, orders)
          }
        }
      }
    }
  }

  /**
   * Logs the history of a completed order hen received from the assistant. It then removes the
   * order from the list of inflight orders it's tracking.
   */
  private def receiveOrderComplete(requestReader: RequestReader, orders: Set[String], context: ActorContext[Command], message: OrderCompleted): Behavior[SupervisorProtocol.Command] = {
    context.log.info("Order: {} was complete with history {}", message.orderId, message.log.map(status => status.status).mkString(" - "))
    val currentOrders = orders.filterNot(order => order.equals(message.orderId))
    receiveMessage(requestReader, currentOrders)
  }

}
