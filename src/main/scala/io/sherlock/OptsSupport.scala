package io.sherlock

trait OptsSupport {
  val Opt = """(\S+)=(\S+)""".r

  def argsToOpts(args: Seq[String]): Map[String, String] =
    args.collect { case Opt(key, value) ⇒ key → value }.toMap

  def applySystemProperties(options: Map[String, String]): Unit =
    for ((key, value) ← options if key startsWith "-D") {
      println(s"SetProperty: ${key.substring(2)} - $value")
      System.setProperty(key substring 2, value)
    }
}