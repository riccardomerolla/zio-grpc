package io.github.riccardomerolla.ziogrpc.server

import zio.Chunk

final case class GrpcService[-R](endpoints: Chunk[GrpcEndpoint[R, ?, ?, ?]])

object GrpcService:
  def empty[R]: GrpcService[R] = GrpcService(Chunk.empty)

  extension [R](self: GrpcService[R])
    def ++(other: GrpcService[R]): GrpcService[R] =
      GrpcService(self.endpoints ++ other.endpoints)
