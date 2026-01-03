package io.github.riccardomerolla.ziogrpc.server

import zio.ZIO

import io.github.riccardomerolla.ziogrpc.core.GrpcMetadata

trait GrpcHandler[-R, +E, -In, +Out]:
  def handle(metadata: GrpcMetadata, input: In): ZIO[R, E, Out]

object GrpcHandler:
  def fromFunction[R, E, In, Out](
      f: (GrpcMetadata, In) => ZIO[R, E, Out]
    ): GrpcHandler[R, E, In, Out] =
    new GrpcHandler[R, E, In, Out]:
      override def handle(metadata: GrpcMetadata, input: In): ZIO[R, E, Out] =
        f(metadata, input)

  def const[R, E, In, Out](value: Out): GrpcHandler[R, E, In, Out] =
    fromFunction((_, _) => ZIO.succeed(value))
