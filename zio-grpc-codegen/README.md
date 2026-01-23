# zio-grpc-codegen

ScalaPB protoc plugin that generates ZIO-native service traits and server stubs for gRPC services.

## Overview

`protoc-gen-zio` is a protoc plugin that transforms `.proto` files into idiomatic Scala 3 + ZIO 2.x code. It generates service traits with typed error channels, companion objects with wiring helpers, and descriptor files for reflection support.

## Features

- **Typed Error Channels**: Service methods use `ZIO[R, E, A]` instead of `Throwable`
- **Pure Effects**: All operations are pure ZIO effects (immutable blueprints)
- **Resource Safety**: Generated code integrates with ZIO's resource management
- **Reflection Support**: Emits descriptors for `grpcurl` and gRPC reflection
- **Scala 3 Native**: Uses modern Scala 3 syntax and idioms
- **Phase 1 MVP**: Supports unary RPCs (streaming not yet implemented)

## Building

Build the fat JAR with all dependencies:

```bash
sbt "project codegen" assembly
```

This creates `target/scala-3.3.1/protoc-gen-zio.jar`.

## Usage

### Command Line

Use the provided wrapper script:

```bash
protoc \
  --plugin=protoc-gen-zio=zio-grpc-codegen/bin/protoc-gen-zio \
  --zio_out=target/generated \
  --proto_path=src/main/protobuf \
  src/main/protobuf/yourservice.proto
```

### With sbt-protoc

Add to your `build.sbt`:

```scala
// project/plugins.sbt
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6")

// build.sbt
Compile / PB.targets := Seq(
  PB.gens.java -> (Compile / sourceManaged).value,
  PB.gens.plugin("zio") -> (Compile / sourceManaged).value
)

PB.protocOptions := Seq(
  s"--plugin=protoc-gen-zio=${baseDirectory.value}/../zio-grpc-codegen/bin/protoc-gen-zio"
)
```

## Generated Code

For a proto service:

```protobuf
syntax = "proto3";
package example;

service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply);
}

message HelloRequest {
  string name = 1;
}

message HelloReply {
  string message = 1;
}
```

The plugin generates:

### Service Trait

```scala
package example

trait Greeter[E]:
  def sayHello(request: HelloRequest): ZIO[Any, E, HelloReply]
```

- Generic error type `E` for typed error handling
- ZIO effects instead of callbacks or futures
- Pure, composable method signatures

### Companion Object

```scala
object Greeter:
  def serviceDescriptor: Descriptors.ServiceDescriptor =
    ExampleDescriptors.fileDescriptor.findServiceByName("Greeter")

  def service[E](
    handler: Greeter[E],
    errorCodec: GrpcErrorCodec[E],
    sayHelloRequestCodec: GrpcCodec[HelloRequest],
    sayHelloResponseCodec: GrpcCodec[HelloReply],
  ): GrpcService[Any] = ???
```

- `serviceDescriptor`: Reflection metadata for grpcurl/grpc-web
- `service`: Factory method to wire handler into gRPC server

### Descriptor File

```scala
package example

object ExampleDescriptors:
  private val encoded: String = "ChBleGFtcGxlLnByb3RvEg..."
  
  lazy val fileDescriptor: Descriptors.FileDescriptor =
    Descriptors.FileDescriptor.buildFrom(
      DescriptorProtos.FileDescriptorProto.parseFrom(
        Base64.getDecoder.decode(encoded)
      ),
      Array.empty
    )
```

- Base64-encoded proto descriptor
- Required for reflection-based tools
- Lazy initialization for efficiency

## Implementation Notes

### Current Scope (Phase 1 MVP)

✅ Unary RPCs
✅ Typed error channels
✅ Service descriptors for reflection
✅ Package-qualified types
✅ Scala 3 syntax

### Not Yet Implemented

❌ Streaming RPCs (client/server/bidirectional)
❌ Automatic codec generation (requires ScalaPB integration)
❌ Client stub generation
❌ Interceptors/middleware generation

### Design Principles

1. **Effects as Blueprints**: Generated ZIO values are pure descriptions, not running computations
2. **No Throwable Leaks**: All errors go through typed error channels
3. **Resource Safety**: Integration with ZIO's resource management primitives
4. **ASCII Output**: Generated code uses only ASCII characters (no unicode)
5. **Minimal Dependencies**: Plugin JAR includes only protobuf-java and ZIO

## Testing

The plugin includes comprehensive integration tests:

```bash
sbt "project codegen" test
```

Tests verify:
- Service trait generation
- Typed error channel presence
- Companion object structure
- Descriptor file encoding
- Streaming RPC rejection
- Method descriptor types

## Examples

See `examples/codegen-demo/` for a complete walkthrough:

1. Proto definition
2. Code generation
3. Server implementation
4. Testing with grpcurl

## License

MIT - see LICENSE file in repository root.
