package com.meijisoftware.models

import java.time.ZonedDateTime

/**
 * The finished order by the kitchen
 *
 * @param orderInfo the original request for the order
 * @param preparedTime when the order was actually prepared
 */
final case class PreparedOrder(orderInfo: OrderRequest, preparedTime: ZonedDateTime)
