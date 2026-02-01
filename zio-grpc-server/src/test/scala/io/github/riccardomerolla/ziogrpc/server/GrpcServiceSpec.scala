package io.github.riccardomerolla.ziogrpc.server

import java.nio.charset.StandardCharsets

import zio.test.{ Spec, TestEnvironment, ZIOSpecDefault, assertTrue }
import zio.{ Chunk, Ref, Scope, ZIO }

import io.github.riccardomerolla.ziogrpc.core.{ GrpcCodec, GrpcCodecError, GrpcErrorCodec, GrpcMetadata }
import io.grpc.{ MethodDescriptor, Status }

object GrpcServiceSpec extends ZIOSpecDefault:

  private val requestCodec: GrpcCodec[String] = new GrpcCodec[String]:
    override def encode(value: String): Either[GrpcCodecError, Array[Byte]] =
      Right(value.getBytes(StandardCharsets.UTF_8))

    override def decode(bytes: Array[Byte]): Either[GrpcCodecError, String] =
      Right(String(bytes, StandardCharsets.UTF_8))

  private val responseCodec: GrpcCodec[String] = new GrpcCodec[String]:
    override def encode(value: String): Either[GrpcCodecError, Array[Byte]] =
      Right(value.getBytes(StandardCharsets.UTF_8))

    override def decode(bytes: Array[Byte]): Either[GrpcCodecError, String] =
      Right(String(bytes, StandardCharsets.UTF_8))

  private val errorCodec: GrpcErrorCodec[Unit] =
    GrpcErrorCodec(_ => Status.UNKNOWN, _ => None)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("GrpcService")(
      test("empty creates a service with no endpoints") {
        val service = GrpcService.empty[Any]
        assertTrue(service.endpoints.isEmpty)
      },
      test("withMiddleware composes middleware on the service") {
        for
          ref    <- Ref.make(0)
          mw      = countingMiddleware(ref)
          handler = GrpcHandler.fromFunction[Any, Unit, String, String] { (_, input) =>
                      ZIO.succeed(input)
                    }
          service = GrpcService(Chunk(makeEndpoint("svc/one", handler))).withMiddleware(mw)
          wrapped = service.middleware(handler)
          _      <- wrapped.handle(GrpcMetadata.empty, "ping")
          count  <- ref.get
        yield assertTrue(count == 1)
      },
      test("++ applies each service middleware to its endpoints") {
        for
          ref1     <- Ref.make(0)
          ref2     <- Ref.make(0)
          mw1       = countingMiddleware(ref1)
          mw2       = countingMiddleware(ref2)
          handler   = GrpcHandler.fromFunction[Any, Unit, String, String] { (_, input) =>
                        ZIO.succeed(input)
                      }
          endpoint1 = makeEndpoint("svc/one", handler)
          endpoint2 = makeEndpoint("svc/two", handler)
          service1  = GrpcService(Chunk(endpoint1)).withMiddleware(mw1)
          service2  = GrpcService(Chunk(endpoint2)).withMiddleware(mw2)
          combined  = service1 ++ service2
          endpoints = combined.endpoints.map(
                        _.asInstanceOf[GrpcEndpoint[Any, Unit, String, String]]
                      )
          _        <- endpoints(0).handler.handle(GrpcMetadata.empty, "ping")
          _        <- endpoints(1).handler.handle(GrpcMetadata.empty, "pong")
          count1   <- ref1.get
          count2   <- ref2.get
        yield assertTrue(count1 == 1 && count2 == 1)
      },
    )

  private def countingMiddleware(ref: Ref[Int]): GrpcMiddleware[Any] =
    new GrpcMiddleware[Any]:
      override def apply[R1 <: Any, E, In, Out](
        handler: GrpcHandler[R1, E, In, Out]
      ): GrpcHandler[R1, E, In, Out] =
        new GrpcHandler[R1, E, In, Out]:
          override def handle(metadata: GrpcMetadata, input: In): ZIO[R1, E, Out] =
            ref.update(_ + 1) *> handler.handle(metadata, input)

  private def makeEndpoint(
    methodName: String,
    handler: GrpcHandler[Any, Unit, String, String],
  ): GrpcEndpoint[Any, Unit, String, String] =
    GrpcEndpoint(
      methodName = methodName,
      methodType = MethodDescriptor.MethodType.UNARY,
      requestCodec = requestCodec,
      responseCodec = responseCodec,
      handler = handler,
      errorCodec = errorCodec,
    )
