package io.github.riccardomerolla.ziogrpc.core

import zio.Chunk
import zio.stream.ZStream

trait GrpcStreamCodec[A]:
  def encode(
      stream: ZStream[Any, GrpcCodecError, A]
    ): ZStream[Any, GrpcCodecError, Chunk[Byte]]

  def decode(
      stream: ZStream[Any, GrpcCodecError, Chunk[Byte]]
    ): ZStream[Any, GrpcCodecError, A]
