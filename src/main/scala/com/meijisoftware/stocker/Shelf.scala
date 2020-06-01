package com.meijisoftware.stocker

import com.meijisoftware.models.PreparedOrder
import com.meijisoftware.stocker.ShelfTemperature.ShelfTemperature

import scala.collection.mutable

/**
 * Enumeration of each ShelfTemperature along with their
 * associated decay modifier.
 */
object ShelfTemperature extends Enumeration{
    type ShelfTemperature = Value

    val hot, cold, frozen, overflow = Value
}

trait Shelf {

    val name: String
    val totalCapacity: Int
    val temperature: ShelfTemperature

    def orders: Seq[PreparedOrder]
    def addOrder(order: PreparedOrder): Boolean

    def removeOrder(orderId: String): Option[PreparedOrder]

    def hasOrder(orderId: String): Boolean

    def canAdd: Boolean

    def currentCapacity: Int
}

/**
 * A simple shelf that can store prepared orders
 *
 * Create using the companion object
 * @param name the name of the shelf
 * @param totalCapacity the total amount of orders it can store
 * @param temperature the temperature the shelf is kept at
 */
class SimpleShelf(val name: String, val totalCapacity: Int, val temperature: ShelfTemperature) extends Shelf {

    private val ordersLookup: mutable.Map[String, PreparedOrder] = mutable.Map.empty

    /**
     * Returns all the current orders stored on the shelf
     */
    def orders: Seq[PreparedOrder] = {
        ordersLookup.values.toSeq
    }

    /**
     * Add a given order to the shelf
     * @param order the prepared order in question
     * @return true if successfully added
     */
    def addOrder(order: PreparedOrder): Boolean = {
        if (canAdd) ordersLookup.addOne((order.orderInfo.orderId, order))
        hasOrder(order.orderInfo.orderId)
    }

    /**
     * Remove a given order and return it.
     * @param orderId id of the order
     * @return the oder in question if it was found, else None
     */
    def removeOrder(orderId: String): Option[PreparedOrder] = {
        ordersLookup.remove(orderId)
    }

    /**
     * Returns true if this shelf conatins
     * the given order
     * @param orderId id of the order
     */
    def hasOrder(orderId: String): Boolean = {
        ordersLookup.contains(orderId)
    }

    /**
     *  Boolean indicating if this shelf as room
     */
    def canAdd: Boolean = {
        if (currentCapacity <= 0) false else true
    }

    /**
     * returns the remaining capacity on this shelf
     */
    def currentCapacity: Int = {
        totalCapacity - ordersLookup.size
    }

}

/**
 * Factory for generating shelves
 */
object Shelf {
    def apply(name: String, totalCapacity: Int, temperature: ShelfTemperature): SimpleShelf = new SimpleShelf(name, totalCapacity, temperature)
}

