package io.github.riccardomerolla.ziogrpc.client

import zio.{ Scope, UIO, ZIO }

enum ChannelError:
  case ConnectionFailed(details: String)

final case class ChannelConfig(target: String)

trait GrpcChannel:
  def shutdown: UIO[Unit]

object GrpcChannel:
  def scoped(config: ChannelConfig): ZIO[Scope, ChannelError, GrpcChannel] =
    ZIO.acquireRelease(
      ZIO.succeed(
        new GrpcChannel:
          override def shutdown: UIO[Unit] = ZIO.unit
      )
    )(_.shutdown)
