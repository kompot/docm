import org.joda.time.DateTime
import play.api.libs.json.JsArray
import play.api.libs.json.JsString
import scala.concurrent.{TimeoutException, Await, Future, ExecutionContext}
import scala.concurrent.duration._
import scala.sys.process._
import ExecutionContext.Implicits.global
import play.api.libs.json._

object DigitalOceanClusterSetup extends App {
  val doClientId = ""
  val doApiKey = ""
  val baseDomain = "taskmanager.biz.tm"
  // dev is used for development, may further be replaced by company name
  val company = "dev"

  // ---------------------------------------------------------------------------
  //
  // All settings go above.
  //
  // ---------------------------------------------------------------------------

  val api = new DigitalOceanApi(doClientId, doApiKey)

  def getSizeIdByMemory(js: JsValue, mem: Int): Option[Int] = {
    val find: Option[JsValue] = js.\("sizes").as[JsArray].value.find(_.\("memory").as[JsNumber].value == 512)
    find.map(_.\("id").as[JsNumber].value.toInt)
  }

  def getImageIdByName(js: JsValue, name: String): Option[Int] = {
    val find: Option[JsValue] = js.\("images").as[JsArray].value.find(_.\("name").as[JsString].value == name)
    find.map(_.\("id").as[JsNumber].value.toInt)
  }

  def getRegionIdByName(js: JsValue, name: String): Option[Int] = {
    val find: Option[JsValue] = js.\("regions").as[JsArray].value.find(_.\("name").as[JsString].value == name)
    find.map(_.\("id").as[JsNumber].value.toInt)
  }

  def getSshKeyIdByName(js: JsValue, name: String): Option[Int] = {
    val find: Option[JsValue] = js.\("ssh_keys").as[JsArray].value.find(_.\("name").as[JsString].value.toLowerCase.contains(name.toLowerCase))
    find.map(_.\("id").as[JsNumber].value.toInt)
  }

//  def getDomainIdByName(js: JsValue, name: String): Option[Int] = {
//    val find: Option[JsValue] = js.\("domains").as[JsArray].value.find(_.\("name").as[JsString].value == name)
//    find.map(_.\("id").as[JsNumber].value.toInt)
//  }

  def getDropletName(kind: DropletKind, num: Int) = getDropletNameShort(kind, num) + "." + baseDomain
  def getDropletNameShort(kind: DropletKind, num: Int) = s"${kind.kind}$num.$company"

  case class Droplet(id: Int, name: String, image_id: Int, size_id: Int, region_id: Int, backups_active: Boolean,
                     ip_address: Option[String], locked: Boolean, status: String) {
    val belongsToCurrentTaskManagerInstance = name.endsWith(s".$company.$baseDomain")
    // ip check can be probably skipped
    val isUpAndHasIp = status.toLowerCase == "active" && ip_address.isDefined
  }
  case class DropletList(status: String, droplets: List[Droplet])
  case class Domain(id: Int, name: String)
  case class DomainList(status: String, domains: List[Domain])
  case class DomainRecord(id: Int, domain_id: Int, record_type: String, name: Option[String], data: String) {
    def isEligibleForRemove: Boolean = record_type == "A" && name.exists(_.endsWith("." + company))
  }
  case class DomainRecordList(status: String, records: List[DomainRecord])
  implicit val dropletFormat = Json.format[Droplet]
  implicit val dropletListFormat = Json.format[DropletList]
  implicit val domainFormat = Json.format[Domain]
  implicit val domainListFormat = Json.format[DomainList]
  implicit val domainRecordFormat = Json.format[DomainRecord]
  implicit val domainRecordListFormat = Json.format[DomainRecordList]






  def removeDroplets() = {
    val b = Json.fromJson(Json.parse(api.getDroplets))(dropletListFormat)
    println("___________" + b)
    b.get.droplets.filter(_.belongsToCurrentTaskManagerInstance).map { d =>
      println("Will destroy droplet " + d.name)
      api.destroyDroplet(d.id)
    }
  }
  removeDroplets()

  while (!allDropletsAreRemoved()) {
    println("Not all " + DropletKind.totalCount + " droplets are removed. Sleep for 10 seconds.")
    Thread.sleep(10000)
  }

  def allDropletsAreRemoved(): Boolean =
    Json.fromJson(Json.parse(api.getDroplets))(dropletListFormat).get.droplets
      .filter(_.belongsToCurrentTaskManagerInstance).isEmpty








  def createDroplets() {
    val sizeId = getSizeIdByMemory(Json.parse(api.getSizes), 512)
    println(sizeId)

    val imageId = getImageIdByName(Json.parse(api.getImages), "Ubuntu 12.04 x32")
    println(imageId)
    val regionId = getRegionIdByName(Json.parse(api.getRegions), "Amsterdam 1")
    println(regionId)
    val keys = Json.parse(api.getSshKeys)
    val brooKey = getSshKeyIdByName(keys, "brusen")
    println("broo key id" + brooKey)
    val kompotKey = getSshKeyIdByName(keys, "fedchenk")
    println("kompot key id" + kompotKey)

    DropletKind.kinds.map { kind =>
      (1 to kind.count).map { n =>
        val name = getDropletName(kind, n)
        val res = api.createDroplet(name, sizeId.get, imageId.get, regionId.get,
          List(brooKey.get, kompotKey.get))
        println(s"Created droplet of $name " + res)
      }
    }
  }
  createDroplets()

