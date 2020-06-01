package com.meijisoftware.supervisor

import akka.actor.typed.ActorRef
import com.meijisoftware.assistant.AssistantProtocol
import com.meijisoftware.models.OrderStatus

/**
 * Contains all messages used to communicate with the Supervisor
 */
object SupervisorProtocol {

  sealed trait Command

  final case class OrderCompleted(orderId: String, log: Seq[OrderStatus], from: ActorRef[AssistantProtocol.Command]) extends Command
  final case class GetNextOrder() extends Command

}
