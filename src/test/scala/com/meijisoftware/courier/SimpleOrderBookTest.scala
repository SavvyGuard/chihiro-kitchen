package com.meijisoftware.courier

import java.time.ZonedDateTime

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import com.meijisoftware.assistant.AssistantProtocol
import com.meijisoftware.models.OrderRequest
import com.meijisoftware.stocker.{ShelfTemperature, StockerProtocol}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpecLike

class SimpleOrderBookTest extends ScalaTestWithActorTestKit with AnyFunSpecLike with BeforeAndAfterEach {

  var orderBook: OrderBook = _

  override def beforeEach: Unit = {
    orderBook = SimpleOrderBook()
    orderBook.addOrder(sampleRequester, samplePickupLocation, existingOrder)
  }

  private val defaultTestId = "TestOrderId"
  private val defaultTestName = "TestName"

  private val existingOrder: OrderRequest = OrderRequest(defaultTestId, defaultTestName, ShelfTemperature.hot, 1, 0.1.toFloat, ZonedDateTime.now())
  private val sampleRequester: ActorRef[AssistantProtocol.Command] = createTestProbe[AssistantProtocol.Command]().ref
  private val samplePickupLocation: ActorRef[StockerProtocol.Command] = createTestProbe[StockerProtocol.Command]().ref

  describe("An OrderBook") {

    describe("Has basic behavior") {
      it("should be able to successfully add an order and remove it") {
        assert(orderBook.addOrder(sampleRequester, samplePickupLocation, existingOrder))
        assert(orderBook.hasOrder(existingOrder.orderId))
        assert(orderBook.removeOrder(existingOrder.orderId))
      }
    }

    describe("When an order is stored") {

      it("should be able to remove an existing order") {
        assert(orderBook.removeOrder(existingOrder.orderId))
      }

      it("should be able to tell that the order is available") {
        assert(orderBook.hasOrder(existingOrder.orderId))
      }

      it("should be able to tell who the requester of the order was") {
        assert(orderBook.requester(existingOrder.orderId).nonEmpty)
        assert(orderBook.requester(existingOrder.orderId).get.equals(sampleRequester))
      }

      it("should be able to tell where the pickup location of the order is") {
        assert(orderBook.pickupLocation(existingOrder.orderId).nonEmpty)
        assert(orderBook.pickupLocation(existingOrder.orderId).get.equals(samplePickupLocation))
      }
    }

    describe("When an order is not stored") {

      val nonExistentOrder: OrderRequest = OrderRequest("UnfoundOrderId", defaultTestName, ShelfTemperature.hot, 1, 0.1.toFloat, ZonedDateTime.now())

      it("should fail to remove the order") {
        assert(!orderBook.removeOrder(nonExistentOrder.orderId))
      }

      it("should be able to tell that the order is not available") {
        assert(!orderBook.hasOrder(nonExistentOrder.orderId))
      }

      it("should return an empty requester") {
        assert(orderBook.requester(nonExistentOrder.orderId).isEmpty)
      }

      it("should return an empty pickp location") {
        assert(orderBook.pickupLocation(nonExistentOrder.orderId).isEmpty)
      }

    }
  }

}
