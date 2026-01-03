package io.github.riccardomerolla.ziogrpc.core

import io.grpc.Status

trait GrpcErrorCodec[E]:
  def toStatus(error: E): Status
  def fromStatus(status: Status): Option[E]

object GrpcErrorCodec:
  final case class ErrorMapping[E](
      toStatus: PartialFunction[E, Status],
      fromStatus: Status => Option[E],
    )

  def apply[E](
      to: E => Status,
      from: Status => Option[E],
    ): GrpcErrorCodec[E] =
    new GrpcErrorCodec[E]:
      override def toStatus(error: E): Status            = to(error)
      override def fromStatus(status: Status): Option[E] = from(status)

  def mapping[E](
      toStatus: PartialFunction[E, Status],
      fromStatus: Status => Option[E],
    ): ErrorMapping[E] =
    ErrorMapping(toStatus, fromStatus)

  def derive[E](
      mappings: ErrorMapping[E]*
    ): GrpcErrorCodec[E] =
    val combinedToStatus = mappings
      .map(_.toStatus)
      .reduceLeftOption(_ orElse _)
      .getOrElse(PartialFunction.empty)

    val combinedFromStatus: Status => Option[E] =
      status => mappings.iterator.flatMap(_.fromStatus(status)).toSeq.headOption

    apply(
      error =>
        if combinedToStatus.isDefinedAt(error) then combinedToStatus(error)
        else Status.UNKNOWN.withDescription(s"Unhandled error: ${error.toString}"),
      combinedFromStatus,
    )
