package io.github.riccardomerolla.ziogrpc.server

import zio.{ Chunk, Scope, ZIO }

object ZioGrpc:
  def server[R](
      config: ServerConfig,
      services: Chunk[GrpcService[R]],
    ): ZIO[Scope, ServerError, GrpcServer] =
    GrpcServer.scoped(config, services)

  def server[R](
      host: String,
      port: Int,
    )(
      services: GrpcService[R]*
    ): ZIO[Scope, ServerError, GrpcServer] =
    server(ServerConfig(host, port), Chunk.fromIterable(services))
