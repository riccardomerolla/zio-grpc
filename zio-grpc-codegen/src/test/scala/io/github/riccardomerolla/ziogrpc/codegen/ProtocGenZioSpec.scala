package io.github.riccardomerolla.ziogrpc.codegen

import zio.test.{ assertTrue, suite, test, ZIOSpecDefault }

import com.google.protobuf.DescriptorProtos.{ FileDescriptorProto, MethodDescriptorProto, ServiceDescriptorProto }
import com.google.protobuf.compiler.PluginProtos.{ CodeGeneratorRequest, CodeGeneratorResponse }
import scala.jdk.CollectionConverters._

object ProtocGenZioSpec extends ZIOSpecDefault:

  private def createTestProto(
      packageName: String,
      serviceName: String,
      methodName: String,
      inputType: String,
      outputType: String,
    ): FileDescriptorProto =
    val method = MethodDescriptorProto
      .newBuilder()
      .setName(methodName)
      .setInputType(inputType)
      .setOutputType(outputType)
      .setClientStreaming(false)
      .setServerStreaming(false)
      .build()

    val service = ServiceDescriptorProto
      .newBuilder()
      .setName(serviceName)
      .addMethod(method)
      .build()

    FileDescriptorProto
      .newBuilder()
      .setName("test.proto")
      .setPackage(packageName)
      .addService(service)
      .build()

  private def createRequest(files: FileDescriptorProto*): CodeGeneratorRequest =
    CodeGeneratorRequest
      .newBuilder()
      .addAllProtoFile(files.asJava)
      .build()

  override def spec =
    suite("ProtocGenZio")(
      test("generates service trait for unary RPC") {
        val proto = createTestProto(
          packageName = "example",
          serviceName = "TestService",
          methodName = "DoSomething",
          inputType = "Request",
          outputType = "Response",
        )

        val request  = createRequest(proto)
        val response = ProtocGenZio.generate(request)

        assertTrue(
          !response.hasError,
          response.getFileCount == 2, // descriptor + service file
        )
      },
      test("generated service file contains typed error channel") {
        val proto = createTestProto(
          packageName = "example",
          serviceName = "MyService",
          methodName = "Execute",
          inputType = "Input",
          outputType = "Output",
        )

        val request  = createRequest(proto)
        val response = ProtocGenZio.generate(request)

        val serviceFile = response.getFileList.asScala.find(_.getName.endsWith("ZioGrpc.scala"))

        assertTrue(
          serviceFile.isDefined,
          serviceFile.exists(_.getContent.contains("trait MyService[E]")),
          serviceFile.exists(_.getContent.contains("ZIO[Any, E,")),
        )
      },
      test("generated companion object has service factory") {
        val proto = createTestProto(
          packageName = "example",
          serviceName = "Calculator",
          methodName = "Add",
          inputType = "Numbers",
          outputType = "Result",
        )

        val request  = createRequest(proto)
        val response = ProtocGenZio.generate(request)

        val serviceFile = response.getFileList.asScala.find(_.getName.endsWith("ZioGrpc.scala"))

        assertTrue(
          serviceFile.isDefined,
          serviceFile.exists(_.getContent.contains("object Calculator")),
          serviceFile.exists(_.getContent.contains("def service[E]")),
          serviceFile.exists(_.getContent.contains("GrpcService")),
        )
      },
      test("generates descriptor file with base64 encoded proto") {
        val proto = createTestProto(
          packageName = "example",
          serviceName = "Service",
          methodName = "Method",
          inputType = "In",
          outputType = "Out",
        )

        val request  = createRequest(proto)
        val response = ProtocGenZio.generate(request)

        val descriptorFile = response.getFileList.asScala.find(_.getName.endsWith("Descriptors.scala"))

        assertTrue(
          descriptorFile.isDefined,
          descriptorFile.exists(_.getContent.contains("object TestDescriptors")),
          descriptorFile.exists(_.getContent.contains("private val encoded: String")),
          descriptorFile.exists(_.getContent.contains("lazy val fileDescriptor")),
        )
      },
      test("rejects streaming RPCs in MVP") {
        val method = MethodDescriptorProto
          .newBuilder()
          .setName("StreamData")
          .setInputType("Input")
          .setOutputType("Output")
          .setClientStreaming(true)
          .setServerStreaming(false)
          .build()

        val service = ServiceDescriptorProto
          .newBuilder()
          .setName("StreamingService")
          .addMethod(method)
          .build()

        val proto = FileDescriptorProto
          .newBuilder()
          .setName("streaming.proto")
          .setPackage("example")
          .addService(service)
          .build()

        val request  = createRequest(proto)
        val response = ProtocGenZio.generate(request)

        assertTrue(
          response.hasError,
          response.getError.contains("Streaming RPCs are not supported"),
        )
      },
      test("generates correct method descriptor type") {
        val proto = createTestProto(
          packageName = "test",
          serviceName = "Svc",
          methodName = "Call",
          inputType = "Req",
          outputType = "Res",
        )

        val request  = createRequest(proto)
        val response = ProtocGenZio.generate(request)

        val serviceFile = response.getFileList.asScala.find(_.getName.endsWith("ZioGrpc.scala"))

        assertTrue(
          serviceFile.exists(_.getContent.contains("MethodDescriptor.MethodType.UNARY"))
        )
      },
    )
