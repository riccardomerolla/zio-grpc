# ZIO-gRPC: Type-Safe gRPC Framework for Effect-Oriented Scala 3

## Executive Summary

ZIO-gRPC is a **clean-room** Scala 3 + ZIO 2.x gRPC framework that treats effect systems as first-class infrastructure. It aims to be the Scala-native alternative to `scalapb/zio-grpc` by emphasizing:

- **Typed errors as a hard requirement** (sealed ADTs mapped to gRPC Status)
- **ZLayer-first dependency injection** for service wiring
- **Resource-safe servers and streams** via `Scope`
- **ZStream-native streaming** with backpressure and cancellation guarantees

ZIO-gRPC starts on the JVM with `grpc-java` transport, while preserving a portability path to grpc-web and Scala.js.

***

## 1. Architecture: Integrating ZIO's Effect System with gRPC

### 1.1 Resource Management via ZIO Scope

Use `Scope` to ensure atomic startup and deterministic shutdown of servers and streams.

```scala
val server: ZIO[Scope, IOException, Server] =
  ZioGrpc.server("0.0.0.0", 8080) {
    UserService.handler(userServiceImpl) ++
    OrderService.handler(orderServiceImpl)
  }

ZIO.scoped {
  server.flatMap(_ => ZIO.never)
}
```

**Requirements:**

- Server startup is atomic (no partial binding or leaked pools)
- Finalizers run on normal, error, and interruption paths
- Cleanup order is deterministic (streams → channels → thread pools)

### 1.2 Typed Error Mapping: Sealed ADTs ↔ Status (MVP)

Typed error mapping is a **hard requirement in MVP**. All domain errors are modeled as sealed ADTs and mapped to gRPC `Status` at the transport edge.

```scala
sealed trait UserServiceError
case class UserNotFound(id: UUID) extends UserServiceError
case class InsufficientPermissions(reason: String) extends UserServiceError
case class DatabaseError(cause: Throwable) extends UserServiceError

object UserServiceError {
  implicit val grpcErrorCodec: GrpcErrorCodec[UserServiceError] =
    GrpcErrorCodec.derive[UserServiceError](
      UserNotFound(_) -> Status.NOT_FOUND,
      InsufficientPermissions(_) -> Status.PERMISSION_DENIED,
      DatabaseError(_) -> Status.INTERNAL
    )
}

val getUser: ZIO[UserService, UserServiceError, User] =
  ZIO.serviceWithZIO[UserService](_.getUser(userId))
```

**Interop requirement:**

- Provide a boundary-layer interop that converts `sealed ADT ↔ Status` automatically
- Business logic **never** depends on `StatusException` or `Throwable`

### 1.3 Streaming with ZStream Backpressure

Streaming uses `ZStream` as the first-class API and must honor backpressure and cancellation semantics.

```scala
trait OrderService {
  def streamOrders(filter: OrderFilter): ZStream[Any, OrderError, Order]
}
```

**Testable requirements:**

- Client cancellation interrupts server handlers
- Backpressure is preserved for server-streaming and bidi streaming
- Chunk safety (no assumptions about chunk size)

***

## 2. Dependency Injection: ZLayer Integration

Service dependencies are encoded in the environment and composed via `ZLayer`.

```scala
trait UserRepository {
  def getUser(id: UUID): ZIO[Any, RepositoryError, User]
}

object UserRepository {
  val live: ZLayer[Database, Nothing, UserRepository] =
    ZLayer.fromFunction(UserRepositoryLive.apply)
}
```

**Rules:**

- No layer creation inside effect bodies
- Use accessor helpers on companions
- Dependencies are wired at the application edge

***

## 3. Context Propagation (FiberRef)

Request-scoped context is stored in a `FiberRef` and propagated across async boundaries.

```scala
object RequestContext {
  val fiberRef: FiberRef[Option[RequestContext]] = FiberRef.unsafe.make(None)

  def get: UIO[Option[RequestContext]] = fiberRef.get
  def set(ctx: RequestContext): UIO[Unit] = fiberRef.set(Some(ctx))
}
```

**Requirement:**

- Middleware can attach and read context without explicit parameter passing

***

## 4. Transport Scope

### 4.1 JVM First (grpc-java)

