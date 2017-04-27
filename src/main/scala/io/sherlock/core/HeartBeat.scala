package io.sherlock.core

import com.github.levkhomich.akka.tracing.TracingSupport

case class HeartBeat(ip: String, path: String, port: Int) extends TracingSupport
