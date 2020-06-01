package com.meijisoftware.stocker

import java.time.ZonedDateTime

import com.meijisoftware.models.{OrderRequest, PreparedOrder}
import org.scalatest.funspec.AnyFunSpec

class SimulatedDecayedValueCalculatorTest extends AnyFunSpec {


  describe("The SimulatedDecayedValueCalculator") {
    it("returns the correct value") {
      val orderInfo: OrderRequest = OrderRequest("testId", "testName", ShelfTemperature.hot, 5, 0.5.toFloat, ZonedDateTime.now().minusSeconds(5))
      val preparedOrder: PreparedOrder = PreparedOrder(orderInfo, ZonedDateTime.now().minusSeconds(5))
      assert(SimulatedDecayedValueCalculator(preparedOrder, ShelfTemperature.hot) == 0.5)
    }

    it("causes the overflow shelf to decay twice as fast") {
      val orderInfo: OrderRequest = OrderRequest("testId", "testName", ShelfTemperature.hot, 5, 0.5.toFloat, ZonedDateTime.now().minusSeconds(5))
      val preparedOrder: PreparedOrder = PreparedOrder(orderInfo, ZonedDateTime.now().minusSeconds(5))
      assert(SimulatedDecayedValueCalculator(preparedOrder, ShelfTemperature.overflow) == 0.0)
    }
  }

}
