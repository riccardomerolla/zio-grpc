package io.github.riccardomerolla.ziogrpc.server

trait GrpcMiddleware[-R]:
  def apply[R1 <: R, E, In, Out](
      handler: GrpcHandler[R1, E, In, Out]
    ): GrpcHandler[R1, E, In, Out]

object GrpcMiddleware:
  val identity: GrpcMiddleware[Any] = new GrpcMiddleware[Any]:
    override def apply[R1 <: Any, E, In, Out](
        handler: GrpcHandler[R1, E, In, Out]
      ): GrpcHandler[R1, E, In, Out] =
      handler

  extension [R](self: GrpcMiddleware[R])
    def ++(other: GrpcMiddleware[R]): GrpcMiddleware[R] =
      new GrpcMiddleware[R]:
        override def apply[R1 <: R, E, In, Out](
            handler: GrpcHandler[R1, E, In, Out]
          ): GrpcHandler[R1, E, In, Out] =
          other(self(handler))
