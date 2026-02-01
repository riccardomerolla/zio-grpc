package io.github.riccardomerolla.ziogrpc.server

import zio.{ LogAnnotation, ZIO }

import io.github.riccardomerolla.ziogrpc.core.{ GrpcMetadata, GrpcRequestContext }

object LoggingMiddleware:

  val default: GrpcMiddleware[Any] = new GrpcMiddleware[Any]:
    override def apply[R1 <: Any, E, In, Out](
      handler: GrpcHandler[R1, E, In, Out]
    ): GrpcHandler[R1, E, In, Out] =
      GrpcHandler.fromFunction { (metadata: GrpcMetadata, input: In) =>
        for
          ctx       <- GrpcRequestContext.get
          methodName = ctx.map(_.methodName).getOrElse("unknown")
          response  <- ZIO.logAnnotate(LogAnnotation("grpc.method", methodName)) {
                         ZIO.logAnnotate(LogAnnotation("grpc.type", "unary")) {
                           loggedCall(handler, metadata, input)
                         }
                       }
        yield response
      }

  private def loggedCall[R, E, In, Out](
    handler: GrpcHandler[R, E, In, Out],
    metadata: GrpcMetadata,
    input: In,
  ): ZIO[R, E, Out] =
    for
      _         <- ZIO.logDebug("gRPC request started")
      startTime <- ZIO.clockWith(_.nanoTime)
      result    <- handler.handle(metadata, input).either
      endTime   <- ZIO.clockWith(_.nanoTime)
      durationMs = (endTime - startTime) / 1_000_000
      response  <- result match
                     case Right(value) =>
                       ZIO.logAnnotate(LogAnnotation("grpc.duration_ms", durationMs.toString)) {
                         ZIO.logInfo("gRPC request completed")
                       } *> ZIO.succeed(value)
                     case Left(error)  =>
                       ZIO.logAnnotate(LogAnnotation("grpc.duration_ms", durationMs.toString)) {
                         ZIO.logAnnotate(LogAnnotation("grpc.error", error.toString)) {
                           ZIO.logWarning("gRPC request failed")
                         }
                       } *> ZIO.fail(error)
    yield response
