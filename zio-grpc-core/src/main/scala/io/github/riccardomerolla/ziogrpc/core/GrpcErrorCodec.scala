package io.github.riccardomerolla.ziogrpc.core

import io.grpc.Status

trait GrpcErrorCodec[E]:
  def toStatus(error: E): Status
  def fromStatus(status: Status): Option[E]

object GrpcErrorCodec:
  def apply[E](
      to: E => Status,
      from: Status => Option[E],
    ): GrpcErrorCodec[E] =
    new GrpcErrorCodec[E]:
      override def toStatus(error: E): Status            = to(error)
      override def fromStatus(status: Status): Option[E] = from(status)
