package com.meijisoftware.stocker

import java.time.ZonedDateTime

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.receptionist.Receptionist
import com.meijisoftware.courier.CourierProtocol
import com.meijisoftware.courier.CourierProtocol.TransferForDelivery
import com.meijisoftware.kitchen.KitchenProtocol
import com.meijisoftware.kitchen.KitchenProtocol.OrderStored
import com.meijisoftware.models.{OrderRequest, PreparedOrder}
import com.meijisoftware.stocker.StockerProtocol.{RetrieveOrder, StorePreparedOrder}
import org.scalatest.funspec.AnyFunSpecLike

class SimulatedStockerTest extends ScalaTestWithActorTestKit with AnyFunSpecLike {

  describe("A Stocker") {

    describe("during setup") {
      it("should register with the receptionist") {
        val testProbe = createTestProbe[Receptionist.Listing]()
        testKit.system.receptionist ! Receptionist.Subscribe(SimulatedStocker.StockerServiceKey, testProbe.ref)
        spawn(SimulatedStocker())
        val message: Receptionist.Listing = testProbe.receiveMessage()
        assert(message.servicesWereAddedOrRemoved)
      }
    }

    describe("when storing an order") {

      it("should acknowledge and store the order in the store room") {
        val storageRoom = StorageRoom()
        val stocker = spawn(SimulatedStocker(storageRoom))
        val testProbe = createTestProbe[KitchenProtocol.Command]()
        val orderInfo = OrderRequest("testId", "testName", ShelfTemperature.hot, 1, 0.1.toFloat, ZonedDateTime.now())
        val preparedOrder = PreparedOrder(orderInfo, ZonedDateTime.now())
        stocker ! StorePreparedOrder(preparedOrder, testProbe.ref)
        testProbe.expectMessage(OrderStored(preparedOrder, stocker.ref))
        assert(storageRoom.retrieveOrder(preparedOrder.orderInfo).nonEmpty)
      }

    }

    describe("when retrieving an order") {
      it("should respond with an empty order if no order is found in the storage room") {
        val stocker = spawn(SimulatedStocker())
        val testProbe = createTestProbe[CourierProtocol.Command]()
        val orderInfo = OrderRequest("testId2", "testName", ShelfTemperature.hot, 1, 0.1.toFloat, ZonedDateTime.now())
        stocker ! RetrieveOrder(orderInfo, testProbe.ref)
        testProbe.expectMessage(TransferForDelivery(orderInfo, None, stocker.ref))
      }

      it("should respond with the order in question if it was found in the storage room") {
        val storageRoom = StorageRoom()
        val stocker = spawn(SimulatedStocker(storageRoom))
        val testProbe = createTestProbe[CourierProtocol.Command]()
        val orderInfo = OrderRequest("testId3", "testName", ShelfTemperature.hot, 1, 0.1.toFloat, ZonedDateTime.now())
        val preparedOrder = PreparedOrder(orderInfo, ZonedDateTime.now())
        storageRoom.storeOrder(preparedOrder)
        stocker ! RetrieveOrder(orderInfo, testProbe.ref)
        testProbe.expectMessage(TransferForDelivery(orderInfo, Option(preparedOrder), stocker.ref))
      }

    }
  }

}
