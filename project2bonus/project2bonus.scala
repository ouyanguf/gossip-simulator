//imports
import akka.actor._
import scala.math._
import scala.collection.mutable.ArrayBuffer
import scala.util.Random
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import scala.language.postfixOps
//import system.dispatcher

//Messages
sealed trait GossipMessage
case object Go extends GossipMessage
case object Rumor extends GossipMessage
case object RemoveMe extends GossipMessage
case object Remind extends GossipMessage
case object Finish extends GossipMessage
case object ShutDown extends GossipMessage
case object FailureMode extends GossipMessage
case object NoNeighbor extends GossipMessage
case object PushRemind extends GossipMessage
case class Set(allneighbor: ArrayBuffer[ActorRef]) extends GossipMessage
case class Sets(s: Double) extends GossipMessage
case class SetTopo(topo: String) extends GossipMessage
case class RanAdd(ranN: ActorRef) extends GossipMessage
case class Push(s: Double, w: Double) extends GossipMessage
case class LastWord(finished: Boolean, neighbor: ArrayBuffer[ActorRef], mys: Double, myw: Double) extends GossipMessage

//Object with main method
object project2bonus {
  def main(args: Array[String]) {
    if (args.length == 4) {
      gossip(numNodes = args(0).toInt, topo = args(1), algo = args(2), failuregap = args(3).toInt) //User Specified Mode
    } else if (args.length == 3) {
      println("No Specification of Failure Period!\nUsing default setting: 1 Failure per 200 Milliseconds!")
      gossip(numNodes = args(0).toInt, topo = args(1), algo = args(2), failuregap = 200) //Default mode
    } else {
      println("Wrong Argument(s)! Using default mode:")
      println("numNodes = 100, topology = full, algorithm = gossip, failure freqnuency: 1 failure per 200 milliseconds")
      gossip(numNodes = 100, topo = "full", algo = "gossip", failuregap = 200) //Default mode
    }

    def gossip(numNodes: Int, topo: String, algo: String, failuregap: Int) {
      val system = ActorSystem("GossipSystem")
      println("Building Topology...")
      val master = system.actorOf(Props(new Master(numNodes, topo, algo, failuregap)), name = "master")
      println("Protocol Start...")
      master ! Go
    }
  }
}

//Master Actor
class Master(numNodes: Int, topo: String, algo: String, failuregap: Int) extends Actor {
  import context._
  if (numNodes == 0) {
    context.system.shutdown()
  }
  val edge: Int = ceil(sqrt(numNodes)).toInt
  val num: Int = if (topo != "2D" && topo != "imp2D") numNodes else pow(edge, 2).toInt
  var numFin: Int = 0
  var time: Long = 0
  var allActors = new ArrayBuffer[ActorRef]()
  var temp = new ArrayBuffer[ActorRef]()
  var noNeiCount: Int = 0
  var failNode: Int = 0

  for (i <- 0 until num) {
    allActors += context.actorOf(Props(new GossipActor(topo)), name = "allActors" + i) //Creat actors
  }

  if (algo == "push-sum") {
    for (i <- 0 until num) {
      allActors(i) ! Sets(i.toDouble) //Set s
    }
  }