  while (!allDropletsIsUpAndHasIp()) {
    println("Not all " + DropletKind.totalCount + " droplets are ip. Sleep for 10 seconds.")
    Thread.sleep(10000)
  }

  def allDropletsIsUpAndHasIp(): Boolean =
    Json.fromJson(Json.parse(api.getDroplets))(dropletListFormat).get.droplets
      .filter(_.belongsToCurrentTaskManagerInstance)
      .count(_.isUpAndHasIp) == DropletKind.totalCount














  val dom = Json.fromJson(Json.parse(api.getDomains))(domainListFormat).get.domains.find(_.name == baseDomain)

  def removeOldDnsEntries() {
    dom.map { domain =>
      Json.fromJson(Json.parse(api.getDomainRecords(domain.id)))(domainRecordListFormat).get.records
        .filter(_.isEligibleForRemove).map { record =>
          println("Will destroy domain record " + domain.id + ", " + record.id)
          api.destroyDomainRecord(domain.id, record.id)
        }
    }
  }
  removeOldDnsEntries()
  
  def addNewDnsEntries() {
    val droplets = Json.fromJson(Json.parse(api.getDroplets))(dropletListFormat).get.droplets
    dom.map { domain =>
      DropletKind.kinds.map { kind =>
        (1 to kind.count).map { n =>
          val dnsName = getDropletNameShort(kind, n)
          println("Will add domain record for " + dnsName)
          api.createDomainRecord(domain.id, "A", droplets.find(_.name == getDropletName(kind, n)).get.ip_address.get, dnsName)
        }
      }
    }
  }
  addNewDnsEntries()












  val droplets = Json.parse(api.getDroplets)
  println("getActiveDroplets" + Json.prettyPrint(droplets))

  def getDropletIp(js: JsValue, dropletType: DropletKind, num: Int): Option[String] = {
    val find: Option[JsValue] = js.\("droplets").as[JsArray].value.find(_.\("name").as[JsString].value == getDropletName(dropletType, num))
    find.map(_.\("ip_address").as[JsString].value)
  }

  def getSaltMasterIp(js: JsValue): Option[String] = {
    DropletKind.kinds.flatMap { kind =>
      (1 to kind.count).map { n =>
        (kind.isSaltMaster(n), kind, n)
      }.find(_._1).flatMap(f => getDropletIp(js, f._2, f._3))
    }.find(_ != None)
  }

  // prevent SSH from asking questions
  val sshDefault = Seq("ssh", "-o", "UserKnownHostsFile=/dev/null", "-o", "StrictHostKeyChecking=no",
    "-o", "CheckHostIP=no")

  val saltMasterIp = getSaltMasterIp(droplets).get
  DropletKind.kinds.map { kind =>
    (1 to kind.count).map { n =>
      getDropletIp(droplets, kind, n).map { ip =>
        println("Setup Salt on target host")
        (sshDefault ++ Seq(s"root@$ip", "apt-get --assume-yes install python-software-properties")).!
        (sshDefault ++ Seq(s"root@$ip", "add-apt-repository ppa:saltstack/salt")).!
        (sshDefault ++ Seq(s"root@$ip", "apt-get update")).!

        if (kind.isSaltMaster(n)) {
          println("Will install Salt master to " + getDropletName(kind, n))
          (sshDefault ++ Seq(s"root@$ip", "apt-get --assume-yes  install salt-master")).!
        }
        (sshDefault ++ Seq(s"root@$ip", "apt-get --assume-yes  install salt-minion")).!
        (sshDefault ++ Seq(s"root@$ip", s"echo 'master: $saltMasterIp' > /etc/salt/minion.d/master.conf")).!
        (sshDefault ++ Seq(s"root@$ip", "service salt-minion restart")).!
      }
    }
  }

  while (!allSaltMinionKeysAreAccepted) {
    println("Not all keys are accepted by Salt master. Sleep for 10 seconds.")
    Thread.sleep(10000)
    tryToAcceptEveryKey
  }

  def tryToAcceptEveryKey = {
    DropletKind.kinds.map { kind =>
      (1 to kind.count).map { n =>
        (sshDefault ++ Seq(s"root@$saltMasterIp", "salt-key -y -a " + getDropletName(kind, n))).!
      }
    }
  }

  def allSaltMinionKeysAreAccepted: Boolean = {
    val allAcceptedKeys = (sshDefault ++ Seq(s"root@$saltMasterIp", "salt-key --list=accepted")).!!
    !DropletKind.kinds.flatMap { kind =>
      (1 to kind.count).map { n =>
        allAcceptedKeys.contains(getDropletName(kind, n))
      }
    }.contains(false)
  }

















  (sshDefault ++ Seq(s"root@$saltMasterIp", "salt 'front*' pkg.install nginx")).!
  (sshDefault ++ Seq(s"root@$saltMasterIp", "salt 'front*' service.start nginx")).!



}
