package com.meijisoftware.supervisor

import java.util.concurrent.TimeUnit

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.Receptionist
import com.meijisoftware.courier.SimulatedCourier
import com.meijisoftware.kitchen.SimulatedKitchen
import com.meijisoftware.stocker.SimulatedStocker
import com.typesafe.config.ConfigFactory
import org.scalatest.funspec.AnyFunSpecLike

import scala.concurrent.duration.FiniteDuration

class SupervisorTest extends ScalaTestWithActorTestKit with AnyFunSpecLike {

  Supervisor.confFile = "test_application.conf"
  SimulatedCourier.confFile = "test_application.conf"

  val requestReader: RequestReader = FileRequestReader("three_orders.json")

  val supervisor: ActorRef[SupervisorProtocol.Command] = spawn(Supervisor(requestReader))

  describe("The Supervisor") {
    val assistantReceptionistProbe: TestProbe[Receptionist.Listing] = createTestProbe[Receptionist.Listing]()

    it("should start the specified number of couriers") {
      val courierReceptionistProbe: TestProbe[Receptionist.Listing] = createTestProbe[Receptionist.Listing]()
      val courierCount = ConfigFactory.load(Supervisor.confFile).getInt("supervisor.courier_count")
      testKit.system.receptionist ! Receptionist.Subscribe(SimulatedCourier.CourierServiceKey, courierReceptionistProbe.ref)
      val message = courierReceptionistProbe.receiveMessage()
      assert(message.serviceInstances(SimulatedCourier.CourierServiceKey).size.equals(courierCount))
    }

    it("should start the specified number of kitchens") {
      val kitchenReceptionistProbe: TestProbe[Receptionist.Listing] = createTestProbe[Receptionist.Listing]()
      val kitchenCount = ConfigFactory.load(Supervisor.confFile).getInt("supervisor.kitchen_count")
      testKit.system.receptionist ! Receptionist.Subscribe(SimulatedKitchen.KitchenServiceKey, kitchenReceptionistProbe.ref)
      val message = kitchenReceptionistProbe.receiveMessage()
      assert(message.serviceInstances(SimulatedKitchen.KitchenServiceKey).size.equals(kitchenCount))
    }

    it("should start the specified number of stockers") {
      val stockerReceptionistProbe: TestProbe[Receptionist.Listing] = createTestProbe[Receptionist.Listing]()
      val stockerCount = ConfigFactory.load(Supervisor.confFile).getInt("supervisor.stocker_count")
      testKit.system.receptionist ! Receptionist.Subscribe(SimulatedStocker.StockerServiceKey, stockerReceptionistProbe.ref)
      val message = stockerReceptionistProbe.receiveMessage()
      assert(message.serviceInstances(SimulatedStocker.StockerServiceKey).size.equals(stockerCount))
    }

    it("should wait the specified interval before reading another request") {
      // timing tests are difficult to accomplish in akka without a significantly high degree of work
      pending
    }

    describe("when there are no more requests") {

      it("should wait for inflight requests") {
        assertThrows[AssertionError](assistantReceptionistProbe.expectTerminated(supervisor))
      }


      it("it should stop itself") {
        val duration = FiniteDuration(4, TimeUnit.SECONDS)
        assistantReceptionistProbe.expectTerminated(supervisor, duration)
      }


    }

  }
}
