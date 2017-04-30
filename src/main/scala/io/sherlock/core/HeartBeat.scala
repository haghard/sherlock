package io.sherlock.core

import brave.propagation.TraceContext

case class HeartBeat(ip: String, path: String, port: Int)

case class HeartBeatTrace(hb: HeartBeat, traceCtx: TraceContext, tracer: brave.Tracer)
