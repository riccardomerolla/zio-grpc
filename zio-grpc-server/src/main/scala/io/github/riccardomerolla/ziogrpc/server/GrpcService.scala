package io.github.riccardomerolla.ziogrpc.server

import zio.Chunk

import com.google.protobuf.Descriptors

final case class GrpcService[-R](
  endpoints: Chunk[GrpcEndpoint[R, ?, ?, ?]],
  descriptor: Option[Descriptors.ServiceDescriptor] = None,
  middleware: GrpcMiddleware[R] = GrpcMiddleware.identity,
)

object GrpcService:
  def empty[R]: GrpcService[R] = GrpcService(Chunk.empty)

  extension [R](self: GrpcService[R])
    def ++(other: GrpcService[R]): GrpcService[R] =
      GrpcService(
        self.endpoints.map(_.withMiddleware(self.middleware)) ++
          other.endpoints.map(_.withMiddleware(other.middleware))
      )

    def withMiddleware(mw: GrpcMiddleware[R]): GrpcService[R] =
      self.copy(middleware = self.middleware ++ mw)
