package io.github.riccardomerolla.ziogrpc.codegen

import com.google.protobuf.DescriptorProtos.{ FileDescriptorProto, MethodDescriptorProto, ServiceDescriptorProto }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import scala.jdk.CollectionConverters._

object ProtocGenZio:
  def main(args: Array[String]): Unit =
    val request  = CodeGeneratorRequest.parseFrom(System.in.readAllBytes())
    val response = generate(request)
    response.writeTo(System.out)

  private def generate(request: CodeGeneratorRequest): CodeGeneratorResponse =
    val files       = request.getProtoFileList.asScala.toList
    val unsupported = files.exists(file => file.getServiceList.asScala.exists(hasStreamingMethod))

    if unsupported then
      CodeGeneratorResponse
        .newBuilder()
        .setError("Streaming RPCs are not supported by the MVP code generator")
        .build()
    else
      val generated = files.flatMap(generateFile)
      CodeGeneratorResponse.newBuilder().addAllFile(generated.asJava).build()

  private def generateFile(file: FileDescriptorProto): List[CodeGeneratorResponse.File] =
    val pkg = file.getPackage
    file.getServiceList.asScala.toList.map { service =>
      val content  = renderService(pkg, service)
      val fileName = filePath(pkg, s"${service.getName}ZioGrpc.scala")
      CodeGeneratorResponse.File.newBuilder().setName(fileName).setContent(content).build()
    }

  private def renderService(pkg: String, service: ServiceDescriptorProto): String =
    val methods = service.getMethodList.asScala.toList
    val builder = new StringBuilder

    if pkg.nonEmpty then builder.append(s"package $pkg\n\n")

    builder.append("import zio.ZIO\n")
    builder.append("import zio.Chunk\n")
    builder.append("import io.grpc.MethodDescriptor\n")
    builder.append(
      "import io.github.riccardomerolla.ziogrpc.core.{GrpcCodec, GrpcErrorCodec}\n"
    )
    builder.append(
      "import io.github.riccardomerolla.ziogrpc.server.{GrpcEndpoint, GrpcHandler, GrpcService}\n\n"
    )

    val serviceName = service.getName

    builder.append(s"trait ${serviceName}[E]:\n")
    methods.foreach { method =>
      val methodName = lowerFirst(method.getName)
      val inputType  = scalaType(method.getInputType)
      val outputType = scalaType(method.getOutputType)
      builder.append(s"  def $methodName(request: $inputType): ZIO[Any, E, $outputType]\n")
    }

    builder.append("\n")
    builder.append(s"object ${serviceName}:\n")
    builder.append(s"  def service[E](\n")
    builder.append(s"    handler: ${serviceName}[E],\n")
    builder.append(s"    errorCodec: GrpcErrorCodec[E],\n")

    methods.foreach { method =>
      val methodName = lowerFirst(method.getName)
      val inputType  = scalaType(method.getInputType)
      val outputType = scalaType(method.getOutputType)
      builder.append(s"    ${methodName}RequestCodec: GrpcCodec[$inputType],\n")
      builder.append(s"    ${methodName}ResponseCodec: GrpcCodec[$outputType],\n")
    }

    builder.append(s"  ): GrpcService[Any] =\n")
    builder.append(s"    GrpcService(\n")
    builder.append(s"      Chunk(\n")

    methods.foreach { method =>
      val methodName  = lowerFirst(method.getName)
      val inputType   = scalaType(method.getInputType)
      val outputType  = scalaType(method.getOutputType)
      val fullService =
        if pkg.nonEmpty then s"$pkg.${serviceName}" else serviceName
      val fullMethod  = s"$fullService/${method.getName}"

      builder.append("        GrpcEndpoint(\n")
      builder.append(s"          methodName = \"$fullMethod\",\n")
      builder.append("          methodType = MethodDescriptor.MethodType.UNARY,\n")
      builder.append(s"          requestCodec = ${methodName}RequestCodec,\n")
      builder.append(s"          responseCodec = ${methodName}ResponseCodec,\n")
      builder.append("          handler = GrpcHandler.fromFunction { (_, request) =>\n")
      builder.append(s"            handler.${methodName}(request)\n")
      builder.append("          },\n")
      builder.append("          errorCodec = errorCodec\n")
      builder.append("        ),\n")
    }

    builder.append("      )\n")
    builder.append("    )\n")

    builder.toString()

  private def hasStreamingMethod(service: ServiceDescriptorProto): Boolean =
    service.getMethodList.asScala.exists(method => method.getClientStreaming || method.getServerStreaming)

  private def filePath(pkg: String, fileName: String): String =
    if pkg.isEmpty then fileName else s"${pkg.replace('.', '/')}/$fileName"

  private def scalaType(protoType: String): String =
    protoType.split('.').lastOption.getOrElse(protoType)

  private def lowerFirst(value: String): String =
    if value.isEmpty then value else value.head.toLower + value.tail
