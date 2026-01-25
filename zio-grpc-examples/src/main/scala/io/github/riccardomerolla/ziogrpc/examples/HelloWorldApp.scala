package io.github.riccardomerolla.ziogrpc.examples

import java.nio.charset.StandardCharsets

import zio.{ Chunk, Scope, ZIO, ZIOAppArgs, ZIOAppDefault }

import io.github.riccardomerolla.ziogrpc.core.{ GrpcCodec, GrpcCodecError, GrpcErrorCodec, GrpcMetadata }
import io.github.riccardomerolla.ziogrpc.server.{ GrpcEndpoint, GrpcHandler, GrpcService, ServerConfig, ZioGrpc }
import io.grpc.{ MethodDescriptor, Status }

enum HelloError:
  case InvalidName(value: String)

object HelloError:
  val codec: GrpcErrorCodec[HelloError] =
    GrpcErrorCodec(
      {
        case HelloError.InvalidName(value) =>
          Status.INVALID_ARGUMENT.withDescription(s"Invalid name: $value")
      },
      status =>
        Option.when(status.getCode == Status.INVALID_ARGUMENT.getCode) {
          val description = Option(status.getDescription).getOrElse("unknown")
          HelloError.InvalidName(description)
        },
    )

final case class HelloRequest(name: String)
final case class HelloReply(message: String)

object HelloWorldApp extends ZIOAppDefault:
  private val helloRequestCodec: GrpcCodec[HelloRequest] = new GrpcCodec[HelloRequest]:
    override def encode(value: HelloRequest): Either[GrpcCodecError, Array[Byte]] =
      Right(value.name.getBytes(StandardCharsets.UTF_8))

    override def decode(bytes: Array[Byte]): Either[GrpcCodecError, HelloRequest] =
      Right(HelloRequest(String(bytes, StandardCharsets.UTF_8)))

  private val helloReplyCodec: GrpcCodec[HelloReply] = new GrpcCodec[HelloReply]:
    override def encode(value: HelloReply): Either[GrpcCodecError, Array[Byte]] =
      Right(value.message.getBytes(StandardCharsets.UTF_8))

    override def decode(bytes: Array[Byte]): Either[GrpcCodecError, HelloReply] =
      Right(HelloReply(String(bytes, StandardCharsets.UTF_8)))

  private val helloHandler: GrpcHandler[Any, HelloError, HelloRequest, HelloReply] =
    GrpcHandler.fromFunction { (_: GrpcMetadata, request: HelloRequest) =>
      ZIO.ifZIO(ZIO.succeed(request.name.trim.isEmpty))(
        ZIO.fail(HelloError.InvalidName(request.name)),
        ZIO.succeed(HelloReply(s"Hello, ${request.name}!")),
      )
    }

  private val helloService: GrpcService[Any] =
    GrpcService(
      Chunk(
        GrpcEndpoint(
          methodName = "helloworld.Greeter/SayHello",
          methodType = MethodDescriptor.MethodType.UNARY,
          requestCodec = helloRequestCodec,
          responseCodec = helloReplyCodec,
          handler = helloHandler,
          errorCodec = HelloError.codec,
        )
      )
    )

  override def run: ZIO[Environment & (ZIOAppArgs & Scope), Any, Any] =
    ZIO
      .scoped {
        ZIO
          .acquireRelease(
            for
              _      <- ZIO.logInfo("Starting HelloWorld gRPC server on 0.0.0.0:9000")
              server <- ZioGrpc.server(ServerConfig("0.0.0.0", 9000), Chunk(helloService))
              _      <- server.start
              _      <- ZIO.logInfo("Server started. Press Ctrl+C to stop.")
            yield server
          )(server => server.shutdown *> ZIO.logInfo("Server stopped"))
          .flatMap(_ => ZIO.never)
      }
      .catchAllCause(_ => ZIO.unit)
      .as(zio.ExitCode.success)
