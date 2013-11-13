import scala.sys.process._
import scala.util.Try

object Ssh {
  // prevent SSH from asking questions
  val sshOptions = Seq(
    "-q",
    "-o", "UserKnownHostsFile=/dev/null",
    "-o", "StrictHostKeyChecking=no",
    "-o", "CheckHostIP=no"
  )

  val sshDefault = Seq("ssh") ++ sshOptions

  def ping(user: String, ip: String) = {
    val res = Try((sshDefault ++ Seq(s"$user@$ip", "ifconfig")).!!.contains(ip)).getOrElse(false)
    Log.print("Ping " + ip + ". Result is " + res + ".")
    res
  }

  def apply(user: String, ip: String, command: String) =
    (sshDefault ++ Seq(s"$user@$ip", command)).!!
}
