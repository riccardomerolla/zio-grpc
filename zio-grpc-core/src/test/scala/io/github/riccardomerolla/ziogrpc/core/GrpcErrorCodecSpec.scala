package io.github.riccardomerolla.ziogrpc.core

import zio.test.{ assertTrue, suite, test, ZIOSpecDefault }

import io.grpc.Status

enum TestError:
  case NotFound(id: String)
  case Unauthorized

object GrpcErrorCodecSpec extends ZIOSpecDefault:
  private val codec: GrpcErrorCodec[TestError] =
    GrpcErrorCodec.derive(
      GrpcErrorCodec.mapping(
        { case TestError.NotFound(_) => Status.NOT_FOUND },
        status =>
          Option.when(status.getCode == Status.NOT_FOUND.getCode) {
            TestError.NotFound(status.getDescription)
          },
      ),
      GrpcErrorCodec.mapping(
        { case TestError.Unauthorized => Status.PERMISSION_DENIED },
        status =>
          Option.when(status.getCode == Status.PERMISSION_DENIED.getCode) {
            TestError.Unauthorized
          },
      ),
    )

  override def spec =
    suite("GrpcErrorCodec")(
      test("maps errors to statuses") {
        val status = codec.toStatus(TestError.NotFound("42"))
        assertTrue(status.getCode == Status.NOT_FOUND.getCode)
      },
      test("maps statuses back to errors") {
        val status = Status.PERMISSION_DENIED
        val error  = codec.fromStatus(status)
        assertTrue(error.contains(TestError.Unauthorized))
      },
      test("interop converts to and from StatusException") {
        val exception = GrpcStatusInterop.toStatusException(TestError.Unauthorized)(using codec)
        val roundTrip = GrpcStatusInterop.fromStatusException(exception)(using codec)
        assertTrue(roundTrip.contains(TestError.Unauthorized))
      },
    )
