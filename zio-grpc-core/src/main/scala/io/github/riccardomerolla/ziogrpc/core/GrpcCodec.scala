package io.github.riccardomerolla.ziogrpc.core

import io.grpc.MethodDescriptor

enum GrpcCodecError:
  case DecodeFailure(details: String)
  case EncodeFailure(details: String)

trait GrpcCodec[A]:
  def encode(value: A): Either[GrpcCodecError, Array[Byte]]
  def decode(bytes: Array[Byte]): Either[GrpcCodecError, A]

object GrpcCodec:
  def marshaller[A](codec: GrpcCodec[A]): MethodDescriptor.Marshaller[A] =
    new MethodDescriptor.Marshaller[A]:
      override def stream(value: A) =
        val bytes = codec.encode(value) match
          case Left(error)    =>
            throw new IllegalArgumentException(error.toString)
          case Right(encoded) =>
            encoded
        new java.io.ByteArrayInputStream(bytes)

      override def parse(stream: java.io.InputStream): A =
        val bytes = stream.readAllBytes()
        codec.decode(bytes) match
          case Left(error)  => throw new IllegalArgumentException(error.toString)
          case Right(value) => value
