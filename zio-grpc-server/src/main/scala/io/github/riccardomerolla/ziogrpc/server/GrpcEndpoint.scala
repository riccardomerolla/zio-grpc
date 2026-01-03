package io.github.riccardomerolla.ziogrpc.server

import io.github.riccardomerolla.ziogrpc.core.GrpcErrorCodec

final case class GrpcEndpoint[-R, E, In, Out](
    methodName: String,
    handler: GrpcHandler[R, E, In, Out],
    errorCodec: GrpcErrorCodec[E],
  )
