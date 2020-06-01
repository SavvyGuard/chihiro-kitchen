package com.meijisoftware.stocker

import java.time.ZonedDateTime

import com.meijisoftware.models.{OrderRequest, PreparedOrder}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpec

class SimpleShelfTest extends AnyFunSpec with BeforeAndAfterEach {

  var shelf: Shelf = _

  private val defaultTestId = "TestOrderId"
  private val shelfName = "Test Shelf"
  private val shelfSize = 10
  private val shelfTemperature = ShelfTemperature.hot

  override def beforeEach: Unit = {
    shelf = Shelf(shelfName, shelfSize, shelfTemperature)
  }

  describe("A Shelf") {

    def sampleOrder(id: String = defaultTestId): PreparedOrder = {
      val orderInfo = OrderRequest(id, "testName", ShelfTemperature.hot, 1, 0.1.toFloat, ZonedDateTime.now())
      val preparedOrder = PreparedOrder(orderInfo, ZonedDateTime.now())
      preparedOrder
    }

    describe("has basic behavior") {


      it("should be able to add a prepared order and retrieve it") {
        val preparedOrder = sampleOrder()
        shelf.addOrder(preparedOrder)
        assert(shelf.removeOrder(preparedOrder.orderInfo.orderId).get.equals(preparedOrder))
      }

      it("should be able to provide the current capacity") {
        assert(shelf.currentCapacity.equals(shelfSize))
        val orders = Seq.range(0, shelfSize - 2)
            .map(num => num.toString)
            .map(id => sampleOrder(id))
        orders
          .foreach(order => shelf.addOrder(order))
        assert(shelf.currentCapacity.equals(2))
      }

      it("should be able to tell if an order is present") {
        val preparedOrder = sampleOrder()
        shelf.addOrder(preparedOrder)
        assert(shelf.hasOrder(preparedOrder.orderInfo.orderId))
        assert(!shelf.hasOrder("OtherOrderId"))
      }

      it("should be able to provide a sequence of all stored orders") {
        val orders = Seq.range(0, 3)
          .map(num => num.toString)
          .map(id => sampleOrder(id))
        orders
          .foreach(order => shelf.addOrder(order))
        assert(shelf.orders.size.equals(orders.size))
        shelf.orders
          .foreach(storedOrder => assert(orders.contains(storedOrder)))
      }
    }

    describe("when full") {

      def setupShelf: Unit = {
        val orders = Seq.range(0, shelfSize)
          .map(num => num.toString)
          .map(id => sampleOrder(id))
        orders
          .foreach(order => shelf.addOrder(order))
      }

      it("should have zero remaining capacity") {
        setupShelf
        assert(shelf.currentCapacity.equals(0))
      }

      it("canAdd should return false") {
        setupShelf
        assert(!shelf.canAdd)
      }

      it("should not allow new orders to be stored") {
        setupShelf
        val newOrder = sampleOrder()
        assert(!shelf.addOrder(newOrder))
        assert(!shelf.hasOrder(newOrder.orderInfo.orderId))
      }
    }
  }

}