  topo match { //Set Neighbor Info for each node
    case "full" =>
      for (i <- 0 until num) {
        allActors(i) ! Set(allActors - allActors(i)) //Set Neighbor Info
      }
    case "2D" =>

      allActors(0) ! Set(temp += (allActors(1), allActors(edge))) //set neighbor for first element in first line
      temp = new ArrayBuffer[ActorRef]()

      for (i <- 1 to edge - 2) {
        allActors(i) ! Set(temp += (allActors(i - 1), allActors(i + 1), allActors(i + edge))) //first line except first and last
        temp = new ArrayBuffer[ActorRef]()
      }

      allActors(edge - 1) ! Set(temp += (allActors(edge - 2), allActors(edge - 1 + edge))) //last one of first line
      temp = new ArrayBuffer[ActorRef]()

      for (i: Int <- edge to num - edge - 1) { //Middle lines
        if (i % edge == 0) {
          allActors(i) ! Set(temp += (allActors(i - edge), allActors(i + edge), allActors(i + 1)))
          temp = new ArrayBuffer[ActorRef]()
        } else if (i % edge == edge - 1) {
          allActors(i) ! Set(temp += (allActors(i - edge), allActors(i + edge), allActors(i - 1)))
          temp = new ArrayBuffer[ActorRef]()
        } else {
          allActors(i) ! Set(temp += (allActors(i - edge), allActors(i + edge), allActors(i - 1), allActors(i + 1)))
          temp = new ArrayBuffer[ActorRef]()
        }
      }

      allActors(num - edge) ! Set(temp += (allActors(num - edge - edge), allActors(num - edge + 1))) //set neighbor for first element in last line
      temp = new ArrayBuffer[ActorRef]()

      for (i <- num - edge + 1 to num - 2) {
        allActors(i) ! Set(temp += (allActors(i - 1), allActors(i + 1), allActors(i - edge))) //last line except first and last element
        temp = new ArrayBuffer[ActorRef]()
      }

      allActors(num - 1) ! Set(temp += (allActors(num - 2), allActors(num - 1 - edge))) //last one of last line
      temp = new ArrayBuffer[ActorRef]()

    case "line" =>

      allActors(0) ! Set(temp += allActors(1)) //set neighbor for first element in line
      temp = new ArrayBuffer[ActorRef]()

      for (i <- 1 to num - 2) {
        allActors(i) ! Set(temp += (allActors(i - 1), allActors(i + 1))) //line except first and last
        temp = new ArrayBuffer[ActorRef]()
      }

      allActors(num - 1) ! Set(temp += allActors(num - 2)) //last one of line
      temp = new ArrayBuffer[ActorRef]()

    case "imp2D" =>

      var ranList = allActors.clone()
      var ranN: ActorRef = null
      allActors(0) ! Set(temp += (allActors(1), allActors(edge))) //set neighbor for first element in first line

      ranN = (ranList - allActors(0) -- temp)(Random.nextInt((ranList - allActors(0) -- temp).length))

      allActors(0) ! RanAdd(ranN)

      ranN ! RanAdd(allActors(0))

      ranList -= (allActors(0), ranN)

      temp = new ArrayBuffer[ActorRef]()

      for (i <- 1 to edge - 2) {
        allActors(i) ! Set(temp += (allActors(i - 1), allActors(i + 1), allActors(i + edge))) //first line except first and last
        if (ranList.contains(allActors(i))) {
          ranN = (ranList - allActors(i) -- temp)(Random.nextInt((ranList - allActors(i) -- temp).length))
          allActors(i) ! RanAdd(ranN)
          ranN ! RanAdd(allActors(i))
          ranList -= (allActors(i), ranN)
        }
        temp = new ArrayBuffer[ActorRef]()
      }

      allActors(edge - 1) ! Set(temp += (allActors(edge - 2), allActors(edge - 1 + edge))) //last one of first line
      if (ranList.contains(allActors(edge - 1))) {
        ranN = (ranList - allActors(edge - 1) -- temp)(Random.nextInt((ranList - allActors(edge - 1) -- temp).length))
        allActors(edge - 1) ! RanAdd(ranN)
        ranN ! RanAdd(allActors(edge - 1))
        ranList -= (allActors(edge - 1), ranN)
      }
      temp = new ArrayBuffer[ActorRef]()

      for (i: Int <- edge to num - edge - 1) { //Middle lines
        if (i % edge == 0) {
          allActors(i) ! Set(temp += (allActors(i - edge), allActors(i + edge), allActors(i + 1)))
          if (ranList.contains(allActors(i)) && ranList.length >= 2) {
            ranN = (ranList - allActors(i) -- temp)(Random.nextInt((ranList - allActors(i) -- temp).length))
            allActors(i) ! RanAdd(ranN)
            ranN ! RanAdd(allActors(i))
            ranList -= (allActors(i), ranN)
          }
          temp = new ArrayBuffer[ActorRef]()
        } else if (i % edge == edge - 1) {
          allActors(i) ! Set(temp += (allActors(i - edge), allActors(i + edge), allActors(i - 1)))
          if (ranList.contains(allActors(i)) && ranList.length >= 2) {
            ranN = (ranList - allActors(i) -- temp)(Random.nextInt((ranList - allActors(i) -- temp).length))
            allActors(i) ! RanAdd(ranN)
            ranN ! RanAdd(allActors(i))
            ranList -= (allActors(i), ranN)
          }
          temp = new ArrayBuffer[ActorRef]()
        } else {
          allActors(i) ! Set(temp += (allActors(i - edge), allActors(i + edge), allActors(i - 1), allActors(i + 1)))
          if (ranList.contains(allActors(i)) && ranList.length >= 2) {
            ranN = (ranList - allActors(i) -- temp)(Random.nextInt((ranList - allActors(i) -- temp).length))
            allActors(i) ! RanAdd(ranN)
            ranN ! RanAdd(allActors(i))
            ranList -= (allActors(i), ranN)
          }
          temp = new ArrayBuffer[ActorRef]()
        }
      }

      allActors(num - edge) ! Set(temp += (allActors(num - edge - edge), allActors(num - edge + 1))) //set neighbor for first element in last line
      if (ranList.contains(allActors(num - edge)) && ranList.length >= 2) {
        ranN = (ranList - allActors(num - edge) -- temp)(Random.nextInt((ranList - allActors(num - edge) -- temp).length))
        allActors(num - edge) ! RanAdd(ranN)
        ranN ! RanAdd(allActors(num - edge))
        ranList -= (allActors(num - edge), ranN)
      }
      temp = new ArrayBuffer[ActorRef]()

      for (i <- num - edge + 1 to num - 2) {
        allActors(i) ! Set(temp += (allActors(i - 1), allActors(i + 1), allActors(i - edge))) //last line except first and last element
        if (ranList.contains(allActors(i)) && ranList.length >= 2) {
          ranN = (ranList - allActors(i) -- temp)(Random.nextInt((ranList - allActors(i) -- temp).length))
          allActors(i) ! RanAdd(ranN)
          ranN ! RanAdd(allActors(i))
          ranList -= (allActors(i), ranN)
        }
        temp = new ArrayBuffer[ActorRef]()
      }

      allActors(num - 1) ! Set(temp += (allActors(num - 2), allActors(num - 1 - edge))) //last one of last line
      if (ranList.contains(allActors(num - 1)) && ranList.length >= 2) {
        ranN = (ranList - allActors(num - 1) -- temp)(Random.nextInt((ranList - allActors(num - 1) -- temp).length))
        allActors(num - 1) ! RanAdd(ranN)
        ranN ! RanAdd(allActors(num - 1))
        ranList -= (allActors(num - 1), ranN)
      }
      temp = new ArrayBuffer[ActorRef]()

  }

