package com.meijisoftware.stocker

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.meijisoftware.courier.CourierProtocol
import com.meijisoftware.kitchen.KitchenProtocol
import com.meijisoftware.models.PreparedOrder
import com.meijisoftware.stocker.StockerProtocol.{Command, KitchenConnected, RetrieveOrder, StorePreparedOrder}


trait Stocker {
  def apply(storageRoom: StorageRoom): Behavior[Command]
}

/**
 * Factory for stocker actors
 */
object SimulatedStocker extends Stocker {

  /**
   * This is a factory method for generated new SimulatedStocker actors.
   *
   * The Simulated Stocker controls access to the StorageRoom, and will expect
   * to be the only entity writing directly to the StorageRoom.
   *
   * A new Storage Room object will be generated by default, but one can also
   * be passed in. However, in such a case care must be made to ensure that
   * no other entity is concurrently accessing the storage room.
   *
   * @param storageRoom a storage room which the generated actor should be the
   *                    sole accessor of
   * @return a Simulated Stocker actor using the specified StorageRoom, or a unique
   *         default if none is passed in.
   */
  def apply(storageRoom: StorageRoom = StorageRoom()): Behavior[Command] = Behaviors.setup { context =>
    context.system.receptionist ! Receptionist.Register(StockerServiceKey, context.self)
    receiveMessage(storageRoom)
  }

  val StockerServiceKey: ServiceKey[Command] = ServiceKey[Command]("SimulatedStocker")

  /**
   * Dispatches general stocker messages to the correct handlers
   */
  private def receiveMessage(storageRoom: StorageRoom): Behavior[Command] = Behaviors.receive { (context, message) =>
    message match {
      case receivingOrder: StorePreparedOrder => receiveStorePreparedOrder(storageRoom, context, receivingOrder)
      case retrievingOrder: RetrieveOrder => receiveRetrieveOrder(storageRoom, context, retrievingOrder)
      case _: KitchenConnected => receiveMessage(storageRoom)
      case _ => Behaviors.unhandled
    }
  }

  /**
   * Receives a message from a kitchen telling it store an order and acknowledges the request
   */
  private def receiveStorePreparedOrder(storageRoom: StorageRoom, context: ActorContext[Command], message: StorePreparedOrder): Behavior[Command] = {
    context.log.info("Received order: {}", message.order.orderInfo.orderId)
    storageRoom.storeOrder(message.order)

    message.from ! KitchenProtocol.OrderStored(message.order, context.self)
    receiveMessage(storageRoom)
  }

  /**
   * Receives a message from a courier telling it to hand over an order.
   */
  private def receiveRetrieveOrder(storageRoom: StorageRoom, context: ActorContext[Command], message: RetrieveOrder): Behavior[Command] = {
    context.log.info("Retrieving order: {}", message.order.orderId)

    val retrievedOrder: Option[PreparedOrder] = storageRoom.retrieveOrder(message.order)

    message.from ! CourierProtocol.TransferForDelivery(message.order, retrievedOrder, context.self)

    receiveMessage(storageRoom)
  }
}