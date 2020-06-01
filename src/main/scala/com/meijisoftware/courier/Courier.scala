package com.meijisoftware.courier

import java.util.concurrent.TimeUnit

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.meijisoftware.assistant.AssistantProtocol.{OrderComplete, UpdateCourierStatus}
import com.meijisoftware.courier.CourierProtocol.{ArriveForPickup, Command, TransferForDelivery}
import com.meijisoftware.stocker.StockerProtocol.RetrieveOrder
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

trait Courier {
  def apply(orderBook: OrderBook): Behavior[Command]
}

/**
 * Factory for Courier actors
 */
object SimulatedCourier extends Courier {

  var confFile = "application.conf"

  val CourierServiceKey: ServiceKey[Command] = ServiceKey[Command]("SimulatedCourier")

  /**
   * Creates a courier actor
   *
   * During setup, it registers itself with the receptionist
   *
   * @param orderBook by default an order book will be generated for a new actor. However,
   *                  one can be passed in for testing purposes or to allow for handovers
   *                  between couriers
   */
  def apply(orderBook: OrderBook = SimpleOrderBook()): Behavior[Command] = Behaviors.setup{ context =>
    context.log.info("Registering with receptionist")
    context.system.receptionist ! Receptionist.Register(CourierServiceKey, context.self)

    receiveMessage(orderBook)
  }

  /**
   * Dispatches a general courier message to individual handlers
   */
  private def receiveMessage(orderBook: OrderBook): Behavior[Command]= Behaviors.receive { (context, message) =>
    message match {
      case arriveForPickup: ArriveForPickup => receiveArriveForPickup(orderBook, context, arriveForPickup)
      case transferForDelivery: TransferForDelivery => receiveTransferForDelivery(orderBook, context, transferForDelivery)
      case _ => Behaviors.unhandled
    }
  }

  /**
   * Handles a message notifying the courier to arrive for pickup of an item
   */
  def receiveArriveForPickup(orderBook: OrderBook, context: ActorContext[Command], message: ArriveForPickup): Behavior[Command] = {
    context.log.info("Received notification for order: {}!", message.order.orderId)
    val config = ConfigFactory.load(confFile)
    val min = config.getInt("courier.min_wait")
    val max = config.getInt("courier.max_wait")

    val secondsToArrival: Int = Random.nextInt((max - min) + 1) + min
    val durationToArrival: FiniteDuration = FiniteDuration(secondsToArrival, TimeUnit.SECONDS)
    message.from ! UpdateCourierStatus(message.order, context.self, "Arriving")
    context.log.info("Time to arrive for courier for order {}: {} s", message.order.orderId, secondsToArrival)
    context.scheduleOnce(durationToArrival, message.from, UpdateCourierStatus(message.order, context.self, "Arrived"))
    context.scheduleOnce(durationToArrival, message.location, RetrieveOrder(message.order, context.self))

    orderBook.addOrder(message.from, message.location, message.order)
    receiveMessage(orderBook)
  }

  /** Obtain the prepared order from the Stocker for delivery.
   *
   * If a prepared order is received, notify the assistant that the order
   * was delivered. Else, notify the assistant that the courier
   * is unable to fulfil the order. */
  def receiveTransferForDelivery(orderBook: OrderBook, context: ActorContext[CourierProtocol.Command], message: CourierProtocol.TransferForDelivery): Behavior[Command] = {
    val deliveryMessage = message.order
        .map(preparedOrder => preparedOrder.orderInfo)
        .map(orderInfo => OrderComplete(orderInfo, successful = true, context.self))
        .getOrElse(OrderComplete(message.request, successful = false, context.self))

    val assistant = orderBook.requester(message.request.orderId).get

    val pickupMessage = message.order match {
      case Some(_) => "Order Picked Up"
      case _ => "Order Discarded"
    }

    assistant ! UpdateCourierStatus(message.request, context.self, pickupMessage)

    assistant ! deliveryMessage

    orderBook.removeOrder(message.request.orderId)
    receiveMessage(orderBook)
  }
}
