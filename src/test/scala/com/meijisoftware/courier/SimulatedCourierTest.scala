package com.meijisoftware.courier

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.Receptionist
import com.meijisoftware.assistant.AssistantProtocol
import com.meijisoftware.assistant.AssistantProtocol.{OrderComplete, UpdateCourierStatus}
import com.meijisoftware.courier.CourierProtocol.{ArriveForPickup, TransferForDelivery}
import com.meijisoftware.models.{OrderRequest, PreparedOrder}
import com.meijisoftware.stocker.StockerProtocol.RetrieveOrder
import com.meijisoftware.stocker.{ShelfTemperature, StockerProtocol}
import org.scalatest.funspec.AnyFunSpecLike

import scala.concurrent.duration.FiniteDuration

class SimulatedCourierTest extends ScalaTestWithActorTestKit with AnyFunSpecLike {

  var courier: ActorRef[CourierProtocol.Command] = _
  val orderInfo: OrderRequest = OrderRequest("testId", "testName", ShelfTemperature.hot, 1, 0.1.toFloat, ZonedDateTime.now())
  var assistantProbe: TestProbe[AssistantProtocol.Command] = _
  var stockerProbe: TestProbe[StockerProtocol.Command] = _

  def initialize(): Unit = {
    courier = spawn(SimulatedCourier())
    assistantProbe = createTestProbe[AssistantProtocol.Command]()
    stockerProbe = createTestProbe[StockerProtocol.Command]()
    SimulatedCourier.confFile = "test_application.conf"
  }

  describe("A Simulated Courier") {

    describe("during setup") {
      it("should register with the receptionist") {
        val receptionistProbe: TestProbe[Receptionist.Listing] = createTestProbe[Receptionist.Listing]()
        testKit.system.receptionist ! Receptionist.Subscribe(SimulatedCourier.CourierServiceKey, receptionistProbe.ref)
        val message: Receptionist.Listing = receptionistProbe.receiveMessage()
        assert(message.servicesWereAddedOrRemoved)
      }
    }
    describe("in an happy case path") {

      initialize()

      it("should update the requester on receipt of request") {
        courier ! ArriveForPickup(orderInfo, stockerProbe.ref, assistantProbe.ref)
        assistantProbe.expectMessage(UpdateCourierStatus(orderInfo, courier, "Arriving"))
      }

      it("should update the requester on arrival") {
        assistantProbe.expectMessage(UpdateCourierStatus(orderInfo, courier, "Arrived"))
      }


      it("should send a retrieval request to the stocker on arrival") {
        stockerProbe.expectMessage(RetrieveOrder(orderInfo, courier))
      }

      it("should update the requester when it successfully picks up the item") {
        courier ! TransferForDelivery(orderInfo, Some(PreparedOrder(orderInfo, orderInfo.createTime)), stockerProbe.ref)
        assistantProbe.expectMessage(UpdateCourierStatus(orderInfo, courier, "Order Picked Up"))
      }

      it("should mark the package as successfully delivered to the original requester") {
        assistantProbe.expectMessage(OrderComplete(orderInfo, successful = true, courier))
      }
    }

    it("should arrive within the configured amount of time and update the requester") {
      initialize()
      SimulatedCourier.confFile = "courier_interval_test.conf"
      val testWaitTime = 1
      val durationToArrival: FiniteDuration = FiniteDuration(testWaitTime + 1, TimeUnit.SECONDS)
      courier ! ArriveForPickup(orderInfo, stockerProbe.ref, assistantProbe.ref)
      val currentTime = ZonedDateTime.now()
      assistantProbe.receiveMessages(1)
      assistantProbe.expectMessage(durationToArrival, UpdateCourierStatus(orderInfo, courier, "Arrived"))
      val messageReceiveTime = ZonedDateTime.now()
      assert(ChronoUnit.MILLIS.between(currentTime, messageReceiveTime) < testWaitTime * 1000 + 100)
      assert(ChronoUnit.MILLIS.between(currentTime, messageReceiveTime) > testWaitTime * 1000 - 100)
    }

    describe("when the package is unavailable") {


      it("should notify the requester if the package was discarded by the stocker") {

        initialize()
        courier ! ArriveForPickup(orderInfo, stockerProbe.ref, assistantProbe.ref)
        courier ! TransferForDelivery(orderInfo, None, stockerProbe.ref)

        assistantProbe.receiveMessages(2)
        assistantProbe.expectMessage(UpdateCourierStatus(orderInfo, courier, "Order Discarded"))
      }

      it("should notify the requester the order was unsuccessful") {
        assistantProbe.expectMessage(OrderComplete(orderInfo, successful = false, courier))
      }
    }
  }

}
