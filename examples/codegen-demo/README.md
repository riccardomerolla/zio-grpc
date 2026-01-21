# Codegen Demo: Generating ZIO-gRPC Services with ScalaPB Extension

This walkthrough shows how to run the `protoc-gen-zio` code generator on a sample proto, wire the emitted descriptors, and validate with grpcurl.

## Prerequisites
- `sbt` installed
- `protoc` available on PATH
- JDK 21+ (tested on GraalVM 24)

## Layout
```
examples/codegen-demo/
  src/main/protobuf/helloworld.proto   # sample proto
  src/main/scala/GreeterServer.scala   # runnable server using generated code
```

## 1) Build the codegen plugin JAR
```bash
sbt ";project codegen; package"
```
This produces `zio-grpc-codegen/target/scala-3.3.1/zio-grpc-codegen_3-*.jar`.

## 2) Run protoc with the ZIO plugin
```bash
protoc \
  --plugin=protoc-gen-zio=../zio-grpc-codegen/target/scala-3.3.1/zio-grpc-codegen_3-*.jar \
  --java_out=target/gen \
  --zio_out=target/gen \
  --proto_path=src/main/protobuf \
  src/main/protobuf/helloworld.proto
```
Generated files land under `target/gen/helloworld/`:
- `GreeterZioGrpc.scala` (service trait + endpoints + `serviceDescriptor` hook)
- `HelloworldDescriptors.scala` (embedded `FileDescriptor` for reflection)

## 3) Wire the generated artifacts into a server
A minimal server skeleton (pseudo-code):
```scala
import helloworld.{Greeter, HelloRequest, HelloReply, HelloworldDescriptors}
import io.github.riccardomerolla.ziogrpc.server._
import io.github.riccardomerolla.ziogrpc.core._
import io.grpc.MethodDescriptor
import zio._

object GreeterServer extends ZIOAppDefault:
  given GrpcErrorCodec[Nothing] = GrpcErrorCodec(_ => io.grpc.Status.OK, _ => None)
  val reqCodec: GrpcCodec[HelloRequest] = ???
  val resCodec: GrpcCodec[HelloReply] = ???

  val service: GrpcService[Any] = Greeter.service(
    handler = new Greeter[Nothing]:
      def sayHello(req: HelloRequest) = ZIO.succeed(HelloReply(s"Hello, ${req.name}!")),
    errorCodec = summon[GrpcErrorCodec[Nothing]],
    sayHelloRequestCodec = reqCodec,
    sayHelloResponseCodec = resCodec
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

## Notes
- The current codegen handles unary methods only (no streaming yet).
- You still need to supply `GrpcCodec`/`GrpcErrorCodec` implementations for messages/errors; codegen does not yet emit codecs.
- If you want sbt to manage sources, add `target/gen` to `Compile / unmanagedSourceDirectories` or wire sbt-protoc to emit into `sourceManaged`.
