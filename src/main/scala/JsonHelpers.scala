import play.api.libs.json.Json

trait JsonHelpers {
  implicit val domainFormat = Json.format[Domain]
  implicit val domainListFormat = Json.format[DomainList]
  implicit val domainRecordFormat = Json.format[DomainRecord]
  implicit val domainRecordListFormat = Json.format[DomainRecordList]
  implicit val dropletFormat = Json.format[Droplet]
  implicit val dropletListFormat = Json.format[DropletList]
  implicit val imageFormat = Json.format[Image]
  implicit val imageListFormat = Json.format[ImageList]
  implicit val regionFormat = Json.format[Region]
  implicit val regionListFormat = Json.format[RegionList]
  implicit val sshKeyFormat = Json.format[SshKey]
  implicit val sshKeyListFormat = Json.format[SshKeyList]
  implicit val sizeFormat = Json.format[Size]
  implicit val sizeListFormat = Json.format[SizeList]
}
