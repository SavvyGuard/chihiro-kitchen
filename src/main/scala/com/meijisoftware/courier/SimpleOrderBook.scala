package com.meijisoftware.courier

import akka.actor.typed.ActorRef
import com.meijisoftware.assistant.AssistantProtocol
import com.meijisoftware.models.OrderRequest
import com.meijisoftware.stocker.StockerProtocol

import scala.collection.mutable

trait OrderBook {
  def addOrder(requester: ActorRef[AssistantProtocol.Command], pickupLocation: ActorRef[StockerProtocol.Command], order: OrderRequest): Boolean
  def removeOrder(orderId: String): Boolean
  def hasOrder(orderId: String): Boolean
  def pickupLocation(orderId: String): Option[ActorRef[StockerProtocol.Command]]
  def requester(orderId: String): Option[ActorRef[AssistantProtocol.Command]]
}

/**
 * An Order Book for making deliveries
 *
 * It contains all the orders that the current courier is working on, along with information
 * on who requested the order (which assistant) as well as where to pickup the order.
 *
 * Note that this is not thread safe and should only be accessed by a singe entity (an individual
 * courier).
 *
 * Create using the companion object
 */
class SimpleOrderBook extends OrderBook {

  private final case class DeliveryInfo(order: OrderRequest, requester: ActorRef[AssistantProtocol.Command], pickupLocation: ActorRef[StockerProtocol.Command])

  private val deliveryBook: mutable.Map[String, DeliveryInfo] = mutable.Map.empty

  /**
   * Add an order to the book
   * @param requester the person who is making the delivery request
   * @param pickupLocation where to pickup the order (stocker)
   * @param order the order in question
   * @return boolean indicating the success of the request
   */
  def addOrder(requester: ActorRef[AssistantProtocol.Command], pickupLocation: ActorRef[StockerProtocol.Command], order: OrderRequest): Boolean = {
    val deliveryInfo = DeliveryInfo(order, requester, pickupLocation)
    deliveryBook.addOne((order.orderId, deliveryInfo))
    true
  }

  /**
   * Return the pickup location for a given order
   * @param orderId the id of an order
   * @return A stocker indicating where to pickup
   */
  def pickupLocation(orderId: String): Option[ActorRef[StockerProtocol.Command]] = {
    deliveryBook.get(orderId)
      .map(deliveryInfo => deliveryInfo.pickupLocation)
  }

  /**
   * Return the requester for a given order
   * @param orderId the id of an order
   * @return the assistant who placed the delivery request
   */
  def requester(orderId: String): Option[ActorRef[AssistantProtocol.Command]] = {
    deliveryBook.get(orderId)
      .map(deliveryInfo => deliveryInfo.requester)
  }

  /**
   * Remove the order from the delivery book
   * @param orderId the id of an order
   * @return boolean indicating the success of the operation
   */
  def removeOrder(orderId: String): Boolean  = {
    val removedItem = deliveryBook.remove(orderId)
    removedItem.nonEmpty
  }

  /**
   * Indicate if an order is stored within the order book
   * @param orderId the id of an order
   * @return true if the order as found
   */
  def hasOrder(orderId: String): Boolean = {
    deliveryBook.contains(orderId)
  }

}

/**
 * Factory for generating SimpleOrderBook instances
 */
object SimpleOrderBook {
  def apply(): SimpleOrderBook = new SimpleOrderBook
}
