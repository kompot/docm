import com.typesafe.config.ConfigFactory
import scala.collection.JavaConversions._
import scalax.file.Path
import scalax.io.Resource

object DigitalOceanClusterSetup extends App {
  val options = Map(
    "destroy" -> "Will only remove existing droplets, DNS records and exit.",
    "help" -> "This message"
  )

  val cnf = ConfigFactory.parseFile(new java.io.File("config"))
  val baseDomain =     cnf.getString("baseDomain")
  val nodeSuffix =     cnf.getString("nodeSuffix")
  val defaultMemory =  cnf.getInt("memory")
  val defaultImage =   cnf.getString("image")
  val defaultRegion =  cnf.getString("region")
  val defaultSshKeys = cnf.getStringList("sshKeys")

  val api = new DigitalOceanApi(cnf.getString("digitalOcean.clientId"), cnf.getString("digitalOcean.apiKey"))

  val nodes = cnf.getObjectList("nodes").toList.zipWithIndex.map { node =>
    val nodeConfig = cnf.getList("nodes").get(node._2).atKey("node")
    Node(nodeConfig.getString("node.name"), nodeConfig.getStringList("node.roles").toList)
  }

  (args.contains("help"), args.contains("destroy")) match {
    case (true, _) => options.map { kv =>
      println(kv._1)
      println("  " + kv._2)
    }
    case (_, true) => {
      destroyDroplets(confirmation = false)
      removeOldDnsEntries()
    }
    case (_, _) => {
      println("""---------------------------------------------""")
      println("""Run `sbt "run help"` to see available options""")
      println("""---------------------------------------------""")
      destroyDroplets(confirmation = true)
      createDroplets(nodes)
      removeOldDnsEntries()
      addNewDnsEntries()
      installSalt()
      preProcessSaltPillars()
      copySaltStatesToMasterAndApply()
    }
  }

  private def destroyDroplets(confirmation: Boolean) = {
    Log.print("Starting to destroy all related droplets.")
    api.droplets.map(_.dropletsCurrentSite(nodes).map { dropletToDestroy =>
      val res = api.destroyDroplet(dropletToDestroy.id)
      Log.print("Started destroying " + dropletToDestroy.name + ". Result is " + res + ".")
    })
    if (confirmation) {
      while (!api.droplets.exists(_.dropletsCurrentSite(nodes).isEmpty)) {
        Log.print("Not all droplets that belong to current site destroyed. Sleep for 10 seconds.")
        Thread.sleep(10000)
      }
    }
  }

  private def createDroplets(nodes: List[Node]) {
    val sizeId   = api.sizes.flatMap(_.sizes.find(_.memory == defaultMemory).map(_.id))
    val imageId  = api.images.flatMap(_.images.find(_.name == defaultImage).map(_.id))
    val regionId = api.regions.flatMap(_.regions.find(_.slug == defaultRegion).map(_.id))
    val keyIds   = api.sshKeys.map(_.ssh_keys.filter(key => defaultSshKeys.exists(key.name.contains)))
      .getOrElse(List()).map(_.id)
    (sizeId, imageId, regionId) match {
      case (None, _, _) => Log.err(s"Droplet memory size set in config ($defaultMemory) can't be found.")
      case (_, None, _) => Log.err(s"Image name set in config ($defaultImage) can't be found.")
      case (_, _, None) => Log.err(s"Region name set in config ($defaultRegion) can't be found.")
      case _            => nodes.map { n =>
        val name = n.dropletName
        val res = api.createDroplet(name, sizeId.get, imageId.get, regionId.get, keyIds)
        Log.print(s"Started creating droplet of $name with. Result is " + res + ".")
      }
    }

    while (!allDropletsAreUp) {
      Log.print("Not all " + nodes.length + " droplets are up. Sleep for 10 seconds.")
      Thread.sleep(10000)
    }

    def allDropletsAreUp = {
      val droplets = api.droplets
      droplets.map(_.dropletsCurrentSite(nodes).count(_.isUpAndHasIp)).exists(_ == nodes.length) &&
        ableToLogIn(droplets.map(_.dropletsCurrentSite(nodes)).getOrElse(List()))
    }

    def ableToLogIn(droplets: List[Droplet]) = {
      droplets.map(d => Ssh.ping("root", d.ip_address.get)).filter(_ == false).isEmpty
    }
  }

  private def removeOldDnsEntries() {
    api.domains.map(_.domains.map { domain =>
      api.domainRecords(domain.id).map(_.records.filter(_.isEligibleForRemove).map { record =>
        val res = api.destroyDomainRecord(domain.id, record.id)
        Log.print("Destroyed domain record " + record.data + " for domain " + domain.name + ". Result is " + res + ".")
      })
    })
  }

