package io.github.riccardomerolla.ziogrpc.server

import zio.{ Chunk, ZIO }
import zio.test.{ assertTrue, suite, test, ZIOSpecDefault }

import java.net.ServerSocket
import java.nio.charset.StandardCharsets

import io.github.riccardomerolla.ziogrpc.client.{
  ChannelConfig,
  ClientError,
  GrpcChannel,
  GrpcChannelLive,
  GrpcClient,
  GrpcClientCall,
}
import io.github.riccardomerolla.ziogrpc.core.{ GrpcCodec, GrpcErrorCodec, GrpcRequestContext }
import io.grpc.{ CallOptions, ClientInterceptors, Metadata, MethodDescriptor, Status }
import io.grpc.stub.{ ClientCalls, MetadataUtils }

enum TestError:
  case Invalid(value: String)
  case MissingContext

object GrpcServerSpec extends ZIOSpecDefault:
  private val methodName = "test.Greeter/SayHello"

  private val requestCodec: GrpcCodec[String] = new GrpcCodec[String]:
    override def encode(value: String) =
      Right(value.getBytes(StandardCharsets.UTF_8))

    override def decode(bytes: Array[Byte]) =
      Right(String(bytes, StandardCharsets.UTF_8))

  private val responseCodec: GrpcCodec[String] = new GrpcCodec[String]:
    override def encode(value: String) =
      Right(value.getBytes(StandardCharsets.UTF_8))

    override def decode(bytes: Array[Byte]) =
      Right(String(bytes, StandardCharsets.UTF_8))

  private val errorCodec: GrpcErrorCodec[TestError] =
    GrpcErrorCodec.derive(
      GrpcErrorCodec.mapping(
        { case TestError.Invalid(value) => Status.INVALID_ARGUMENT.withDescription(value) },
        status =>
          Option.when(status.getCode == Status.INVALID_ARGUMENT.getCode) {
            TestError.Invalid(Option(status.getDescription).getOrElse("unknown"))
          },
      ),
      GrpcErrorCodec.mapping(
        { case TestError.MissingContext => Status.FAILED_PRECONDITION },
        status =>
          Option.when(status.getCode == Status.FAILED_PRECONDITION.getCode) {
            TestError.MissingContext
          },
      ),
    )

  given GrpcErrorCodec[TestError] = errorCodec

  private val methodDescriptor: MethodDescriptor[String, String] =
    MethodDescriptor
      .newBuilder[String, String]()
      .setType(MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(methodName)
      .setRequestMarshaller(GrpcCodec.marshaller(requestCodec))
      .setResponseMarshaller(GrpcCodec.marshaller(responseCodec))
      .build()

  private val clientCall: GrpcClientCall[String, String] =
    GrpcClientCall.unary(methodDescriptor)

  override def spec =
    suite("GrpcServer")(
      test("unary call succeeds") {
        val handler = GrpcHandler.fromFunction[Any, TestError, String, String] { (_, request) =>
          ZIO.succeed(s"hello $request")
        }

        val endpoint = makeEndpoint(handler)

        withServer(endpoint) { channel =>
          val client = GrpcClient(channel)
          client.unary(clientCall, "zio").map { response =>
            assertTrue(response == "hello zio")
          }
        }
      },
      test("error maps to client remote error") {
        val handler = GrpcHandler.fromFunction[Any, TestError, String, String] { (_, request) =>
          if request == "bad" then ZIO.fail(TestError.Invalid(request))
          else ZIO.succeed(s"ok $request")
        }

        val endpoint = makeEndpoint(handler)

        withServer(endpoint) { channel =>
          val client = GrpcClient(channel)
          client.unary(clientCall, "bad").either.map { result =>
            assertTrue(result == Left(ClientError.Remote(TestError.Invalid("bad"))))
          }
        }
      },
      test("request context is available") {
        val handler = GrpcHandler.fromFunction[Any, TestError, String, String] { (_, _) =>
          GrpcRequestContext.get.flatMap {
            case Some(ctx) => ZIO.succeed(ctx.methodName)
            case None      => ZIO.fail(TestError.MissingContext)
          }
        }

        val endpoint = makeEndpoint(handler)

        withServer(endpoint) { channel =>
          val client = GrpcClient(channel)
          client.unary(clientCall, "zio").map { response =>
            assertTrue(response == methodName)
          }
        }
      },
      test("metadata is propagated to handler") {
        val handler = GrpcHandler.fromFunction[Any, TestError, String, String] { (metadata, _) =>
          ZIO.succeed(metadata.getHeader("x-test").getOrElse(""))
        }

        val endpoint = makeEndpoint(handler)

        withServer(endpoint) { baseChannel =>
          val md  = new Metadata()
          val key = Metadata.Key.of("x-test", Metadata.ASCII_STRING_MARSHALLER)
          md.put(key, "abc")

          val intercepted = ClientInterceptors.intercept(
            baseChannel.channel,
            MetadataUtils.newAttachHeadersInterceptor(md),
          )

          ZIO
            .attemptBlocking {
              ClientCalls.blockingUnaryCall(
                intercepted,
                methodDescriptor,
                CallOptions.DEFAULT,
                "ping",
              )
            }
            .map(response => assertTrue(response == "abc"))
        }
      },
      test("transport error surfaces when status cannot be decoded") {
        val handler = GrpcHandler.fromFunction[Any, TestError, String, String] { (_, _) =>
          ZIO.dieMessage("boom")
        }

        val endpoint = makeEndpoint(handler)

        withServer(endpoint) { channel =>
          val client = GrpcClient(channel)
          client.unary(clientCall, "ping").either.map { result =>
            val isInternal = result match
              case Left(ClientError.Transport(status)) =>
                status.getCode == Status.INTERNAL.getCode
              case _                                   => false
            assertTrue(isInternal)
          }
        }
      },
    )

  private def makeEndpoint(
      handler: GrpcHandler[Any, TestError, String, String]
    ): GrpcEndpoint[Any, TestError, String, String] =
    GrpcEndpoint(
      methodName = methodName,
      methodType = MethodDescriptor.MethodType.UNARY,
      requestCodec = requestCodec,
      responseCodec = responseCodec,
      handler = handler,
      errorCodec = errorCodec,
    )

  private def withServer[A](
      endpoint: GrpcEndpoint[Any, TestError, String, String]
    )(
      use: GrpcChannel => ZIO[Any, Any, A]
    ): ZIO[Any, Throwable, A] =
    ZIO.scoped {
      for
        port    <- freePort
        server  <- ZioGrpc
                     .server(
                       ServerConfig("127.0.0.1", port),
                       Chunk(GrpcService(Chunk(endpoint))),
                     )
                     .mapError(error => new RuntimeException(error.toString))
        _       <- server.start.mapError(error => new RuntimeException(error.toString))
        channel <- GrpcChannel
                     .scoped(ChannelConfig(s"127.0.0.1:$port"))
                     .mapError(error => new RuntimeException(error.toString))
        result  <- use(channel).mapError {
                     case throwable: Throwable => throwable
                     case other                => new RuntimeException(other.toString)
                   }
      yield result
    }

  private val freePort: ZIO[Any, Throwable, Int] =
    ZIO.attemptBlocking {
      val socket = new ServerSocket(0)
      try socket.getLocalPort
      finally socket.close()
    }
