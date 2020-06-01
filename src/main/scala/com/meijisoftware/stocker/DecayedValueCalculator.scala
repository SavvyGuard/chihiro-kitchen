package com.meijisoftware.stocker

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import com.meijisoftware.models.PreparedOrder
import com.meijisoftware.stocker.ShelfTemperature.ShelfTemperature

trait DecayedValueCalculator {
  def apply(order: PreparedOrder, shelf: ShelfTemperature): Float
}

/**
 * Calculates the current decayed value of an order.
 */
object SimulatedDecayedValueCalculator extends DecayedValueCalculator {

  private object ShelfModifiers {
    val modifierMap = Map(
      ShelfTemperature.hot -> 1,
      ShelfTemperature.cold -> 1,
      ShelfTemperature.frozen -> 1,
      ShelfTemperature.overflow -> 2
    )

    def apply(shelfTemperature: ShelfTemperature): Int = {
      modifierMap.getOrElse(shelfTemperature, 2)
    }
  }

  /**
   * Return the current decayed value of an order
   *
   * @param order the prepared order in question
   * @param shelfTemperature the temperature of the shelf the order is placed on
   * @return the current decayed value. Negative values should be viewed as stale
   */
  def apply(order: PreparedOrder, shelfTemperature: ShelfTemperature): Float = {
    val orderAge: Int = ChronoUnit.SECONDS.between(order.preparedTime, ZonedDateTime.now()).toInt
    val decayModifier = ShelfModifiers(shelfTemperature)
    (order.orderInfo.shelfLife - order.orderInfo.decayRate * orderAge * decayModifier) / order.orderInfo.shelfLife
  }
}
