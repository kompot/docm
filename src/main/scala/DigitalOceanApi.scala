import play.api.libs.json.{JsString, Format, JsResult, Json}
import scala.sys.process._

/**
 * All Digital Ocean API methods should return [[scala.None]] if api call failed.
 */
class DigitalOceanApi(clientId: String, apiKey: String) extends JsonHelpers {
  val api = "https://api.digitalocean.com"
  val auth = s"client_id=$clientId&api_key=$apiKey"

  def droplets: Option[DropletList] = {
    beforeApiCall("get droplets")
    getOrNone[DropletList](curlCall(Seq(auth, s"$api/droplets/")))
  }

  def sizes: Option[SizeList] = {
    beforeApiCall("get sizes")
    getOrNone[SizeList](curlCall(Seq(auth, s"$api/sizes/")))
  }

  def images: Option[ImageList] = {
    beforeApiCall("get images")
    getOrNone[ImageList](curlCall(Seq(auth, s"$api/images/")))
  }

  def regions: Option[RegionList] = {
    beforeApiCall("get regions")
    getOrNone[RegionList](curlCall(Seq(auth, s"$api/regions/")))
  }

  def sshKeys: Option[SshKeyList] = {
    beforeApiCall("get ssh keys")
    getOrNone[SshKeyList](curlCall(Seq(auth, s"$api/ssh_keys/")))
  }

  def domains: Option[DomainList] = {
    beforeApiCall("get domains")
    getOrNone[DomainList](curlCall(Seq(auth, s"$api/domains/")))
  }

  def domainRecords(domainId: Int): Option[DomainRecordList] = {
    beforeApiCall("get domain records")
    getOrNone[DomainRecordList](curlCall(Seq(auth, s"$api/domains/$domainId/records")))
  }

  def destroyDomainRecord(domainId: Int, recordId: Int): Boolean = {
    beforeApiCall("destroy domain record")
    statusIsOk(curlCall(Seq(auth, s"$api/domains/$domainId/records/$recordId/destroy")))
  }

  def destroyDroplet(dropletId: Int): Boolean = {
    beforeApiCall("destroy droplet")
    statusIsOk(curlCall(Seq(auth, s"$api/droplets/$dropletId/destroy")))
  }

  def createDroplet(name: String, sizeId: Int, imageId: Int, regionId: Int, sshKeyIds: List[Int]): Boolean = {
    beforeApiCall("create droplet")
    statusIsOk(curlCall(Seq(
      s"$auth&name=$name&size_id=$sizeId&image_id=$imageId&region_id=$regionId&ssh_key_ids=" + sshKeyIds.mkString(","),
      s"$api/droplets/new")))
  }

  def createDomainRecord(domainId: Int, recordType: String, data: String, name: String): Boolean = {
    beforeApiCall("create domain record")
    statusIsOk(curlCall(Seq(
      s"$auth&name=$name&record_type=$recordType&data=$data",
      s"$api/domains/$domainId/records/new")))
  }

  private val defaultCurl = Seq("curl", "-s", "-X", "GET", "-d")

  private def beforeApiCall(s: String) = Log.print("API call: " + s)

  private def parseResponse[A](response: String)(implicit formatter: Format[A]): JsResult[A] =
    Json.fromJson(Json.parse(response))(formatter)

  private def getOrNone[A](res: String)(implicit formatter: Format[A]): Option[A] = {
    parseResponse[A](res).map { r => Some(r) }.recoverTotal { r =>
      Log.err(res)
      None
    }
  }

  private def statusIsOk(s: String) = Json.parse(s).\("status").as[JsString].value == "OK"

  private def curlCall(seq: Seq[String]): String = (defaultCurl ++ seq).!!(Log)
}
