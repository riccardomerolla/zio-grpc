package io.github.riccardomerolla.ziogrpc.server

import io.github.riccardomerolla.ziogrpc.core.{ GrpcCodec, GrpcErrorCodec }
import io.grpc.MethodDescriptor

final case class GrpcEndpoint[-R, E, In, Out](
  methodName: String,
  methodType: MethodDescriptor.MethodType,
  requestCodec: GrpcCodec[In],
  responseCodec: GrpcCodec[Out],
  handler: GrpcHandler[R, E, In, Out],
  errorCodec: GrpcErrorCodec[E],
)

object GrpcEndpoint:
  extension [R, E, In, Out](self: GrpcEndpoint[R, E, In, Out])
    def withMiddleware(mw: GrpcMiddleware[R]): GrpcEndpoint[R, E, In, Out] =
      self.copy(handler = mw(self.handler))
