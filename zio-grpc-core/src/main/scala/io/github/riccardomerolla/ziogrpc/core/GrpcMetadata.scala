package io.github.riccardomerolla.ziogrpc.core

final case class GrpcMetadata(headers: Map[String, String]):
  def getHeader(name: String): Option[String] =
    headers.get(GrpcMetadata.normalize(name))

  def withHeader(name: String, value: String): GrpcMetadata =
    copy(headers = headers.updated(GrpcMetadata.normalize(name), value))

  def removeHeader(name: String): GrpcMetadata =
    copy(headers = headers.removed(GrpcMetadata.normalize(name)))

object GrpcMetadata:
  val empty: GrpcMetadata = GrpcMetadata(Map.empty)

  private def normalize(name: String): String =
    name.trim.toLowerCase
