import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import play.api.libs.json.JsArray
import play.api.libs.json.JsString
import scala.concurrent.{TimeoutException, Await, Future, ExecutionContext}
import scala.concurrent.duration._
import scala.sys.process._
import ExecutionContext.Implicits.global
import play.api.libs.json._

object DigitalOceanClusterSetup extends App {
  val cnf = ConfigFactory.parseFile(new java.io.File("config"))
  // TODO check for mandatory keys in config
  val baseDomain = cnf.getString("baseDomain")
  val company = cnf.getString("nodeNameSuffix")
  val defaultMemory = cnf.getInt("memory")
  val defaultImage = cnf.getString("image")
  val defaultRegion = cnf.getString("region")

  val api = new DigitalOceanApi(cnf.getString("digitalOcean.clientId"), cnf.getString("digitalOcean.apiKey"))
  Log.print(api.droplets.map(_.droplets.map(_.name)).mkString(", "))

  def getDropletName(kind: DropletKind, num: Int) = getDropletNameShort(kind, num) + "." + baseDomain
  def getDropletNameShort(kind: DropletKind, num: Int) = s"${kind.kind}$num.$company"





  def destroyDroplets() = {
    Log.print("Starting to destroy all related droplets.")
    api.droplets.map(_.droplets.filter(_.currentSite).map { dropletToDestroy =>
      val res = api.destroyDroplet(dropletToDestroy.id)
      Log.print("Started destroying " + dropletToDestroy.name + ". Result is " + res + ".")
    })
  }
  destroyDroplets()

  while (!api.droplets.exists(_.droplets.filter(_.currentSite).isEmpty)) {
    Log.print("Not all " + DropletKind.totalCount + " droplets are destroyed. Sleep for 10 seconds.")
    Thread.sleep(10000)
  }



  def createDroplets() {
    val sizeId   = api.sizes.flatMap(_.sizes.find(_.memory == defaultMemory).map(_.id))
    val imageId  = api.images.flatMap(_.images.find(_.name == defaultImage).map(_.id))
    val regionId = api.regions.flatMap(_.regions.find(_.slug == defaultRegion).map(_.id))

    // TODO add keys
    //    val keys = Json.parse(api.getSshKeys)
    //    val brooKey = getSshKeyIdByName(keys, "brusen")
    //    println("broo key id" + brooKey)
    //    val kompotKey = getSshKeyIdByName(keys, "fedchenk")
    //    println("kompot key id" + kompotKey)
    (sizeId, imageId, regionId) match {
      case (None, _, _) => Log.err(s"Droplet memory size set in config ($defaultMemory) can't be found.")
      case (_, None, _) => Log.err(s"Image name set in config ($defaultImage) can't be found.")
      case (_, _, None) => Log.err(s"Region name set in config ($defaultRegion) can't be found.")
      case _            =>
        DropletKind.kinds.map { kind =>
          (1 to kind.count).map { n =>
            val name = getDropletName(kind, n)
            val res = api.createDroplet(name, sizeId.get, imageId.get, regionId.get, List())
            Log.print(s"Started creating droplet of $name with. Result is " + res + ".")
          }
        }
    }
  }
  createDroplets()

  while (!allDropletsIsUpAndHasIp) {
    Log.print("Not all " + DropletKind.totalCount + " droplets are up. Sleep for 10 seconds.")
    Thread.sleep(10000)
  }

