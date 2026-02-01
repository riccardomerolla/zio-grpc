package io.github.riccardomerolla.ziogrpc.client

import zio.{ LogAnnotation, ZIO }

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

final case class GrpcClient(channel: GrpcChannel, logging: Boolean = false):
  def unary[In, Out, E](
    call: GrpcClientCall[In, Out],
    request: In,
  )(using codec: GrpcErrorCodec[E]
  ): ZIO[Any, ClientError[E], Out] =
    if logging then loggedUnary(call, request)
    else executeCall(call, request)

  private def loggedUnary[In, Out, E](
    call: GrpcClientCall[In, Out],
    request: In,
  )(using codec: GrpcErrorCodec[E]
  ): ZIO[Any, ClientError[E], Out] =
    val methodName = call.methodDescriptor.getFullMethodName
    ZIO.logAnnotate(LogAnnotation("grpc.method", methodName)) {
      ZIO.logAnnotate(LogAnnotation("grpc.role", "client")) {
        for
          _         <- ZIO.logDebug("gRPC client call started")
          startTime <- ZIO.clockWith(_.nanoTime)
          result    <- executeCall(call, request).either
          endTime   <- ZIO.clockWith(_.nanoTime)
          durationMs = (endTime - startTime) / 1_000_000
          response  <- result match
                         case Right(value) =>
                           ZIO.logAnnotate(LogAnnotation("grpc.duration_ms", durationMs.toString)) {
                             ZIO.logDebug("gRPC client call completed")
                           } *> ZIO.succeed(value)
                         case Left(error)  =>
                           ZIO.logAnnotate(LogAnnotation("grpc.duration_ms", durationMs.toString)) {
                             ZIO.logAnnotate(LogAnnotation("grpc.error", error.toString)) {
                               ZIO.logWarning("gRPC client call failed")
                             }
                           } *> ZIO.fail(error)
        yield response
      }
    }

  private def executeCall[In, Out, E](
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
