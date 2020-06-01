package com.meijisoftware.stocker

import akka.actor.typed.ActorRef
import com.meijisoftware.courier.CourierProtocol
import com.meijisoftware.kitchen.KitchenProtocol
import com.meijisoftware.models.{OrderRequest, PreparedOrder}

/**
 * Contains all messages used to communicate with the stocker
 */
object StockerProtocol {

  sealed trait Command

  final case class StorePreparedOrder(order: PreparedOrder, from: ActorRef[KitchenProtocol.Command]) extends Command
  final case class RetrieveOrder(order: OrderRequest, from: ActorRef[CourierProtocol.Command]) extends Command

  final case class KitchenConnected(from: ActorRef[KitchenProtocol.Command]) extends Command

}
