/**
 * Copyright (C) 2009-2012 Typesafe Inc. <http://www.typesafe.com>
 * Upgraded by Amir Ghaffari to Akka 2.4.9
 */
package akka.tutorial.first.scala

import akka.actor._
import akka.routing.FromConfig
import akka.routing.RoundRobinPool
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory


object Pi extends App {
 
  calculate(nrOfWorkers = 4, nrOfElements = 10000, nrOfMessages = 10000)
 
  sealed trait PiMessage
  case object Calculate extends PiMessage
  case class Work(start: Int, nrOfElements: Int) extends PiMessage
  case class Result(value: Double) extends PiMessage
  case class PiApproximation(pi: Double, duration: Duration)
 
  class Worker extends Actor {
 
    def calculatePiFor(start: Int, nrOfElements: Int): Double = {
      var acc = 0.0
      for (i <- start until (start + nrOfElements))
        acc += 4.0 * (1 - (i % 2) * 2) / (2 * i + 1)
      acc
    }
 
    def receive = {
      case Work(start, nrOfElements) =>
        sender ! Result(calculatePiFor(start, nrOfElements)) // perform the work
    }
  }
 
  class Master(nrOfWorkers: Int, nrOfMessages: Int, nrOfElements: Int, listener: ActorRef)
    extends Actor {
 
    var pi: Double = _
    var nrOfResults: Int = _
    val start: Long = System.currentTimeMillis
 
    val workerRouter: ActorRef =
        //context.actorOf(RoundRobinPool(5).props(Props[Worker]))  // the router settings provided programmatically
        context.actorOf(FromConfig.props(Props[Worker]), "myrouter") // the router settings are provided in configuration file 
  
    def receive = {
      case Calculate =>
        for (i <- 0 until nrOfMessages) workerRouter ! Work(i * nrOfElements, nrOfElements)
      case Result(value) =>
        pi += value
        nrOfResults += 1
        if (nrOfResults == nrOfMessages) {
          // Send the result to the listener
          listener ! PiApproximation(pi, duration = (System.currentTimeMillis - start).millis)
          // Stops this actor and all its supervised children
          context.stop(self)
        }
    }
 
  }
 
  class Listener extends Actor {
    def receive = {
      case PiApproximation(pi, duration) =>
        println("\n\tPi approximation: \t\t%s\n\tCalculation time: \t%s"
          .format(pi, duration))
        context.system.shutdown()
    }
  }
 
 
  def calculate(nrOfWorkers: Int, nrOfElements: Int, nrOfMessages: Int) {
    
    val config = ConfigFactory.load()
    // Create an Akka system
    val system = ActorSystem("PiSystem",config.getConfig("MyConfig").withFallback(config))
 
    // create the result listener, which will print the result and shutdown the system
    val listener = system.actorOf(Props[Listener], name = "listener")
 
    // create the master
    val master = system.actorOf(Props(new Master(
      nrOfWorkers, nrOfMessages, nrOfElements, listener)),
      name = "master")
 
    // start the calculation
    master ! Calculate
 
  }
}
