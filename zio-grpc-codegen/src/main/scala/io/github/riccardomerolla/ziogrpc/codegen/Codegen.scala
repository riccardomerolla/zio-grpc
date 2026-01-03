package io.github.riccardomerolla.ziogrpc.codegen

import zio.{ Chunk, ZIO }

enum CodegenError:
  case InvalidProto(details: String)
  case OutputFailure(details: String)

final case class CodegenRequest(protoFiles: Chunk[String], outputDir: String)

trait Codegen:
  def generate(request: CodegenRequest): ZIO[Any, CodegenError, Unit]

object Codegen:
  val noop: Codegen = new Codegen:
    override def generate(request: CodegenRequest): ZIO[Any, CodegenError, Unit] =
      ZIO.unit
