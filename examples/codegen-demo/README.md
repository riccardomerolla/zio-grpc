# Codegen Demo: Generating ZIO-gRPC Services with protoc-gen-zio

This walkthrough shows how to run the `protoc-gen-zio` code generator on a sample proto, wire the emitted descriptors, and validate with grpcurl.

## Prerequisites
- `sbt` installed
- `protoc` available on PATH
- JDK 17+ (tested with Adoptium JDK 17)

## Layout
```
examples/codegen-demo/
  src/main/protobuf/helloworld.proto   # sample proto
  src/main/scala/GreeterServer.scala   # runnable server using generated code
```

## 1) Build the codegen plugin JAR
```bash
sbt "project codegen" assembly
```
This produces `zio-grpc-codegen/target/scala-3.3.1/protoc-gen-zio.jar` - a fat JAR with all dependencies.

## 2) Run protoc with the ZIO plugin
```bash
protoc \
  --plugin=protoc-gen-zio=../../zio-grpc-codegen/bin/protoc-gen-zio \
  --zio_out=target/gen \
  --proto_path=src/main/protobuf \
  src/main/protobuf/helloworld.proto
```
Generated files land under `target/gen/helloworld/`:
- `GreeterZioGrpc.scala` (service trait + endpoints + `serviceDescriptor` hook)
- `HelloworldDescriptors.scala` (embedded `FileDescriptor` for reflection)

## 3) Wire the generated artifacts into a server
A minimal server skeleton:
```scala
import helloworld.{Greeter, HelloRequest, HelloReply}
import io.github.riccardomerolla.ziogrpc.server._
import io.github.riccardomerolla.ziogrpc.core._
import zio._

object GreeterServer extends ZIOAppDefault:
  given GrpcErrorCodec[Nothing] = GrpcErrorCodec(_ => io.grpc.Status.OK, _ => None)
  
  // Provide simple codecs for your message types
  given GrpcCodec[HelloRequest] = ???
  given GrpcCodec[HelloReply] = ???

  val service: GrpcService[Any] = Greeter.service(
    handler = new Greeter[Nothing]:
      def sayHello(req: HelloRequest) = ZIO.succeed(HelloReply(s"Hello, ${req.name}!")),
    errorCodec = summon[GrpcErrorCodec[Nothing]],
    sayHelloRequestCodec = summon[GrpcCodec[HelloRequest]],
    sayHelloResponseCodec = summon[GrpcCodec[HelloReply]]
  )

  override def run = ZIO.scoped {
    ZioGrpc
      .server(ServerConfig("0.0.0.0", 9000), Chunk(service))
      .flatMap(_.start *> ZIO.never)
  }
```
Ensure the generated `HelloworldDescriptors.scala` is on the classpath so reflection works.

## 4) Call with grpcurl
With the server running:
```bash
grpcurl -plaintext localhost:9000 list
grpcurl -plaintext -d '{"name":"Nancy"}' localhost:9000 helloworld.Greeter/SayHello
```
Because we register `ProtoReflectionService` in the server, once descriptors are on the classpath, grpcurl will discover the service via reflection.

## 5) Clean up generated files
To remove the generated output for a clean slate:
```bash
rm -rf target/gen
```

To stop the demo server started via sbt, press Ctrl+C (it runs in a forked JVM per repo settings).

## Generated Code Features

The `protoc-gen-zio` plugin generates:

### 1. Service Trait
```scala
trait Greeter[E]:
  def sayHello(request: HelloRequest): ZIO[Any, E, HelloReply]
```
- Uses typed error channel `E` (no `Throwable` exposed)
- Methods return `ZIO` effects (pure, composable blueprints)
- Compatible with any error type you choose

### 2. Companion Object
```scala
object Greeter:
  def serviceDescriptor: Descriptors.ServiceDescriptor
  def service[E](...): GrpcService[Any]
```
- `serviceDescriptor` provides reflection metadata for grpcurl/grpc-web
- `service` factory wires your handler into the gRPC server infrastructure
- Accepts codecs for serialization and error mapping

### 3. Descriptor File
```scala
object HelloworldDescriptors:
  lazy val fileDescriptor: Descriptors.FileDescriptor
```
- Base64-encoded proto descriptor for runtime reflection
- Required for `grpcurl` and other reflection-based tools

## Notes
- The current codegen handles **unary methods only** (no streaming yet - Phase 1 MVP)
- You still need to supply `GrpcCodec`/`GrpcErrorCodec` implementations for messages/errors
- For production use, integrate with ScalaPB for automatic message codec generation
- If you want sbt to manage sources, add `target/gen` to `Compile / unmanagedSourceDirectories` or use sbt-protoc

## Integration with sbt-protoc (Optional)

To automatically generate code during compilation:

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
