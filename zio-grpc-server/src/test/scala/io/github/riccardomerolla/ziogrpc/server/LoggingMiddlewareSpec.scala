package io.github.riccardomerolla.ziogrpc.server

import zio.test.{ Spec, TestEnvironment, ZIOSpecDefault, assertTrue }
import zio.{ Chunk, LogLevel, Scope, ZIO }

import io.github.riccardomerolla.ziogrpc.core.{ GrpcMetadata, GrpcRequestContext }

object LoggingMiddlewareSpec extends ZIOSpecDefault:

  enum TestError:
    case BadInput(value: String)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("LoggingMiddleware")(
      test("preserves successful handler result") {
        val handler = GrpcHandler.fromFunction[Any, TestError, String, String] { (_, input) =>
          ZIO.succeed(s"ok: $input")
        }
        val wrapped = LoggingMiddleware.default(handler)
        val context = GrpcRequestContext(GrpcMetadata.empty, "test.Service/Method")

        GrpcRequestContext.withContext(context) {
          wrapped.handle(GrpcMetadata.empty, "hello").map { result =>
            assertTrue(result == "ok: hello")
          }
        }
      },
      test("preserves handler error") {
        val handler = GrpcHandler.fromFunction[Any, TestError, String, String] { (_, _) =>
          ZIO.fail(TestError.BadInput("bad"))
        }
        val wrapped = LoggingMiddleware.default(handler)
        val context = GrpcRequestContext(GrpcMetadata.empty, "test.Service/Method")

        GrpcRequestContext.withContext(context) {
          wrapped.handle(GrpcMetadata.empty, "bad").either.map { result =>
            assertTrue(result == Left(TestError.BadInput("bad")))
          }
        }
      },
      test("emits debug log for request start and info log for success") {
        val handler = GrpcHandler.fromFunction[Any, TestError, String, String] { (_, input) =>
          ZIO.succeed(s"ok: $input")
        }
        val wrapped = LoggingMiddleware.default(handler)
        val context = GrpcRequestContext(GrpcMetadata.empty, "test.Service/Method")

        GrpcRequestContext.withContext(context) {
          for
            _    <- wrapped.handle(GrpcMetadata.empty, "hello")
            logs <- zio.test.ZTestLogger.logOutput
          yield
            val debugLogs = logs.filter(_.logLevel == LogLevel.Debug)
            val infoLogs  = logs.filter(_.logLevel == LogLevel.Info)
            assertTrue(
              debugLogs.exists(_.message() == "gRPC request started"),
              infoLogs.exists(_.message() == "gRPC request completed"),
            )
        }
      },
      test("emits warning log on handler failure") {
        val handler = GrpcHandler.fromFunction[Any, TestError, String, String] { (_, _) =>
          ZIO.fail(TestError.BadInput("bad"))
        }
        val wrapped = LoggingMiddleware.default(handler)
        val context = GrpcRequestContext(GrpcMetadata.empty, "test.Service/Method")

        GrpcRequestContext.withContext(context) {
          for
            _    <- wrapped.handle(GrpcMetadata.empty, "bad").either
            logs <- zio.test.ZTestLogger.logOutput
          yield
            val warningLogs = logs.filter(_.logLevel == LogLevel.Warning)
            assertTrue(warningLogs.exists(_.message() == "gRPC request failed"))
        }
      },
      test("annotates logs with grpc.method from request context") {
        val handler = GrpcHandler.fromFunction[Any, TestError, String, String] { (_, input) =>
          ZIO.succeed(s"ok: $input")
        }
        val wrapped = LoggingMiddleware.default(handler)
        val context = GrpcRequestContext(GrpcMetadata.empty, "test.Service/Method")

        GrpcRequestContext.withContext(context) {
          for
            _    <- wrapped.handle(GrpcMetadata.empty, "hello")
            logs <- zio.test.ZTestLogger.logOutput
          yield
            val completionLog = logs.find(_.message() == "gRPC request completed")
            assertTrue(
              completionLog.exists(_.annotations.get("grpc.method").contains("test.Service/Method")),
              completionLog.exists(_.annotations.get("grpc.type").contains("unary")),
            )
        }
      },
      test("annotates failure logs with grpc.error") {
        val handler = GrpcHandler.fromFunction[Any, TestError, String, String] { (_, _) =>
          ZIO.fail(TestError.BadInput("bad"))
        }
        val wrapped = LoggingMiddleware.default(handler)
        val context = GrpcRequestContext(GrpcMetadata.empty, "test.Service/Method")

        GrpcRequestContext.withContext(context) {
          for
            _    <- wrapped.handle(GrpcMetadata.empty, "bad").either
            logs <- zio.test.ZTestLogger.logOutput
          yield
            val failureLog = logs.find(_.message() == "gRPC request failed")
            assertTrue(
              failureLog.exists(_.annotations.contains("grpc.error")),
              failureLog.exists(_.annotations.contains("grpc.duration_ms")),
            )
        }
      },
    )
