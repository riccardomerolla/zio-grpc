package io.github.riccardomerolla.ziogrpc.server

import zio.Chunk

import com.google.protobuf.Descriptors

final case class GrpcService[-R](
    endpoints: Chunk[GrpcEndpoint[R, ?, ?, ?]],
    descriptor: Option[Descriptors.ServiceDescriptor] = None,
  )

object GrpcService:
  def empty[R]: GrpcService[R] = GrpcService(Chunk.empty)

  extension [R](self: GrpcService[R])
    def ++(other: GrpcService[R]): GrpcService[R] =
      GrpcService(self.endpoints ++ other.endpoints)
