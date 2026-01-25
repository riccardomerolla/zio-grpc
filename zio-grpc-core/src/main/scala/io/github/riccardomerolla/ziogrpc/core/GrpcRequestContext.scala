package io.github.riccardomerolla.ziogrpc.core

import zio.{ FiberRef, UIO, Unsafe, ZIO }

final case class GrpcRequestContext(
  metadata: GrpcMetadata,
  methodName: String,
)

object GrpcRequestContext:
  private val fiberRef: FiberRef[Option[GrpcRequestContext]] =
    Unsafe.unsafe { implicit unsafe =>
      FiberRef.unsafe.make(None)
    }

  def get: UIO[Option[GrpcRequestContext]] =
    fiberRef.get

  def set(ctx: GrpcRequestContext): UIO[Unit] =
    fiberRef.set(Some(ctx))

  def withContext[R, E, A](ctx: GrpcRequestContext)(effect: ZIO[R, E, A]): ZIO[R, E, A] =
    fiberRef.locally(Some(ctx))(effect)
