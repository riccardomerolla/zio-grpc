package io.github.riccardomerolla.ziogrpc.server

import zio.{ Chunk, Scope, UIO, ZIO }

enum ServerError:
  case StartupFailure(details: String)
  case ShutdownFailure(details: String)

final case class ServerConfig(host: String, port: Int)

trait GrpcServer:
  def start: ZIO[Any, ServerError, Unit]
  def shutdown: UIO[Unit]

final case class GrpcServerLive[-R](
    config: ServerConfig,
    services: Chunk[GrpcService[R]],
  ) extends GrpcServer:
  override def start: ZIO[Any, ServerError, Unit] =
    ZIO.logInfo(s"Starting gRPC server on ${config.host}:${config.port}")

  override def shutdown: UIO[Unit] =
    ZIO.logInfo("Shutting down gRPC server")

object GrpcServer:
  def scoped[R](
      config: ServerConfig,
      services: Chunk[GrpcService[R]],
    ): ZIO[Scope, ServerError, GrpcServer] =
    ZIO.acquireRelease(
      ZIO.succeed(GrpcServerLive(config, services))
    )(_.shutdown)
