package io.github.riccardomerolla.ziogrpc.server

import zio.{ Chunk, Exit, Runtime, Scope, UIO, Unsafe, ZIO }

import java.net.InetSocketAddress

import io.github.riccardomerolla.ziogrpc.core.{ GrpcCodec, GrpcMetadata, GrpcRequestContext, GrpcStatusInterop }
import io.grpc.{ MethodDescriptor, Server, ServerServiceDefinition, Status }
import io.grpc.netty.NettyServerBuilder
import io.grpc.stub.ServerCalls

enum ServerError:
  case StartupFailure(details: String)
  case ShutdownFailure(details: String)

final case class ServerConfig(host: String, port: Int)

trait GrpcServer:
  def start: ZIO[Any, ServerError, Unit]
  def shutdown: UIO[Unit]

final case class GrpcServerLive(server: Server) extends GrpcServer:
  override def start: ZIO[Any, ServerError, Unit] =
    ZIO
      .attempt(server.start())
      .mapError(error => ServerError.StartupFailure(error.getMessage))
      .unit

  override def shutdown: UIO[Unit] =
    ZIO.attempt(server.shutdown()).unit.orDie

object GrpcServer:
  def scoped[R](
      config: ServerConfig,
      services: Chunk[GrpcService[R]],
    ): ZIO[R & Scope, ServerError, GrpcServer] =
    for
      runtime <- ZIO.runtime[R]
      server  <- ZIO.acquireRelease(
                   ZIO
                     .attempt(buildServer(config, services, runtime))
                     .mapError(error => ServerError.StartupFailure(error.getMessage))
                 )(server => ZIO.attempt(server.shutdown()).unit.orDie)
    yield GrpcServerLive(server)

  private def buildServer[R](
      config: ServerConfig,
      services: Chunk[GrpcService[R]],
      runtime: Runtime[R],
    ): Server =
    val builder = NettyServerBuilder.forAddress(
      new InetSocketAddress(config.host, config.port)
    )

    val serviceDefinitions = buildServiceDefinitions(services, runtime)
    serviceDefinitions.foldLeft(builder)((acc, service) => acc.addService(service)).build()

  private def buildServiceDefinitions[R](
      services: Chunk[GrpcService[R]],
      runtime: Runtime[R],
    ): Chunk[ServerServiceDefinition] =
    val endpoints = services.flatMap(_.endpoints)
    val grouped   = endpoints.groupBy(endpoint => serviceName(endpoint.methodName))

    Chunk.fromIterable(
      grouped.map {
        case (service, serviceEndpoints) =>
          serviceEndpoints
            .foldLeft(ServerServiceDefinition.builder(service)) { (builder, endpoint) =>
              builder.addMethod(buildMethodDescriptor(endpoint), buildHandler(endpoint, runtime))
            }
            .build()
      }
    )

  private def buildMethodDescriptor[In, Out](endpoint: GrpcEndpoint[?, ?, In, Out]) =
    MethodDescriptor
      .newBuilder[In, Out]()
      .setType(endpoint.methodType)
      .setFullMethodName(endpoint.methodName)
      .setRequestMarshaller(GrpcCodec.marshaller(endpoint.requestCodec))
      .setResponseMarshaller(GrpcCodec.marshaller(endpoint.responseCodec))
      .build()

  private def buildHandler[R, E, In, Out](
      endpoint: GrpcEndpoint[R, E, In, Out],
      runtime: Runtime[R],
    ) =
    if endpoint.methodType != MethodDescriptor.MethodType.UNARY then
      throw new IllegalArgumentException(
        s"Unsupported method type: ${endpoint.methodType}"
      )

    ServerCalls.asyncUnaryCall(
      new ServerCalls.UnaryMethod[In, Out]:
        override def invoke(request: In, observer: io.grpc.stub.StreamObserver[Out]): Unit =
          val metadata = GrpcMetadata.empty
          val context  = GrpcRequestContext(metadata, endpoint.methodName)
          val effect   = GrpcRequestContext
            .withContext(context) {
              endpoint.handler.handle(metadata, request)
            }
            .mapError(error => GrpcStatusInterop.toStatusException(error)(using endpoint.errorCodec))

          Unsafe.unsafe { implicit unsafe =>
            runtime.unsafe.run(effect) match
              case Exit.Success(value) =>
                observer.onNext(value)
                observer.onCompleted()
              case Exit.Failure(cause) =>
                cause.failureOption match
                  case Some(statusException) => observer.onError(statusException)
                  case None                  =>
                    observer.onError(
                      Status
                        .INTERNAL
                        .withDescription("Unhandled defect")
                        .withCause(cause.squash)
                        .asException()
                    )
          }
    )

  private def serviceName(methodName: String): String =
    Option(MethodDescriptor.extractFullServiceName(methodName)).getOrElse(methodName)
