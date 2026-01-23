# zio-grpc

ZIO-gRPC is a type-safe gRPC framework for Scala 3 and ZIO 2.x. This repository provides a multi-module foundation for building effect-oriented gRPC services with typed error channels, resource safety, and ScalaPB integration.

## Modules

```
zio-grpc-core/     // protocol codecs + shared types
zio-grpc-server/   // server DSL, handlers, middleware
zio-grpc-client/   // client stubs + channel management
zio-grpc-codegen/  // protoc-gen-zio plugin for code generation
zio-grpc-examples/ // sample apps
```

## Features

✅ **Typed Error Channels** - Service methods use `ZIO[R, E, A]` with domain-specific error types (no `Throwable` leaks)
✅ **Resource Safety** - All resources managed with `ZIO.acquireRelease` and scoped lifecycles
✅ **Effect-Oriented** - Pure, composable ZIO effects throughout the API
✅ **Code Generation** - `protoc-gen-zio` plugin generates idiomatic Scala 3 + ZIO code from `.proto` files
✅ **Reflection Support** - Generated descriptors enable `grpcurl` and gRPC reflection
✅ **Scala 3** - Modern syntax with enums, extension methods, and type inference

## Quick Start

### 1. Define your service in proto

```protobuf
syntax = "proto3";
package example;

service Calculator {
  rpc Add (Numbers) returns (Result);
}

message Numbers {
  int32 a = 1;
  int32 b = 2;
}

message Result {
  int32 sum = 1;
}
```

### 2. Generate ZIO code

```bash
# Build the plugin
sbt "project codegen" assembly

# Generate code
protoc \
  --plugin=protoc-gen-zio=zio-grpc-codegen/bin/protoc-gen-zio \
  --zio_out=target/gen \
  --proto_path=src/main/protobuf \
  src/main/protobuf/calculator.proto
```

### 3. Implement your service

```scala
import example.{Calculator, Numbers, Result}
import io.github.riccardomerolla.ziogrpc.core._
import io.github.riccardomerolla.ziogrpc.server._
import zio._

object CalculatorService extends ZIOAppDefault:
  // Define your handler with typed errors
  val handler = new Calculator[Nothing]:
    def add(req: Numbers): ZIO[Any, Nothing, Result] =
      ZIO.succeed(Result(req.a + req.b))

  // Wire it into a GrpcService
  val service: GrpcService[Any] = Calculator.service(
    handler = handler,
    errorCodec = ???,  // Your error codec
    addRequestCodec = ???,  // Your message codecs
    addResponseCodec = ???
  )

  // Start the server
  override def run = ZIO.scoped {
    ZioGrpc
      .server(ServerConfig("0.0.0.0", 9000), Chunk(service))
      .flatMap(_.start *> ZIO.never)
  }
```

## Code Generation

The `protoc-gen-zio` plugin generates:

**Service Trait** with typed error channel:
```scala
trait Calculator[E]:
  def add(request: Numbers): ZIO[Any, E, Result]
```

**Companion Object** with wiring:
```scala
object Calculator:
  def serviceDescriptor: Descriptors.ServiceDescriptor
  def service[E](...): GrpcService[Any]
```

**Descriptor File** for reflection:
```scala
object CalculatorDescriptors:
  lazy val fileDescriptor: Descriptors.FileDescriptor
```

See `examples/codegen-demo/README.md` for a complete walkthrough.

## Development

- Format: `sbt scalafmtAll`
- Test: `sbt test`
- Compile: `sbt compile`
- Build plugin: `sbt "project codegen" assembly`

## Contributing

1. Follow the guidelines in `AGENTS.md`.
2. Keep effects typed and resource-safe.
3. Add tests for any new behavior.
4. Use ZIO best practices (see `AGENTS.md` for details).

## License

MIT - see LICENSE file for details.
