# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ZIO-gRPC is a type-safe gRPC framework for Scala 3 and ZIO 2.x. The project follows strict Effect-Oriented Programming principles and provides a foundation for building composable, resource-safe gRPC services.

This is a **clean-room alternative** to `scalapb/zio-grpc` with typed errors as a hard requirement, ZLayer-first dependency injection, and transport-edge error mapping. See `docs/BLUEPRINT.md` for the complete design philosophy, architectural decisions, and execution roadmap.

## Module Architecture

The codebase is organized as a multi-module sbt project:

- **zio-grpc-core**: Protocol codecs (`GrpcCodec`, `GrpcErrorCodec`), shared types (`GrpcMetadata`, `GrpcRequestContext`), and status interop. This is the foundation for encoding/decoding messages and mapping typed errors to gRPC status codes.

- **zio-grpc-server**: Server DSL (`ZioGrpc`), handler composition (`GrpcHandler`), middleware (`GrpcMiddleware`), and service definition (`GrpcService`, `GrpcEndpoint`). Handlers take `(GrpcMetadata, In) => ZIO[R, E, Out]` and errors are mapped to status codes via `GrpcErrorCodec`.

- **zio-grpc-client**: Client stubs (`GrpcClient`) and channel management (`GrpcChannel`). Client calls return `ZIO[Any, ClientError[E], Out]` where `ClientError` distinguishes remote domain errors from transport failures.

- **zio-grpc-codegen**: Proto codegen utilities (scaffold for future protoc plugin). Contains `ProtocGenZio` and `Codegen` placeholders.

- **zio-grpc-examples**: Sample applications demonstrating handler patterns, error mapping, and scoped server lifecycle.

## Key Architectural Patterns

### Error Handling Flow

1. Domain errors are typed as ADTs (e.g., `enum ServiceError`)
2. `GrpcErrorCodec[E]` maps domain errors to gRPC `Status` codes
3. Server handlers return `ZIO[R, E, Out]` where `E` is domain-specific
4. Client receives `ClientError[E]` which wraps remote errors or transport failures
5. All error conversions happen at boundaries; business logic stays type-safe

### Server Lifecycle

Servers use ZIO `Scope` for resource management:
```scala
ZioGrpc.server(host, port)(services: _*)
  .provide(dependencies)
```
The server is automatically shut down when the scope closes (via `GrpcServer.scoped`).

### Handler Composition

Handlers are trait-based and composable:
- `GrpcHandler[R, E, In, Out]` represents request handling logic
- `GrpcMiddleware` wraps handlers for cross-cutting concerns
- `GrpcEndpoint` combines method descriptor, codec, error codec, and handler
- `GrpcService` groups multiple endpoints

## Common Build Commands

```bash
# Compile all modules
sbt compile

# Run all tests (uses ZIO Test framework)
sbt test

# Format code (MUST run before commits)
sbt scalafmtAll

# Compile a specific module
sbt "project zio-grpc-server" compile

# Run tests for a specific module
sbt "project zio-grpc-core" test

# Run a specific test suite
sbt "testOnly io.github.riccardomerolla.ziogrpc.core.GrpcErrorCodecSpec"

# Run example application
sbt "project zio-grpc-examples" run
```

## Development Constraints (from AGENTS.md)

This project enforces strict ZIO discipline. All code must follow these rules:

### Effect Construction
- Use `ZIO.attempt`, `ZIO.attemptBlocking`, `ZIO.async`, or `ZIO.fromFuture`
- NO side effects in constructors or pure values
- NO `println` - use `ZIO.log*` instead
- NO `Thread.sleep` or blocking operations outside `attemptBlocking`

### Typed Errors
- ALL errors typed as domain-specific ADTs (enums/case classes)
- NO thrown exceptions in business logic
- NO untyped `Throwable` in error channels
- Map throwables to typed errors at boundaries only

### Resource Safety
- Use `ZIO.acquireRelease` or `Scope` for all resources
- Use `ZLayer.scoped` with finalizers for services
- All background fibers must use `forkScoped`

### Dependency Injection
- Define services as traits with companion object `live` layer
- Use `ZIO.serviceWithZIO[Service](_.method)` pattern for access
- Compose layers at application boundaries, not inside effects
- NO layer creation inside runtime logic

### Concurrency
- Use `zipPar`, `race`, `foreachPar` with `.withParallelism(n)`
- Use `Ref`, `Queue`, `Hub` for shared state
- NO `var` or mutable state
- Structure concurrency with scopes

### Testing
- Use `ZIOSpecDefault` and ZIO Test framework
- Test both success and failure cases
- Use `TestClock`, `TestRandom` for deterministic tests
- Provide test layers with `ZLayer.succeed` or mock implementations

## Code Review Checklist

Before committing, verify:
- [ ] Effects use correct `R`, `E`, `A` types
- [ ] Errors are domain ADTs, not `Throwable`
- [ ] No side effects escape ZIO constructors
- [ ] Resources managed with `acquireRelease` or `Scope`
- [ ] Dependencies injected via ZLayer
- [ ] No blocking outside `attemptBlocking`
- [ ] Code formatted with `sbt scalafmtAll`
- [ ] Tests pass with `sbt test`

## Error Codec Pattern

When defining a new service error type:

```scala
enum MyServiceError:
  case NotFound(id: String)
  case InvalidInput(reason: String)
  case Unauthorized

given GrpcErrorCodec[MyServiceError] with
  def toStatus(error: MyServiceError): io.grpc.Status =
    error match
      case NotFound(id) => Status.NOT_FOUND.withDescription(s"ID: $id")
      case InvalidInput(r) => Status.INVALID_ARGUMENT.withDescription(r)
      case Unauthorized => Status.PERMISSION_DENIED

  def fromStatus(status: io.grpc.Status): Option[MyServiceError] = ???
```

## Testing Protocol

All changes MUST include tests. Use ZIO Test patterns:
- Property-based tests with `Gen` and `check` for invariants
- Mock layers for dependencies
- Test error cases explicitly
- Use `TestClock` for time-based behavior

Example test structure:
```scala
object MyServiceSpec extends ZIOSpecDefault:
  def spec = suite("MyService")(
    test("success case") {
      for {
        result <- MyService.operation(validInput)
      } yield assertTrue(result == expected)
    },
    test("error case") {
      for {
        exit <- MyService.operation(invalidInput).exit
      } yield assert(exit)(fails(equalTo(ExpectedError)))
    }
  ).provide(MyService.test, dependencies)
```

## References

- **`docs/BLUEPRINT.md`** - Complete design philosophy, architectural decisions, typed error requirements, streaming semantics, competitive positioning vs scalapb/zio-grpc, and execution roadmap (Phase 1-3)
- `AGENTS.md` - Complete ZIO coding standards and validation checklist
- `.github/copilot-instructions.md` - Detailed patterns and anti-patterns for ZIO development
- `zio-grpc-examples/src/main/scala/` - Handler patterns and example implementations