  private def addNewDnsEntries() {
    val droplets = api.droplets.map(_.dropletsCurrentSite(nodes)).get
    api.domains.map(_.domains.find(dm => baseDomain.endsWith(dm.name)).map { domain =>
      nodes.map { node =>
        val dnsName = node.dropletNameShort
        Log.print("Will add domain record for " + dnsName)
        droplets.find(_.name == node.dropletName).map { droplet =>
          api.createDomainRecord(domain.id, "A", droplet.ip_address.get, dnsName)
        }.orElse {
          Log.print("No droplet found for this domain name. Doing nothing.")
          None
        }
      }
    })
  }

  private def installSalt() {
    val droplets = api.droplets

    droplets.flatMap(_.dropletsCurrentSite(nodes).find(_.name == nodes.find(_.isSaltMaster)
        .map(_.dropletName).getOrElse(""))).flatMap(_.ip_address).map { saltMasterIp =>
      Log.print("Salt master IP is " + saltMasterIp)
      nodes.par.map { node =>
        droplets.flatMap(_.dropletsCurrentSite(nodes).find(_.name == node.dropletName)).map { droplet =>
          Log.print("Set up Salt on " + droplet.name)
          droplet.ip_address.map { ip =>
            Ssh("root", ip, "apt-get --assume-yes install python-software-properties")
            Ssh("root", ip, "add-apt-repository ppa:saltstack/salt")
            Ssh("root", ip, "apt-get update")

            if (node.isSaltMaster) {
              Log.print("Install Salt master to " + node.dropletName)
              Ssh("root", ip, "apt-get --assume-yes install salt-master")
            }
            Ssh("root", ip, "apt-get --assume-yes install salt-minion")
            Ssh("root", ip, s"echo 'master: $saltMasterIp' > /etc/salt/minion.d/master.conf")
            Ssh("root", ip, "service salt-minion restart")
          }
        }
      }

      while (!allSaltMinionKeysAreAccepted) {
        println("Not all keys are accepted by Salt master. Sleep for 10 seconds.")
        Thread.sleep(10000)
        tryToAcceptEveryKey
      }

      def tryToAcceptEveryKey = nodes.map { node =>
        Ssh("root", saltMasterIp, "salt-key -y -a " + node.dropletName)
      }

      def allSaltMinionKeysAreAccepted: Boolean = {
        val allAcceptedKeys = Ssh("root", saltMasterIp, "salt-key --list=accepted")
        !nodes.map { node =>
          allAcceptedKeys.contains(node.dropletName)
        }.contains(false)
      }
    } getOrElse {
      Log.err("Salt master can not be found. Be sure to assign exactly one `saltmaster` role.")
    }
  }

  private def preProcessSaltPillars() {
    val droplets = api.droplets
    val frontServerIPs = droplets.map(_.dropletsCurrentSite(nodes)).map(_.filter(_.roles.exists(_.contains("front"))).map(_.ip_address.get)).getOrElse(List())
    val balancerPillar = scala.io.Source.fromFile("pillar/balancer.template").getLines().map { line =>
      if (line.startsWith("front_ips")) {
        // TODO hard-coded play port
        "front_ips: " + frontServerIPs.mkString("['", ":9000', '", ":9000']")
      } else {
        line
      }
    }
    printToFile(new java.io.File("pillar/balancer.sls"))(pw => { balancerPillar.foreach(pw.println) })
    println(balancerPillar.toList)
  }

  private def copySaltStatesToMasterAndApply() {
    import scala.sys.process._

    api.droplets.flatMap(_.dropletsCurrentSite(nodes).find(_.name == nodes.find(_.isSaltMaster)
        .map(_.dropletName).getOrElse(""))).flatMap(_.ip_address).map { saltMasterIp =>
      Log.print("Copy Salt states to master")
      Seq("scp", "-r", "pillar", s"root@$saltMasterIp:/srv").!!
      Seq("scp", "-r", "salt", s"root@$saltMasterIp:/srv").!!
      // TODO remove this and salt/_modules/apt.py when 0.17.2 arrives
      // fix for https://github.com/saltstack/salt/issues/8015
      Ssh("root", saltMasterIp, "salt '*' saltutil.sync_modules")
      Log.print("Apply Salt states" + saltMasterIp)
      nodes.par.map { node =>
        node.saltStates.map { role =>
          Log.print("Will perform this command on salt master")
          Log.print(s"salt '${node.dropletName}' state.sls $role")
          Log.print(Ssh("root", saltMasterIp, s"salt '${node.dropletName}' state.sls $role"))
        }
      }
    }
  }

  private def printToFile(f: java.io.File)(op: java.io.PrintWriter => Unit) {
    val p = new java.io.PrintWriter(f, "utf-8")
    try { op(p) } finally { p.close() }
  }

}
