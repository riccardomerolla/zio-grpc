package io.github.riccardomerolla.ziogrpc.core

import io.grpc.{ Status, StatusException }

object GrpcStatusInterop:
  def toStatusException[E](error: E)(using codec: GrpcErrorCodec[E]): StatusException =
    new StatusException(codec.toStatus(error))

  def toStatus[E](error: E)(using codec: GrpcErrorCodec[E]): Status =
    codec.toStatus(error)

  def fromStatusException[E](
    exception: StatusException
  )(using codec: GrpcErrorCodec[E]
  ): Option[E] =
    codec.fromStatus(exception.getStatus)

  def fromStatus[E](status: Status)(using codec: GrpcErrorCodec[E]): Option[E] =
    codec.fromStatus(status)
