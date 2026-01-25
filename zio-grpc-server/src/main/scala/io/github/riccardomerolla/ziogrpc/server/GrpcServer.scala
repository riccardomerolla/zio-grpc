package io.github.riccardomerolla.ziogrpc.server

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

import zio.*

import io.github.riccardomerolla.ziogrpc.core.{ GrpcCodec, GrpcMetadata, GrpcRequestContext, GrpcStatusInterop }
import io.grpc.*
import io.grpc.netty.NettyServerBuilder
import io.grpc.protobuf.ProtoFileDescriptorSupplier
import io.grpc.protobuf.services.ProtoReflectionService

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
    val withReflection     = builder.addService(ProtoReflectionService.newInstance())
    serviceDefinitions.foldLeft(withReflection)((acc, service) => acc.addService(service)).build()

  private def buildServiceDefinitions[R](
    services: Chunk[GrpcService[R]],
    runtime: Runtime[R],
  ): Chunk[ServerServiceDefinition] =
    val endpoints            = services.flatMap(_.endpoints)
    val descriptorsByService = services.flatMap(svc => svc.descriptor.map(d => serviceName(d.getFullName) -> d)).toMap
    val grouped              = endpoints.groupBy(endpoint => serviceName(endpoint.methodName))

    Chunk.fromIterable(
      grouped.map {
        case (service, serviceEndpoints) =>
          val schemaDescriptor      = descriptorsByService.get(service).map(SchemaDescriptor.apply)
          val grpcServiceDescriptor = schemaDescriptor.map { schema =>
            val builder = ServiceDescriptor.newBuilder(service).setSchemaDescriptor(schema)
            val methods = serviceEndpoints.map(ep => buildMethodDescriptor(ep))
            methods.foldLeft(builder)((b, md) => b.addMethod(md)).build()
          }

          val serverServiceBuilder = grpcServiceDescriptor match
            case Some(descriptor) => ServerServiceDefinition.builder(descriptor)
            case None             => ServerServiceDefinition.builder(service)

          serviceEndpoints
            .foldLeft(serverServiceBuilder) { (builder, endpoint) =>
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
  ): ServerCallHandler[In, Out] =
    if endpoint.methodType != MethodDescriptor.MethodType.UNARY then
      throw new IllegalArgumentException(
        s"Unsupported method type: ${endpoint.methodType}"
      ) // scalafix:ok DisableSyntax.throw

    new ServerCallHandler[In, Out]:
      override def startCall(
        call: ServerCall[In, Out],
        headers: Metadata,
      ): ServerCall.Listener[In] =
        given ExecutionContext = ExecutionContext.global

        new ServerCall.Listener[In]:
          private val requestRef: AtomicReference[Option[In]] = new AtomicReference(None)

          override def onMessage(message: In): Unit =
            requestRef.set(Some(message))

          override def onHalfClose(): Unit =
            requestRef.get() match
              case None      =>
                call.close(
                  Status.INTERNAL.withDescription("No request message received"),
                  new Metadata(),
                )
              case Some(req) =>
                val metadata                                     = GrpcMetadata.fromGrpc(headers)
                val context                                      = GrpcRequestContext(metadata, endpoint.methodName)
                val effect: ZIO[R, io.grpc.StatusException, Out] =
                  GrpcRequestContext
                    .withContext(context) {
                      endpoint.handler.handle(metadata, req)
                    }
                    .mapError(error => GrpcStatusInterop.toStatusException(error)(using endpoint.errorCodec))

                Unsafe.unsafe { implicit unsafe =>
                  val future: scala.concurrent.Future[Out] =
                    runtime.unsafe.runToFuture(effect)

                  future.onComplete {
                    case Success(value)                                    =>
                      call.sendHeaders(new Metadata())
                      call.sendMessage(value)
                      call.close(Status.OK, new Metadata())
                    case Failure(statusException: io.grpc.StatusException) =>
                      call.close(statusException.getStatus, new Metadata())
                    case Failure(throwable)                                =>
                      call.close(
                        Status
                          .INTERNAL
                          .withDescription("Unhandled execution failure")
                          .withCause(throwable),
                        new Metadata(),
                      )
                  }
                }

          override def onReady(): Unit =
            call.request(1)

  private def serviceName(methodName: String): String =
    Option(MethodDescriptor.extractFullServiceName(methodName)).getOrElse(methodName)

  final private case class SchemaDescriptor(
    serviceDescriptor: com.google.protobuf.Descriptors.ServiceDescriptor
  ) extends ProtoFileDescriptorSupplier,
      io.grpc.protobuf.ProtoServiceDescriptorSupplier:
    override def getFileDescriptor: com.google.protobuf.Descriptors.FileDescriptor =
      serviceDescriptor.getFile

    override def getServiceDescriptor: com.google.protobuf.Descriptors.ServiceDescriptor =
      serviceDescriptor
