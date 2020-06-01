package com.meijisoftware.kitchen

import java.time.ZonedDateTime

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.receptionist.Receptionist
import com.meijisoftware.assistant.AssistantProtocol
import com.meijisoftware.assistant.AssistantProtocol.{OrderReceived, UpdateKitchenStatus}
import com.meijisoftware.kitchen.KitchenProtocol.PrepareOrder
import com.meijisoftware.models.OrderRequest
import com.meijisoftware.stocker.SimulatedStocker.StockerServiceKey
import com.meijisoftware.stocker.StockerProtocol.{KitchenConnected, StorePreparedOrder}
import com.meijisoftware.stocker.{ShelfTemperature, StockerProtocol}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpecLike

class SimulatedKitchenTest extends ScalaTestWithActorTestKit with AnyFunSpecLike with BeforeAndAfterAll {

  /**
   * Since the Akka TestKit Framework shares an ActorSystem for the
   * entire test suite, we generate the different probes we need once
   * here.
   *
   * We can isolate each test by itself by modifying the TestKit
   * to start/terminate a new system before and after each test if
   * necessary later.
   */

  val receptionistProbe = createTestProbe[Receptionist.Listing]()
  val assistantProbe = createTestProbe[AssistantProtocol.Command]()
  val stockerProbe = createTestProbe[StockerProtocol.Command]()

  testKit.system.receptionist ! Receptionist.Subscribe(SimulatedKitchen.KitchenServiceKey, receptionistProbe.ref)
  testKit.system.receptionist ! Receptionist.Register(StockerServiceKey, stockerProbe.ref)

  val kitchen = spawn(SimulatedKitchen())

  val orderInfo = OrderRequest("testId2", "testName", ShelfTemperature.hot, 1, 0.1.toFloat, ZonedDateTime.now())
  kitchen ! PrepareOrder(orderInfo, assistantProbe.ref)

  describe("A Simulated Kitchen") {


    describe("during setup") {
      it("should register with the receptionist") {
        val message: Receptionist.Listing = receptionistProbe.receiveMessage()
        assert(message.servicesWereAddedOrRemoved)
      }

      it("should acquire a listing from the stocker") {
        stockerProbe.expectMessage(KitchenConnected(kitchen))
      }
    }

    describe("when receiving orders") {

      it("should acknowledge and notify the assistant where to schedule a pickup") {
        assistantProbe.expectMessage(OrderReceived(orderInfo, stockerProbe.ref, kitchen))
      }

      it("should notify the assistant of the preparation status") {
        assistantProbe.expectMessage(UpdateKitchenStatus(orderInfo, kitchen, "PreparingOrder"))
        assistantProbe.expectMessage(UpdateKitchenStatus(orderInfo, kitchen, "PreparedOrder"))
      }

      it("should send the prepared order to the stocker") {
        val stockerMessage: StockerProtocol.Command = stockerProbe.receiveMessage()
        stockerMessage match {
          case storePrepared: StorePreparedOrder => assert(storePrepared.order.orderInfo.equals(orderInfo))
          case _ => assert(false)
        }
      }
    }
  }

}
