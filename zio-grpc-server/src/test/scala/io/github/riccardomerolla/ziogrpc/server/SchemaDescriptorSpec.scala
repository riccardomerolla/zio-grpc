package io.github.riccardomerolla.ziogrpc.server

import scala.jdk.CollectionConverters.*

import zio.Scope
import zio.test.{ Spec, TestEnvironment, ZIOSpecDefault, assertTrue }

import com.google.protobuf.Descriptors
import io.grpc.protobuf.{ ProtoFileDescriptorSupplier, ProtoServiceDescriptorSupplier }

object SchemaDescriptorSpec extends ZIOSpecDefault:

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("SchemaDescriptor")(
      test("getFileDescriptor returns the service file descriptor") {
        val serviceDescriptor = createMockServiceDescriptor()

        // Create SchemaDescriptor instance using reflection
        val schemaDescriptor = createSchemaDescriptor(serviceDescriptor)

        val result = schemaDescriptor.getFileDescriptor
        assertTrue(result == serviceDescriptor.getFile)
      },
      test("getServiceDescriptor returns the service descriptor") {
        val serviceDescriptor = createMockServiceDescriptor()

        // Create SchemaDescriptor instance using reflection
        val schemaDescriptor = createSchemaDescriptor(serviceDescriptor)

        val result = schemaDescriptor.getServiceDescriptor
        assertTrue(result == serviceDescriptor)
      },
      test("SchemaDescriptor implements ProtoFileDescriptorSupplier") {
        val serviceDescriptor = createMockServiceDescriptor()

        // Create SchemaDescriptor instance using reflection
        val schemaDescriptor = createSchemaDescriptor(serviceDescriptor)

        val isSupplier = schemaDescriptor.isInstanceOf[ProtoFileDescriptorSupplier]
        assertTrue(isSupplier)
      },
      test("SchemaDescriptor implements ProtoServiceDescriptorSupplier") {
        val serviceDescriptor = createMockServiceDescriptor()

        // Create SchemaDescriptor instance using reflection
        val schemaDescriptor = createSchemaDescriptor(serviceDescriptor)

        val isSupplier = schemaDescriptor.isInstanceOf[ProtoServiceDescriptorSupplier]
        assertTrue(isSupplier)
      },
    )

  private def createMockServiceDescriptor(): Descriptors.ServiceDescriptor = {
    val proto = com.google.protobuf.DescriptorProtos.ServiceDescriptorProto
      .newBuilder()
      .setName("TestService")
      .build()

    val fileProto = com.google.protobuf.DescriptorProtos.FileDescriptorProto
      .newBuilder()
      .setName("test.proto")
      .setPackage("test.package")
      .addService(proto)
      .build()

    val newFileDescriptor = Descriptors.FileDescriptor.buildFrom(
      fileProto,
      Array[Descriptors.FileDescriptor](),
    )

    newFileDescriptor.getServices.asScala.head
  }

  private def createSchemaDescriptor(
    serviceDescriptor: Descriptors.ServiceDescriptor
  ): ProtoFileDescriptorSupplier & ProtoServiceDescriptorSupplier = {
    // Use reflection to access the private SchemaDescriptor case class
    val grpcServerClass = Class.forName(
      "io.github.riccardomerolla.ziogrpc.server.GrpcServer$SchemaDescriptor"
    )

    val constructor = grpcServerClass.getDeclaredConstructor(
      classOf[Descriptors.ServiceDescriptor]
    )
    constructor.setAccessible(true)

    constructor.newInstance(serviceDescriptor)
      .asInstanceOf[ProtoFileDescriptorSupplier & ProtoServiceDescriptorSupplier]
  }
