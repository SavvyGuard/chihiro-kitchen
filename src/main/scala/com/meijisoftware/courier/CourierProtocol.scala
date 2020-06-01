package com.meijisoftware.courier

import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.Receptionist
import com.meijisoftware.assistant.AssistantProtocol
import com.meijisoftware.models.{OrderRequest, PreparedOrder}
import com.meijisoftware.stocker.StockerProtocol

/**
 * Contains all the messages used to communicate with the courier
 */
object CourierProtocol {

  sealed trait Command

  final case class ArriveForPickup(order: OrderRequest, location: ActorRef[StockerProtocol.Command], from: ActorRef[AssistantProtocol.Command]) extends Command
  final case class TransferForDelivery(request: OrderRequest, order: Option[PreparedOrder], from: ActorRef[StockerProtocol.Command]) extends Command

  final case class StockerListing(listing: Receptionist.Listing) extends Command
}
