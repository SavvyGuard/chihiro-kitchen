package com.meijisoftware.kitchen

import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.Receptionist
import com.meijisoftware.assistant.AssistantProtocol
import com.meijisoftware.models.{OrderRequest, PreparedOrder}
import com.meijisoftware.stocker.StockerProtocol

/**
 * Contains all messages used to communicate with the kitchen
 */
object KitchenProtocol {

  sealed trait Command

  final case class PrepareOrder(order: OrderRequest, from: ActorRef[AssistantProtocol.Command]) extends Command
  final case class OrderStored(order: PreparedOrder, from: ActorRef[StockerProtocol.Command]) extends Command

  final case class StockerListing(listing: Receptionist.Listing) extends Command

}
