package com.meijisoftware.stocker
import java.time.ZonedDateTime

import com.meijisoftware.models.{OrderRequest, PreparedOrder}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funspec.AnyFunSpec

class StorageRoomSpec extends AnyFunSpec with BeforeAndAfterEach {

  var storageRoom: StorageRoom = _
  override def beforeEach {
    storageRoom = StorageRoom()
  }

  describe("A Storage Room") {

    describe("has basic behavior") {

      it("should be able to store a prepared order and retrieve it") {
        val orderInfo = OrderRequest("testId", "testName", ShelfTemperature.hot, 1, 0.1.toFloat, ZonedDateTime.now())
        val preparedOrder = PreparedOrder(orderInfo, ZonedDateTime.now())
        storageRoom.storeOrder(preparedOrder)
        val retrievedOrder = storageRoom.retrieveOrder(orderInfo)
        assert(retrievedOrder.nonEmpty)
        assert(retrievedOrder.get.equals(preparedOrder))
      }

    }

    describe("when handling stale inventory") {

      it("should not accept orders that are already stale") {
        val orderInfo = OrderRequest("testId", "testName", ShelfTemperature.hot, 1, 1.toFloat, ZonedDateTime.now().minusSeconds(1))
        val staleOrder = PreparedOrder(orderInfo, ZonedDateTime.now().minusSeconds(1))
        assert(!storageRoom.storeOrder(staleOrder))
      }

      it("should automatically remove any order that is stale") {
        val orderInfo = OrderRequest("testId", "testName", ShelfTemperature.hot, 1, 1.toFloat, ZonedDateTime.now().minusSeconds(1))
        val almostStaleOrder = PreparedOrder(orderInfo, ZonedDateTime.now().minusNanos(900000000))
        assert(storageRoom.storeOrder(almostStaleOrder).equals(true))
        Thread.sleep(100 )
        assert(storageRoom.retrieveOrder(almostStaleOrder.orderInfo).isEmpty)
      }

    }

    describe("When at max capacity") {
      it("should still accept storage requests") {
        val orderInfo = OrderRequest("testId", "testName", ShelfTemperature.hot, 1, 0.1.toFloat, ZonedDateTime.now())
        val preparedOrder = PreparedOrder(orderInfo, ZonedDateTime.now())
        assert(storageRoom.storeOrder(preparedOrder).equals(true))
      }

      it("should remove the least fresh order on new storage requests") {
        pending
      }
    }
  }

}