  def allDropletsIsUpAndHasIp =
    api.droplets.map(_.droplets.filter(_.currentSite).count(_.isUpAndHasIp)).exists(_ == DropletKind.totalCount)











//  val dom = Json.fromJson(Json.parse(api.getDomains))(domainListFormat).get.domains.find(_.name == baseDomain)
//
//  def removeOldDnsEntries() {
//    dom.map { domain =>
//      Json.fromJson(Json.parse(api.getDomainRecords(domain.id)))(domainRecordListFormat).get.records
//        .filter(_.isEligibleForRemove).map { record =>
//          println("Will destroy domain record " + domain.id + ", " + record.id)
//          api.destroyDomainRecord(domain.id, record.id)
//        }
//    }
//  }
//  removeOldDnsEntries()
//  
//  def addNewDnsEntries() {
//    val droplets = Json.fromJson(Json.parse(api.getDroplets))(dropletListFormat).get.droplets
//    dom.map { domain =>
//      DropletKind.kinds.map { kind =>
//        (1 to kind.count).map { n =>
//          val dnsName = getDropletNameShort(kind, n)
//          println("Will add domain record for " + dnsName)
//          api.createDomainRecord(domain.id, "A", droplets.find(_.name == getDropletName(kind, n)).get.ip_address.get, dnsName)
//        }
//      }
//    }
//  }
//  addNewDnsEntries()
//
//
//
//
//
//
//
//
//
//
//
//
//  val droplets = Json.parse(api.getDroplets)
//  println("getActiveDroplets" + Json.prettyPrint(droplets))
//
//  def getDropletIp(js: JsValue, dropletType: DropletKind, num: Int): Option[String] = {
//    val find: Option[JsValue] = js.\("droplets").as[JsArray].value.find(_.\("name").as[JsString].value == getDropletName(dropletType, num))
//    find.map(_.\("ip_address").as[JsString].value)
//  }
//
//  def getSaltMasterIp(js: JsValue): Option[String] = {
//    DropletKind.kinds.flatMap { kind =>
//      (1 to kind.count).map { n =>
//        (kind.isSaltMaster(n), kind, n)
//      }.find(_._1).flatMap(f => getDropletIp(js, f._2, f._3))
//    }.find(_ != None)
//  }
//
//  // prevent SSH from asking questions
//  val sshDefault = Seq("ssh", "-o", "UserKnownHostsFile=/dev/null", "-o", "StrictHostKeyChecking=no",
//    "-o", "CheckHostIP=no")
//
//  val saltMasterIp = getSaltMasterIp(droplets).get
//  DropletKind.kinds.map { kind =>
//    (1 to kind.count).map { n =>
//      getDropletIp(droplets, kind, n).map { ip =>
//        println("Setup Salt on target host")
//        (sshDefault ++ Seq(s"root@$ip", "apt-get --assume-yes install python-software-properties")).!
//        (sshDefault ++ Seq(s"root@$ip", "add-apt-repository ppa:saltstack/salt")).!
//        (sshDefault ++ Seq(s"root@$ip", "apt-get update")).!
//
//        if (kind.isSaltMaster(n)) {
//          println("Will install Salt master to " + getDropletName(kind, n))
//          (sshDefault ++ Seq(s"root@$ip", "apt-get --assume-yes  install salt-master")).!
//        }
//        (sshDefault ++ Seq(s"root@$ip", "apt-get --assume-yes  install salt-minion")).!
//        (sshDefault ++ Seq(s"root@$ip", s"echo 'master: $saltMasterIp' > /etc/salt/minion.d/master.conf")).!
//        (sshDefault ++ Seq(s"root@$ip", "service salt-minion restart")).!
//      }
//    }
//  }
//
//  while (!allSaltMinionKeysAreAccepted) {
//    println("Not all keys are accepted by Salt master. Sleep for 10 seconds.")
//    Thread.sleep(10000)
//    tryToAcceptEveryKey
//  }
//
//  def tryToAcceptEveryKey = {
//    DropletKind.kinds.map { kind =>
//      (1 to kind.count).map { n =>
//        (sshDefault ++ Seq(s"root@$saltMasterIp", "salt-key -y -a " + getDropletName(kind, n))).!
//      }
//    }
//  }
//
//  def allSaltMinionKeysAreAccepted: Boolean = {
//    val allAcceptedKeys = (sshDefault ++ Seq(s"root@$saltMasterIp", "salt-key --list=accepted")).!!
//    !DropletKind.kinds.flatMap { kind =>
//      (1 to kind.count).map { n =>
//        allAcceptedKeys.contains(getDropletName(kind, n))
//      }
//    }.contains(false)
//  }
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//  (sshDefault ++ Seq(s"root@$saltMasterIp", "salt 'front*' pkg.install nginx")).!
//  (sshDefault ++ Seq(s"root@$saltMasterIp", "salt 'front*' service.start nginx")).!
//


}
