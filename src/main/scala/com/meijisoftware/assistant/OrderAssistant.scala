package com.meijisoftware.assistant

import java.time.ZonedDateTime

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.meijisoftware.assistant.AssistantProtocol._
import com.meijisoftware.courier.CourierProtocol.ArriveForPickup
import com.meijisoftware.courier.{CourierProtocol, SimulatedCourier}
import com.meijisoftware.kitchen.{KitchenProtocol, SimulatedKitchen}
import com.meijisoftware.models.{OrderRequest, OrderStatus}
import com.meijisoftware.supervisor.SupervisorProtocol
import com.meijisoftware.supervisor.SupervisorProtocol.OrderCompleted

trait OrderAssistant {
  def apply(request: OrderRequest, requester: ActorRef[SupervisorProtocol.Command]): Behavior[Command]
}

/**
 * Factory for OrderAssistant actors. Each Order should have
 * its own OrderAssistant.
 */
object SimulatedOrderAssistant extends OrderAssistant {

  private final case class Order(request: OrderRequest, requester: ActorRef[SupervisorProtocol.Command], orderLog: Seq[OrderStatus] )
  val AssistantServiceKey: ServiceKey[Command] = ServiceKey[Command]("SimulatedAssistant")

  /**
   * Create a new order assistant for a given order
   * @param request the request the assistant should handle
   * @param requester the entity making the request who should be notified on completion
   */
  def apply(request: OrderRequest, requester: ActorRef[SupervisorProtocol.Command]): Behavior[Command] = Behaviors.setup { context =>
    val order = Order(request, requester, Seq.empty)

    context.log.info("Registering with receptionist")
    context.system.receptionist ! Receptionist.Register(AssistantServiceKey, context.self)
    val listingResponseAdapter: ActorRef[Receptionist.Listing] = context.messageAdapter[Receptionist.Listing](ListingAdapter)

    context.log.info("Looking for a courier")
    context.system.receptionist ! Receptionist.Find(SimulatedCourier.CourierServiceKey, listingResponseAdapter)
    context.log.info("Looking for a kitchen")
    context.system.receptionist ! Receptionist.Find(SimulatedKitchen.KitchenServiceKey, listingResponseAdapter)

    receiveMessage(order, None, None)
  }

  /**
   * Dispatches a general message to the assistant to the correct message handler
   */
  private def receiveMessage(order: Order, kitchen: Option[ActorRef[KitchenProtocol.Command]], courier: Option[ActorRef[CourierProtocol.Command]]): Behavior[Command] = Behaviors.receive { (context, message) =>
    message match {
      case listing: ListingAdapter => receiveListingMessage(order, kitchen, courier, context, listing)
      case orderReceived: OrderReceived => receiveOrderReceivedMessage(order, kitchen, courier, context, orderReceived)
      case courierMessage: UpdateCourierStatus => receiveCourierMessage(order, kitchen, courier, context, courierMessage)
      case kitchenMessage: UpdateKitchenStatus => receiveKitchenMessage(order, kitchen, courier, context, kitchenMessage)
      case complete: OrderComplete => receiveOrderCompleteMessage(order, context, complete)
      case _ => Behaviors.unhandled
    }
  }

  /**
   * Receives listing information about the kitchen and courier. When all required
   * actors have been received, it begins the process by sending a message to the kitchen
   * to begin preparing the order
   */
  private def receiveListingMessage(order: Order, kitchen: Option[ActorRef[KitchenProtocol.Command]], courier: Option[ActorRef[CourierProtocol.Command]], context: ActorContext[Command], message: ListingAdapter): Behavior[Command] = {

    val receivedKitchen = message.listing.key match {
      case SimulatedKitchen.KitchenServiceKey => message.listing.serviceInstances(SimulatedKitchen.KitchenServiceKey).headOption
      case _ => kitchen
    }
    val receivedCourier = message.listing.key match {
      case SimulatedCourier.CourierServiceKey => message.listing.serviceInstances(SimulatedCourier.CourierServiceKey).headOption
      case _ => courier
    }
    (receivedKitchen, receivedCourier) match {
      case (Some(concreteKitchen), Some(concreteCourier)) => connectToKitchen(order, concreteKitchen, concreteCourier, context)
      case _ => receiveMessage(order, receivedKitchen, receivedCourier)
    }
    receiveMessage(order, receivedKitchen, receivedCourier)
  }

  /**
   * Tells the kitchen to begin preparing the order
   */
  private def connectToKitchen(order: Order, kitchen: ActorRef[KitchenProtocol.Command], courier: ActorRef[CourierProtocol.Command], context: ActorContext[Command]): Behavior[Command] = {
    kitchen ! KitchenProtocol.PrepareOrder(order.request, context.self)
    receiveMessage(order, Some(kitchen), Some(courier))
  }


  /**
   * Receives an acknowledgmentfrom the kitchen and tells the courier where to pickup
   * the order
   */
  private def receiveOrderReceivedMessage(order: Order, kitchen: Option[ActorRef[KitchenProtocol.Command]], courier: Option[ActorRef[CourierProtocol.Command]], context: ActorContext[Command], message: OrderReceived): Behavior[Command] = {
    courier.get ! ArriveForPickup(order.request, message.pickupLocation, context.self)

    receiveMessage(order, kitchen, courier)
  }

  /**
   * Receive a status update from the courier
   */
  private def receiveCourierMessage(order: Order, kitchen: Option[ActorRef[KitchenProtocol.Command]], courier: Option[ActorRef[CourierProtocol.Command]], context: ActorContext[Command], message: UpdateCourierStatus): Behavior[Command] = {
    val orderStatus = OrderStatus(message.status, ZonedDateTime.now())
    context.log.info("Courier had a status update for order: {} status: {}", message.order.orderId, message.status)
    val currentOrder = Order(order.request, order.requester, order.orderLog :+ orderStatus)
    receiveMessage(currentOrder, kitchen, courier)
  }

  /**
   * Receive a status update from the kitchen
   */
  private def receiveKitchenMessage(order: Order, kitchen: Option[ActorRef[KitchenProtocol.Command]], courier: Option[ActorRef[CourierProtocol.Command]], context: ActorContext[Command], message: UpdateKitchenStatus): Behavior[Command] =  {
    val orderStatus = OrderStatus(message.status, ZonedDateTime.now())
    context.log.info("Kitchen had a status update for order: {} status: {}", message.order.orderId, message.status)
    val currentOrder = Order(order.request, order.requester, order.orderLog :+ orderStatus)
    receiveMessage(currentOrder, kitchen, courier)
  }

  /**
   * Receive the notification from the courier that the order is complete. Send
   * a history of the order status to the requester and then stop itself
   */
  private def receiveOrderCompleteMessage(order: Order, context: ActorContext[Command], message: OrderComplete): Behavior[Command] = {
    val status: String = message.successful match {
      case true => "Successful Delivery"
      case false => "Delivery Failed"
    }
    context.log.info("Courier completed order: {} with status {}", message.order.orderId, status)
    val orderStatus = OrderStatus(status, ZonedDateTime.now())
    val currentOrder = Order(order.request, order.requester, order.orderLog :+ orderStatus)
    order.requester ! OrderCompleted(order.request.orderId, currentOrder.orderLog, context.self)
    Behaviors.stopped
  }

}