The initial transport is grpc-java for correctness, feature completeness, and compatibility with existing infra.

### 4.2 Portability Path

Preserve abstractions that allow grpc-web and Scala.js transport backends in Phase 3.

***

## 5. Code Generation Path

### 5.1 ScalaPB Extension First (MVP)

The fastest and proven path is to extend ScalaPB via a protoc plugin.

**Compatibility target:**

- ScalaPB extension plugin first (Phase 1)
- Optional alternate codegen path in later phases

Generated code should emit ZIO-native service traits and derive error codecs from protobuf `oneof` error messages.

***

## 6. Proposed Project Structure

```
zio-grpc/
├── zio-grpc-core/       # protocol codecs, error mapping, metadata
├── zio-grpc-server/     # server builder, handler/middleware
├── zio-grpc-client/     # client stubs + channel management
├── zio-grpc-codegen/    # ScalaPB-based protoc plugin
└── zio-grpc-examples/   # hello-world, streaming chat
```

***

## 7. Execution Roadmap

### Phase 1: MVP (3-4 months, core viability)

- [ ] ScalaPB extension plugin (protoc-gen-zio)
- [ ] Unary RPC server/client (grpc-java transport)
- [ ] **Typed error codec derivation (required)**
- [ ] **Interop layer: sealed ADT ↔ Status mapping at edge**
- [ ] Basic server/client DSL
- [ ] Example: hello-world service
- [ ] Reflection bootstrap (register reflection service; full descriptors follow in Phase 2)

### Phase 2: Production (2-3 months, parity + hardening)

- [ ] Server-side streaming with ZStream
- [ ] Client-side streaming
- [ ] Bidirectional streaming
- [ ] Server reflection (grpc.reflection.v1)
- [ ] Middleware/interceptors (auth, tracing)
- [ ] Integration with zio-logging
- [ ] **E2E tests**
- [ ] **Streaming cancellation tests**

### Phase 3: Ecosystem (ongoing, portability + extensions)

- [ ] grpc-web / Scala.js evaluation and prototype
- [ ] OpenAPI/gRPC transcoding
- [ ] zio-grpc-config integration
- [ ] Metrics layer (Prometheus)
- [ ] Load balancing client-side

### Current Progress (Jan 2026)

- ✅ Unary server/client path with typed error interop and ADT ↔ Status mapping
- ✅ Minimal reflection service registration (grpcurl discoverable once descriptors are emitted)
- ✅ Example hello-world service and smoke test
- ⏳ Codegen (ScalaPB extension) to emit descriptors and typed errors for reflection/grpcurl
- ⏳ Streaming support, middleware, and full reflection with generated descriptors

***

## 8. Competitive Positioning vs scalapb/zio-grpc

ZIO-gRPC is **not** a fork of `scalapb/zio-grpc` but a clean-room alternative with a different design center:

- **Typed errors are mandatory** (sealed ADT error channels)
- **ZLayer-first architecture** for dependency wiring
- **FiberRef context propagation** baked into the handler model
- **Transport edge mapping** for ADT ↔ Status

We should match or exceed `scalapb/zio-grpc` on streaming backpressure and cancellation semantics, and validate those guarantees with explicit tests.

### Why not scalapb/zio-grpc?

`scalapb/zio-grpc` is a solid and mature library, but it does not center the design on typed error ADTs or ZLayer-first wiring. ZIO-gRPC targets a stricter effect-and-error model: sealed ADTs are mandatory, errors map to `Status` only at the transport edge, and dependency wiring is encoded in the environment. This clean-room approach keeps the design ZIO-idiomatic while leaving room for transport portability (grpc-web, Scala.js).

***

## 9. Validation Checklist (Before Output)

- [ ] All effects are pure descriptions
- [ ] Errors use sealed ADTs (no raw Throwable)
- [ ] Mapping to Status happens at transport edge only
- [ ] Resources are scoped and finalizers always run
- [ ] Streaming backpressure and cancellation are testable
- [ ] ZLayer wiring only at application boundary

***

## References

- Zionomicon, Chapter 16: Resource Handling (Scopes)
- Zionomicon, Chapter 17: Dependency Injection Essentials
- Zionomicon, Chapter 19: Contextual Data with FiberRef
- Zionomicon, Chapter 35: Middleware patterns
