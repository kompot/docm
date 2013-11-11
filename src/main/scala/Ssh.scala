import scala.sys.process._

object Ssh {
  // prevent SSH from asking questions
  val sshDefault = Seq("ssh",
    "-o", "UserKnownHostsFile=/dev/null",
    "-o", "StrictHostKeyChecking=no",
    "-o", "CheckHostIP=no")

  def ping(user: String, ip: String) = {
    val res = (sshDefault ++ Seq(s"$user@$ip", "ifconfig")).!!.contains(ip)
    Log.print("Ping " + ip + ". Result is " + res + ".")
    res
  }

  def apply(user: String, ip: String, command: String) =
    (sshDefault ++ Seq(s"$user@$ip", command)).!!
}
