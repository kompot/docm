import org.joda.time.DateTime
import scala.sys.process.ProcessLogger

object Log extends ProcessLogger {
  def print(s: => String) = {
    Console.println(s"[${DateTime.now.toString("HH:mm:ss")}] $s")
  }

  def out(s: => String) = print(s)

  def err(s: => String) = print(s)

  def buffer[T](f: => T) = f
}
