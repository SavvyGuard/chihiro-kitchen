package com.meijisoftware

import akka.actor.typed.ActorSystem
import com.meijisoftware.supervisor.Supervisor

/**
 * Main entry point to the application. Will launch an actor system
 * from the top level supervisor.
 */
object ChihiroKitchen extends App{

  val supervisorContext = ActorSystem(Supervisor(), "Supervisor")
}
