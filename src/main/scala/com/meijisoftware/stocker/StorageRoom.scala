package com.meijisoftware.stocker

import com.meijisoftware.models.{OrderRequest, PreparedOrder}
import com.typesafe.scalalogging.Logger

import scala.collection.View

/**
 * A storage room where prepared orders of all kinds can be stored in
 * harmony.
 *
 * Note that this is not thread safe. It's meant to be accessed by a single
 * actor.
 *
 * Note that while the average case for storage and retrieval is O(1), the
 * worst case is O(n) when the StorageRoom is full. As there are physical
 * limitations preventing a storage room from being too large, in practice
 * this has little impact.
 */
class StorageRoom {


  private val hotShelf = Shelf("hot", 10, ShelfTemperature.hot)
  private val coldShelf = Shelf("cold", 10, ShelfTemperature.cold)
  private val frozen = Shelf("frozen", 10, ShelfTemperature.frozen)
  private val overflow = Shelf("overflow", 15, ShelfTemperature.overflow)

  val shelves: Seq[Shelf] = Seq(hotShelf, coldShelf, frozen, overflow)

  private val logger = Logger(classOf[StorageRoom])


  /**
   * Store a prepared order inside the storage room. The storage room
   * will always accept a new storage request, unless the prepared
   * order in question is already stale.
   *
   * Orders that become stale within the storage room will be automatically
   * removed.
   *
   * @param preparedOrder an order that has been prepared by the kitchen
   * @return false if the order is already stale, true otherwise
   */
  def storeOrder(preparedOrder: PreparedOrder): Boolean = {
    removeWasteOrders()
    val currentFreshness = SimulatedDecayedValueCalculator(preparedOrder, preparedOrder.orderInfo.temp)
    if (currentFreshness <= 0) return false
    val inTempShelves: View[Shelf] = shelves.view
      .filter(shelf => shelf.temperature == preparedOrder.orderInfo.temp)
    val desiredShelf: Option[Shelf] = inTempShelves
      .find(shelf => shelf.canAdd)
      .orElse({
        removeWasteOrders()
        inTempShelves
          .find(shelf => shelf.canAdd)
      })
    val shelfWithRoom = desiredShelf
      .getOrElse(guaranteedOverflowShelf())
    val result = shelfWithRoom.addOrder(preparedOrder)
    logger.info("Order {} has been placed on shelf {}.", preparedOrder.orderInfo.orderId, shelfWithRoom.name)
    logger.info(StorageRoomPrinter(this))
    result
  }

  /**
   * Retrieve the specified order.
   *
   * @param requestedOrder the specified order to retrieve
   * @return Some containing the requested specified order, or None
   *         if the requested order was not found
   */
  def retrieveOrder(requestedOrder: OrderRequest): Option[PreparedOrder] = {
    removeWasteOrders()
    shelves.view
      .find(shelf => shelf.hasOrder(requestedOrder.orderId))
      .flatMap(shelf => {
        val retrievedOrder = shelf.removeOrder(requestedOrder.orderId)
        logger.info("Order {} has been retrieved.", requestedOrder.orderId)
        logger.info(StorageRoomPrinter(this))
        retrievedOrder
      })
  }

  /**
   * Orders that have decayed to the point where their freshness value
   * is no longer positive should not be delivered and instead discarded
   */
  private def removeWasteOrders(): Unit = {
    shelves.view
      .flatMap(shelf => shelf.orders.map(orderInfo => (shelf, orderInfo)))
      .filter(order => SimulatedDecayedValueCalculator(order._2, order._1.temperature) <= 0)
      .foreach(order => {
        val orderId = order._2.orderInfo.orderId
        order._1.removeOrder(orderId)
        logger.warn("Order {} has decayed and will be removed.", orderId)
        logger.info(StorageRoomPrinter(this))
      })
  }

  /**
   * Guarantees an open place in an overflow shelf and returns it.
   *
   * If no overflow shelf is initially available it will try to
   * shuffle orders around and even discard other orders to make
   * room.
   */
  private def guaranteedOverflowShelf(): Shelf = {
    overFlowShelf()
      .orElse({
        shuffleFromOverflow()
        overFlowShelf()
      })
      .orElse({
        discardLeastValuableOverflow()
        overFlowShelf()
      }).get
  }

  /**
   * Return an open overflow shelf if available.
   */
  private def overFlowShelf(): Option[Shelf] = {
    shelves.view
      .filter(shelf => shelf.temperature == ShelfTemperature.overflow)
      .find(shelf => shelf.canAdd)
  }


  /**
   * shuffle orders that are currently stored in the overflow shelf
   * to a more suitable shelf if room permits
   */
  private def shuffleFromOverflow(): Unit= {
    val openShelves: Seq[Shelf] = shelves.view
      .filterNot(shelf => shelf.temperature == ShelfTemperature.overflow)
      .filter(shelf => shelf.canAdd)
      .toSeq

    val overflowShelves: Seq[Shelf] = shelves.view
      .filter(shelf => shelf.temperature == ShelfTemperature.overflow)
      .toSeq

    openShelves.foreach( openShelf =>
      overflowShelves
        .foreach(overflowShelf => shuffleFromOverflow(overflowShelf, openShelf))
    )
  }

  /**
   * Shuffle orders from a given overflow shelf to a new shelf
   * if temperatures and capacity permit. Chooses the
   * items that have the most shelf life remaining.
   *
   * @param overFlowShelf the overflow shelf in question
   * @param newShelf the new shelf in question
   */
  private def shuffleFromOverflow(overFlowShelf: Shelf, newShelf: Shelf): Unit = {
      overFlowShelf.orders
      .filter(order => order.orderInfo.temp.equals(newShelf.temperature))
      .sortWith((order1, order2) => SimulatedDecayedValueCalculator(order1, ShelfTemperature.overflow)
        > SimulatedDecayedValueCalculator(order2, ShelfTemperature.overflow))
      .take(newShelf.totalCapacity)
      .foreach(order => {
        val orderId = order.orderInfo.orderId
        overFlowShelf.removeOrder(orderId)
        newShelf.addOrder(order)
        logger.info("Order {} has been moved from overflow to shelf {}.", orderId, newShelf.name)
        logger.info(StorageRoomPrinter(this))
      })

  }

  /**
   * Discards the least valuable order from an
   * overflow shelf
   */
  private def discardLeastValuableOverflow(): Unit = {
    val leastValuableOrder: Option[(Shelf, PreparedOrder)] = shelves.view
      .filter(shelf => shelf.temperature == ShelfTemperature.overflow)
      .flatMap(shelf => shelf.orders.map(orderInfo => (shelf, orderInfo)))
      .maxByOption( order => SimulatedDecayedValueCalculator(order._2, order._1.temperature))
    leastValuableOrder
      .foreach(order => {
        val orderId = order._2.orderInfo.orderId
        val shelf = order._1
        shelf.removeOrder(orderId)
        logger.warn("Order {} has been discarded from shelf {} as being least valuable.", orderId, shelf.name)
        logger.info(StorageRoomPrinter(this))
      })
  }

}

object StorageRoom {
  def apply() = new StorageRoom()
}
