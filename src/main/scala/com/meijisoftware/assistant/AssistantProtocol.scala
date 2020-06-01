package com.meijisoftware.assistant

import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.Receptionist
import com.meijisoftware.courier.CourierProtocol
import com.meijisoftware.kitchen.KitchenProtocol
import com.meijisoftware.models.OrderRequest
import com.meijisoftware.stocker.StockerProtocol

/**
 * Lists the messages used to communicate with the assistant
 */
object AssistantProtocol {

  sealed trait Command

  final case class OrderReceived(order: OrderRequest, pickupLocation: ActorRef[StockerProtocol.Command], from: ActorRef[KitchenProtocol.Command]) extends Command
  final case class UpdateKitchenStatus(order: OrderRequest, from: ActorRef[KitchenProtocol.PrepareOrder], status: String) extends Command
  final case class UpdateCourierStatus(order: OrderRequest, from: ActorRef[CourierProtocol.ArriveForPickup], status: String) extends Command
  final case class OrderComplete(order: OrderRequest, successful: Boolean, from: ActorRef[CourierProtocol.ArriveForPickup]) extends Command
  final case class ListingAdapter(listing: Receptionist.Listing) extends Command

}

