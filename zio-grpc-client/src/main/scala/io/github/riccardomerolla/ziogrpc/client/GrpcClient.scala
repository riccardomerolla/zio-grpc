package io.github.riccardomerolla.ziogrpc.client

import zio.ZIO

import io.github.riccardomerolla.ziogrpc.core.{ GrpcErrorCodec, GrpcStatusInterop }
import io.grpc.stub.ClientCalls
import io.grpc.{ CallOptions, MethodDescriptor, StatusException }

enum ClientError[+E]:
  case Remote(error: E)
  case Transport(status: io.grpc.Status)
  case CallFailure(details: String)

trait GrpcClientCall[In, Out]:
  def methodDescriptor: MethodDescriptor[In, Out]

object GrpcClientCall:
  def unary[In, Out](
    descriptor: MethodDescriptor[In, Out]
  ): GrpcClientCall[In, Out] =
    new GrpcClientCall[In, Out]:
      override def methodDescriptor: MethodDescriptor[In, Out] = descriptor

final case class GrpcClient(channel: GrpcChannel):
  def unary[In, Out, E](
    call: GrpcClientCall[In, Out],
    request: In,
  )(using codec: GrpcErrorCodec[E]
  ): ZIO[Any, ClientError[E], Out] =
    ZIO
      .attemptBlocking(
        ClientCalls.blockingUnaryCall(
          channel.channel,
          call.methodDescriptor,
          CallOptions.DEFAULT,
          request,
        )
      )
      .mapError {
        case exception: StatusException                =>
          GrpcStatusInterop
            .fromStatus(exception.getStatus)
            .map(ClientError.Remote(_))
            .getOrElse(ClientError.Transport(exception.getStatus))
        case exception: io.grpc.StatusRuntimeException =>
          GrpcStatusInterop
            .fromStatus(exception.getStatus)
            .map(ClientError.Remote(_))
            .getOrElse(ClientError.Transport(exception.getStatus))
        case other                                     =>
          ClientError.CallFailure(Option(other.getMessage).getOrElse(other.toString))
      }
