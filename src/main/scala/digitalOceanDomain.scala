case class Node(name: String, roles: List[String])

case class Droplet(id: Int, name: String, image_id: Int, size_id: Int, region_id: Int, backups_active: Boolean,
                   ip_address: Option[String], locked: Boolean, status: String) {
  val currentSite = name.endsWith(DigitalOceanClusterSetup.nodeSuffix + DigitalOceanClusterSetup.baseDomain)
  // ip check can be probably skipped
  val isUpAndHasIp = status.toLowerCase == "active" && ip_address.isDefined
}

case class DropletList(status: String, droplets: List[Droplet])

case class Domain(id: Int, name: String)

case class DomainList(status: String, domains: List[Domain])

case class DomainRecord(id: Int, domain_id: Int, record_type: String, name: Option[String], data: String) {
  def isEligibleForRemove = record_type == "A" && name.exists(_.endsWith(DigitalOceanClusterSetup.nodeSuffix))
}

case class DomainRecordList(status: String, records: List[DomainRecord])

case class Size(id: Int, name: String, memory: Int, cpu: Int, disk: Int)

case class SizeList(status: String, sizes: List[Size])

case class Image(id: Int, name: String, distribution: String, public: Boolean)

case class ImageList(status: String, images: List[Image])

case class Region(id: Int, name: String, slug: String)

case class RegionList(status: String, regions: List[Region])

case class SshKey(id: Int, name: String)

case class SshKeyList(status: String, ssh_keys: List[SshKey])
