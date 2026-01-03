package io.github.riccardomerolla.ziogrpc.core

enum GrpcCodecError:
  case DecodeFailure(details: String)
  case EncodeFailure(details: String)

trait GrpcCodec[A]:
  def encode(value: A): Either[GrpcCodecError, Array[Byte]]
  def decode(bytes: Array[Byte]): Either[GrpcCodecError, A]
