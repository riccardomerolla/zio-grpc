package io.github.riccardomerolla.ziogrpc.core

import io.grpc.Metadata

final case class GrpcMetadata(headers: Map[String, String]):
  def getHeader(name: String): Option[String] =
    headers.get(GrpcMetadata.normalize(name))

  def withHeader(name: String, value: String): GrpcMetadata =
    copy(headers = headers.updated(GrpcMetadata.normalize(name), value))

  def removeHeader(name: String): GrpcMetadata =
    copy(headers = headers.removed(GrpcMetadata.normalize(name)))

object GrpcMetadata:
  val empty: GrpcMetadata = GrpcMetadata(Map.empty)

  def fromGrpc(metadata: Metadata): GrpcMetadata =
    val headers = metadata
      .keys()
      .toArray(new Array[String](metadata.keys().size()))
      .toList
      .filterNot(_.endsWith("-bin"))
      .flatMap { key =>
        val grpcKey = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)
        Option(metadata.get(grpcKey)).map(value => normalize(key) -> value)
      }
      .toMap
    GrpcMetadata(headers)

  private def normalize(name: String): String =
    name.trim.toLowerCase
