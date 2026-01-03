package io.github.riccardomerolla.ziogrpc.client

import zio.{ Scope, UIO, ZIO }

import io.grpc.ManagedChannel
import io.grpc.netty.NettyChannelBuilder

enum ChannelError:
  case ConnectionFailed(details: String)

final case class ChannelConfig(target: String)

trait GrpcChannel:
  def channel: ManagedChannel
  def shutdown: UIO[Unit]

final case class GrpcChannelLive(channel: ManagedChannel) extends GrpcChannel:
  override def shutdown: UIO[Unit] =
    ZIO.attempt(channel.shutdown()).unit.orDie

object GrpcChannel:
  def scoped(config: ChannelConfig): ZIO[Scope, ChannelError, GrpcChannel] =
    ZIO.acquireRelease(
      ZIO
        .attempt {
          GrpcChannelLive(
            NettyChannelBuilder.forTarget(config.target).usePlaintext().build()
          )
        }
        .mapError(error => ChannelError.ConnectionFailed(error.getMessage))
    )(_.shutdown)
