package com.meijisoftware.kitchen

import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.meijisoftware.assistant.AssistantProtocol
import com.meijisoftware.kitchen.KitchenProtocol.{Command, OrderStored, PrepareOrder, StockerListing}
import com.meijisoftware.models.PreparedOrder
import com.meijisoftware.stocker.StockerProtocol.KitchenConnected
import com.meijisoftware.stocker.{SimulatedStocker, StockerProtocol}

import scala.concurrent.duration.FiniteDuration


trait Kitchen {
  def apply(): Behavior[Command]
}

/**
 * Factory object for generating simulated kitchen actors.
 *
 * The Kitchen is expected to be a long lasting actor.
 *
 * The Simulated Kitchen prepares and then
 * completes the order immediately.
 *
 */
object SimulatedKitchen extends Kitchen {

  val KitchenServiceKey: ServiceKey[Command] = ServiceKey[Command]("SimulatedKitchen")

  /**
   * Creates a kitchen actor
   *
   * During setup, it registers itself with the receptionist and requests
   * a stocker listing.
   */
  def apply(): Behavior[Command] = Behaviors.setup { context =>
    context.log.info("Registering kitchen with the receptionist")
    context.system.receptionist ! Receptionist.Register(KitchenServiceKey, context.self)
    context.log.info("Requesting a listing about storage areas")
    val listingResponseAdapter: ActorRef[Receptionist.Listing] = context.messageAdapter[Receptionist.Listing](StockerListing)
    context.system.receptionist ! Receptionist.Find(SimulatedStocker.StockerServiceKey, listingResponseAdapter)
    receiveMessage(None)
  }

  /**
   * Dispatches a general kitchen message to the correct handler
   */
  private def receiveMessage(stocker: Option[ActorRef[StockerProtocol.Command]]): Behavior[Command] = Behaviors.receive { (context, message) =>
    message match {
      case stockerListing: StockerListing => receiveStockerListing(context, stockerListing)
      case prepareOrder: PrepareOrder => receivePrepareOrder(stocker, context, prepareOrder)
      case _: OrderStored => receiveMessage(stocker)
      case _ => Behaviors.unhandled
    }
  }

  /**
   * Handles a stocker listing. If a stocker is found it get used to send its prepared orders. The
   * kitchen will not start working on orders until it successfully finds a place to store its
   * finished goods.
   */
  private def receiveStockerListing(context: ActorContext[Command], message: StockerListing): Behavior[Command] = {
    val stocker: Option[ActorRef[StockerProtocol.Command]] =  message.listing.serviceInstances(SimulatedStocker.StockerServiceKey)
      .headOption
    context.log.info("Obtained Stocker Reference: {}", message.listing.serviceInstances(SimulatedStocker.StockerServiceKey)
      .headOption)

    stocker.foreach(concreteStocker => concreteStocker ! KitchenConnected(context.self))
    receiveMessage(stocker)
  }

  /**
   * Handles a message from the assistant telling it to start working on an order. If it hasn't found
   * a suitable stocker yet, will delay work on it by resending the message to itself in the future.
   */
  private def receivePrepareOrder(stocker: Option[ActorRef[StockerProtocol.Command]], context: ActorContext[Command], message: PrepareOrder): Behavior[Command] = {
    val concreteStocker: ActorRef[StockerProtocol.Command] = stocker
      .getOrElse({
        context.scheduleOnce(FiniteDuration(100, TimeUnit.MILLISECONDS), context.self, message)
        return receiveMessage(stocker)
      })
    context.log.info("Received order: {}", message.order.orderId)
    message.from ! AssistantProtocol.OrderReceived(message.order, concreteStocker, context.self)
    message.from ! AssistantProtocol.UpdateKitchenStatus(message.order, context.self, "PreparingOrder")
    message.from ! AssistantProtocol.UpdateKitchenStatus(message.order, context.self, "PreparedOrder")
    context.log.info("Sending prepared order to stocker {}", concreteStocker)
    concreteStocker ! StockerProtocol.StorePreparedOrder(PreparedOrder(message.order, ZonedDateTime.now()), context.self)
    receiveMessage(stocker)
  }
}