import scala.sys.process._

class DigitalOceanApi(clientId: String, apiKey: String) {
  val api = "https://api.digitalocean.com"
  val auth = s"client_id=$clientId&api_key=$apiKey"

  def getDroplets =       Seq("curl", "-X", "GET", "-d", auth, s"$api/droplets/").!!
  def getSizes =          Seq("curl", "-X", "GET", "-d", auth, s"$api/sizes/").!!
  def getImages =         Seq("curl", "-X", "GET", "-d", auth, s"$api/images/").!!
  def getRegions =        Seq("curl", "-X", "GET", "-d", auth, s"$api/regions/").!!
  def getSshKeys =        Seq("curl", "-X", "GET", "-d", auth, s"$api/ssh_keys/").!!
  def getDomains =        Seq("curl", "-X", "GET", "-d", auth, s"$api/domains/").!!
  def getDomainRecords(domainId: Int) =
    Seq("curl", "-X", "GET", "-d", auth, s"$api/domains/$domainId/records").!!
  def destroyDomainRecord(domainId: Int, recordId: Int) =
    Seq("curl", "-X", "GET", "-d", auth, s"$api/domains/$domainId/records/$recordId/destroy").!!
  def destroyDroplet(dropletId: Int) =
    Seq("curl", "-X", "GET", "-d", auth, s"$api/droplets/$dropletId/destroy").!!

  def createDroplet(name: String, sizeId: Int, imageId: Int, regionId: Int, sshKeyIds: List[Int]) = {
    Seq("curl", "-X", "GET", "-d", s"$auth&name=$name&size_id=$sizeId&image_id=$imageId&region_id=$regionId&ssh_key_ids=" + sshKeyIds.mkString(","),
      s"$api/droplets/new").!!
  }
  def createDomainRecord(domainId: Int, recordType: String, data: String, name: String) = {
    Seq("curl", "-X", "GET", "-d", s"$auth&name=$name&record_type=$recordType&data=$data",
      s"$api/domains/$domainId/records/new").!!
  }
}