  def receive = {
    case Go =>
      time = System.currentTimeMillis()
      if (algo == "gossip") {
        allActors(Random.nextInt(num)) ! Rumor
      } else if (algo == "push-sum") {
        allActors(Random.nextInt(num)) ! Push(0, 0)
      } else {
        println("No such algorithm supported!\nTry \"gossip\" or \"push-sum\"!")
        context.system.shutdown()
      }

      self ! FailureMode //Start failure mode

    case Finish =>
      numFin += 1
      if (numFin == num) {
        context.system.shutdown()
        println("Number of Nodes: " + num)
        //println("Converged Nodes: " + (num - noNeiCount) + "\nNot Converged Nodes: " + noNeiCount)
        //println("Converge Ratio: " + ((num - noNeiCount).toDouble * 100 / num.toDouble) + "%")
        println("Number of Failed Nodes: " + failNode)
        println("Time: " + (System.currentTimeMillis() - time) + " milliseconds")
      }
      if (algo == "push-sum" && numFin == 1) {
        context.system.shutdown()
        println("Number of Nodes: " + num)
        //println("Converged Nodes: " + (num - noNeiCount) + "\nNot Converged Nodes: " + noNeiCount)
        //println("Converge Ratio: " + ((num - noNeiCount).toDouble * 100 / num.toDouble) + "%")
        println("Number of Failed Nodes: " + failNode)
        println("Time: " + (System.currentTimeMillis() - time) + " milliseconds")

      }
    case NoNeighbor =>
      noNeiCount += 1

    case FailureMode =>
      context.system.scheduler.scheduleOnce(1 milliseconds, allActors(Random.nextInt(num)), ShutDown) //Shutdown One Node
      context.system.scheduler.scheduleOnce(failuregap milliseconds, self, FailureMode) //Shutdown Loop

    case LastWord(finished, neighbor, mys, myw) =>
      failNode += 1
      var position = num + failNode - 1
      allActors += context.actorOf(Props(new GossipActor(topo)), name = "allActors" + position) //Creat new actors
      allActors(position) ! Set(neighbor)
      for (ac <- neighbor) {
        ac ! RanAdd(allActors(position))
      }
      if (!finished && algo == "gossip")
        self ! Finish
  }
}

//GossipActor Actor
class GossipActor(topo: String) extends Actor {
  import context._

  var neighbor = new ArrayBuffer[ActorRef]()
  var rumorCount: Int = 0
  var boss: ActorRef = null
  var isDone: Boolean = false
  var mys: Double = 1
  var myw: Double = 1
  var lastvalue: Double = 0
  var currvalue: Double = 0
  var valueCount: Int = 0
  var finished: Boolean = false
  var reached: Boolean = false

