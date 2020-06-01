package com.meijisoftware.assistant

import java.time.ZonedDateTime

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.Receptionist
import com.meijisoftware.assistant.AssistantProtocol.{OrderComplete, OrderReceived}
import com.meijisoftware.courier.CourierProtocol.ArriveForPickup
import com.meijisoftware.courier.{CourierProtocol, SimulatedCourier}
import com.meijisoftware.kitchen.KitchenProtocol.PrepareOrder
import com.meijisoftware.kitchen.{KitchenProtocol, SimulatedKitchen}
import com.meijisoftware.models.OrderRequest
import com.meijisoftware.stocker.{ShelfTemperature, StockerProtocol}
import com.meijisoftware.supervisor.SupervisorProtocol
import com.meijisoftware.supervisor.SupervisorProtocol.OrderCompleted
import org.scalatest.funspec.AnyFunSpecLike

class OrderAssistantTest extends ScalaTestWithActorTestKit with AnyFunSpecLike {

  val receptionistProbe: TestProbe[Receptionist.Listing] = createTestProbe[Receptionist.Listing]()
  val supervisorProbe: TestProbe[SupervisorProtocol.Command] = createTestProbe[SupervisorProtocol.Command]()
  val courierProbe: TestProbe[CourierProtocol.Command] = createTestProbe[CourierProtocol.Command]()
  val stockerProbe: TestProbe[StockerProtocol.Command] = createTestProbe[StockerProtocol.Command]()
  val kitchenProbe: TestProbe[KitchenProtocol.Command] = createTestProbe[KitchenProtocol.Command]()

  val orderInfo: OrderRequest = OrderRequest("testId2", "testName", ShelfTemperature.hot, 1, 0.1.toFloat, ZonedDateTime.now())

  testKit.system.receptionist ! Receptionist.Register(SimulatedKitchen.KitchenServiceKey, kitchenProbe.ref)
  testKit.system.receptionist ! Receptionist.Register(SimulatedCourier.CourierServiceKey, courierProbe.ref)

  val orderAssistant: ActorRef[AssistantProtocol.Command] = spawn(SimulatedOrderAssistant(orderInfo, supervisorProbe.ref))

  describe("An Order Assistant") {

    describe("on start") {

      it("should register with the receptionist") {
        testKit.system.receptionist ! Receptionist.Subscribe(SimulatedOrderAssistant.AssistantServiceKey, receptionistProbe.ref)
        val message: Receptionist.Listing = receptionistProbe.receiveMessage()
        assert(message.servicesWereAddedOrRemoved)
      }

      it("should send a prepare order request to the kitchen") {
        kitchenProbe.expectMessage(PrepareOrder(orderInfo, orderAssistant))
      }
    }

    describe("when it receives a location for pickup") {

      orderAssistant ! OrderReceived(orderInfo, stockerProbe.ref, kitchenProbe.ref)

      it("should send an arrive for pickup request to the courier") {
        courierProbe.expectMessage(ArriveForPickup(orderInfo, stockerProbe.ref, orderAssistant))
      }
    }

    describe("when the order is complete") {

      orderAssistant ! OrderComplete(orderInfo, successful = true, courierProbe.ref)

      it("should provide a full log to the requester") {
        val message: SupervisorProtocol.Command = supervisorProbe.receiveMessage()
        assert(message.isInstanceOf[OrderCompleted])
      }

      it("should stop itself") {
        supervisorProbe.expectTerminated(orderAssistant)
      }
    }
  }

}
