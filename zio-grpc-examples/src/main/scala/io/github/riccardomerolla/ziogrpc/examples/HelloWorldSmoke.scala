package io.github.riccardomerolla.ziogrpc.examples

import java.nio.charset.StandardCharsets

import zio.{ Chunk, Scope, ZIO, ZIOAppDefault }

import io.github.riccardomerolla.ziogrpc.client.{ ChannelConfig, ClientError, GrpcChannel, GrpcClient, GrpcClientCall }
import io.github.riccardomerolla.ziogrpc.core.{ GrpcCodec, GrpcCodecError, GrpcErrorCodec, GrpcMetadata }
import io.github.riccardomerolla.ziogrpc.server.{ GrpcEndpoint, GrpcHandler, GrpcService, ServerConfig, ZioGrpc }
import io.grpc.MethodDescriptor

object HelloWorldSmoke extends ZIOAppDefault:
  private given GrpcErrorCodec[HelloError]               = HelloError.codec
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
      if request.name.trim.isEmpty then ZIO.fail(HelloError.InvalidName(request.name))
      else ZIO.succeed(HelloReply(s"Hello, ${request.name}!"))
    }

  private val helloEndpoint: GrpcEndpoint[Any, HelloError, HelloRequest, HelloReply] =
    GrpcEndpoint(
      methodName = "helloworld.Greeter/SayHello",
      methodType = MethodDescriptor.MethodType.UNARY,
      requestCodec = helloRequestCodec,
      responseCodec = helloReplyCodec,
      handler = helloHandler,
      errorCodec = summon[GrpcErrorCodec[HelloError]],
    )

  private val helloMethod: MethodDescriptor[HelloRequest, HelloReply] =
    MethodDescriptor
      .newBuilder[HelloRequest, HelloReply]()
      .setType(MethodDescriptor.MethodType.UNARY)
      .setFullMethodName("helloworld.Greeter/SayHello")
      .setRequestMarshaller(GrpcCodec.marshaller(helloRequestCodec))
      .setResponseMarshaller(GrpcCodec.marshaller(helloReplyCodec))
      .build()

  private val helloCall: GrpcClientCall[HelloRequest, HelloReply] =
    GrpcClientCall.unary(helloMethod)

  override def run: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for
        port    <- randomPort
        _       <- ZIO.logInfo(s"Starting HelloWorld server on $port")
        server  <- ZioGrpc
                     .server(ServerConfig("127.0.0.1", port), Chunk(GrpcService(Chunk(helloEndpoint))))
                     .mapError(error => new RuntimeException(error.toString))
        _       <- server.start.mapError(error => new RuntimeException(error.toString))
        channel <- GrpcChannel
                     .scoped(ChannelConfig(s"127.0.0.1:$port"))
                     .mapError(error => new RuntimeException(error.toString))
        client   = GrpcClient(channel)
        reply   <- client.unary(helloCall, HelloRequest("ZIO")).mapError {
                     case ClientError.Remote(err)     => new RuntimeException(err.toString)
                     case ClientError.Transport(code) => new RuntimeException(code.toString)
                     case ClientError.CallFailure(d)  => new RuntimeException(d)
                   }
        _       <- ZIO.logInfo(s"Response: ${reply.message}")
      yield ()
    }

  private val randomPort: ZIO[Scope, Throwable, Int] =
    ZIO
      .acquireRelease(
        ZIO.attempt(new java.net.ServerSocket(0))
      )(socket => ZIO.attempt(socket.close()).orDie)
      .map(_.getLocalPort)
