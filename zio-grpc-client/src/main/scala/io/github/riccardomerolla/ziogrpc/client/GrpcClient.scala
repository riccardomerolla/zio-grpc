package io.github.riccardomerolla.ziogrpc.client

import zio.ZIO

enum ClientError:
  case TransportFailure(details: String)
  case ProtocolFailure(details: String)

trait GrpcClientCall[-In, +Out]:
  def call(input: In): ZIO[Any, ClientError, Out]

final case class GrpcClient(channel: GrpcChannel):
  def unary[In, Out](
      call: GrpcClientCall[In, Out],
      request: In,
    ): ZIO[Any, ClientError, Out] =
    call.call(request)
