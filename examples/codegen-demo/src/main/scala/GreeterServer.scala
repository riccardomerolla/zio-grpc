package examples.codegen.demo

import helloworld.{Greeter, HelloReply, HelloRequest}
import io.github.riccardomerolla.ziogrpc.core.{GrpcCodec, GrpcErrorCodec}
import io.github.riccardomerolla.ziogrpc.server.{GrpcService, ServerConfig, ZioGrpc}
import io.grpc.MethodDescriptor
import io.grpc.Status
import zio.{Chunk, Scope, ZIO, ZIOAppDefault}
import java.nio.charset.StandardCharsets

// Simple codecs for the demo (UTF-8 JSON-less wire just for illustration)
given GrpcCodec[HelloRequest] with
  override def encode(value: HelloRequest) =
    Right(value.name.getBytes(StandardCharsets.UTF_8))
  override def decode(bytes: Array[Byte]) =
    Right(HelloRequest(String(bytes, StandardCharsets.UTF_8)))

given GrpcCodec[HelloReply] with
  override def encode(value: HelloReply) =
    Right(value.message.getBytes(StandardCharsets.UTF_8))
  override def decode(bytes: Array[Byte]) =
    Right(HelloReply(String(bytes, StandardCharsets.UTF_8)))

given GrpcErrorCodec[Nothing] =
  GrpcErrorCodec(_ => Status.OK, _ => None)

object GreeterServer extends ZIOAppDefault:
  private val handler = new Greeter[Nothing]:
    override def sayHello(request: HelloRequest) =
      ZIO.succeed(HelloReply(s"Hello, ${request.name}!"))

  private val service: GrpcService[Any] = Greeter.service(
    handler = handler,
    errorCodec = summon[GrpcErrorCodec[Nothing]],
    sayHelloRequestCodec = summon[GrpcCodec[HelloRequest]],
    sayHelloResponseCodec = summon[GrpcCodec[HelloReply]]
  )

  override def run: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      ZioGrpc
        .server(ServerConfig("0.0.0.0", 9000), Chunk(service))
        .flatMap(_.start *> ZIO.never)
    }
