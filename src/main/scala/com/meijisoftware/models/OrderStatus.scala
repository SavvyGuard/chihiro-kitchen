package com.meijisoftware.models

import java.time.ZonedDateTime

/**
 * An order's status at a given time.
 *
 * @param status a short identifier for an order's status
 * @param datetime the time of the status
 */
case class OrderStatus(status: String, datetime: ZonedDateTime)