  def receive = {

    case Push(s, w) =>
      if (!isDone) {
        //println(sender.toString.charAt(47) + " to " + self.toString.charAt(47))
        mys = (s + mys) / 2
        myw = (w + myw) / 2
        currvalue = mys / myw
        if (abs(currvalue - lastvalue) <= 1e-15 && w != 0) { //changed!!
          valueCount += 1
        } else {
          valueCount = 0
        }
        lastvalue = currvalue
        if (valueCount < 30 && neighbor.length > 0) {
          //context.system.scheduler.scheduleOnce(0.1 milliseconds, neighbor(Random.nextInt(neighbor.length)), Push(mys, myw))
          neighbor(Random.nextInt(neighbor.length)) ! Push(mys, myw)
          //          if (!reached) {
          //            self ! PushRemind???????????
          //            reached = true
          //          }
        } else {
          isDone = true
          for (ac: ActorRef <- neighbor)
            ac ! RemoveMe
          if (!finished) {
            boss ! Finish
            finished = true
          }
          //context.stop(self)
        }

      }
    case PushRemind =>
      mys /= 2
      myw /= 2
      //neighbor(Random.nextInt(neighbor.length)) ! Push(mys, myw)
      context.system.scheduler.scheduleOnce(10000 milliseconds, neighbor(Random.nextInt(neighbor.length)), Push(mys, myw))
      //self ! Push(0, 0)
      context.system.scheduler.scheduleOnce(10000 milliseconds, self, PushRemind)

    case Rumor =>
      if (!isDone) {
        //println(sender.toString.charAt(47)+" to "+self.toString.charAt(47))
        if (!reached) {
          if (!finished) {
            boss ! Finish
            finished = true
          }
          reached = true
          self ! Remind
        }
        rumorCount += 1
        if (rumorCount < 10 && neighbor.length > 0) {
          //neighbor(Random.nextInt(neighbor.length)) ! Rumor 
          //if(!firstRemind){
          //neighbor(Random.nextInt(neighbor.length)) ! Rumor
          //firstRemind=true
          //}
        } else {
          //sender ! Rumor
          //neighbor(Random.nextInt(neighbor.length)) ! Rumor
          //println(self+":"+neighbor.length)
          isDone = true
          for (ac: ActorRef <- neighbor)
            ac ! RemoveMe
          neighbor = new ArrayBuffer()
          //boss ! Finish

          //context.stop(self)
        }
      }
    //      else {
    //        //weird += 1
    //        //println(weird)
    //        sender ! Remind
    //      }
    case Remind =>
      if (rumorCount < 10 && neighbor.length > 0 && !isDone) {
        neighbor(Random.nextInt(neighbor.length)) ! Rumor
        topo match {
          case "line" =>
            context.system.scheduler.scheduleOnce(800 milliseconds, self, Remind)
          case "2D" =>
            context.system.scheduler.scheduleOnce(800 milliseconds, self, Remind)
          case _ =>
            context.system.scheduler.scheduleOnce(800 milliseconds, self, Remind)
        }

        //context.system.scheduler.scheduleOnce(50 milliseconds, self, Remind)
        //Scheduler.scheduleOnece()//system.//scheduler().scheduleOnce(Duration.create(50, TimeUnit.MILLISECONDS), testActor, "foo", system.dispatcher(), null);
        //var old = System.currentTimeMillis()
        //while (System.currentTimeMillis() - old <= 10) {
        //do nothing
        //}
        //neighbor(Random.nextInt(neighbor.length)) ! Rumor
        //self ! Remind
      }

    case RemoveMe =>
      if (!isDone) {
        neighbor -= sender
        if (neighbor.length <= 0) {
          isDone = true
          if (!finished) {
            boss ! NoNeighbor
            boss ! Finish
            finished = true //??????????????????????????????????????????????

          }
          //context.stop(self)

        }
      }

    case Set(allneighbor) =>
      neighbor ++= allneighbor
      boss = sender

    case Sets(s) =>
      mys = s
      lastvalue = mys / myw

    case RanAdd(ranN) =>
      if (!isDone)
        neighbor += ranN
      else
        ranN ! RemoveMe

    case ShutDown =>
      if (!isDone) {
        boss ! LastWord(finished, neighbor, mys, myw)
        for (ac <- neighbor) {
          ac ! RemoveMe
        }
      }
      context.stop(self)
  }
}