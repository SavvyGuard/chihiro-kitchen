package com.meijisoftware.models

import java.time.ZonedDateTime

import com.meijisoftware.stocker.ShelfTemperature.ShelfTemperature

/**
 * The request for an order
 * @param orderId the id of the order
 * @param name the name of the order
 * @param temp the desired temperature to keep the order at
 * @param shelfLife used to calculate how long an order can stay fresh
 * @param decayRate used to calculate how fast the order decays
 * @param createTime the time the request was created
 */
final case class OrderRequest(orderId: String, name: String, temp: ShelfTemperature, shelfLife: Int, decayRate: Float, createTime: ZonedDateTime)
