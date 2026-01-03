# zio-grpc

ZIO-gRPC is a type-safe gRPC framework for Scala 3 and ZIO 2.x. This repository is the early scaffold that turns the former `zio-quickstart` template into a multi-module foundation aligned with `docs/BLUEPRINT.md`.

## Modules

```
zio-grpc-core/     // protocol codecs + shared types
zio-grpc-server/   // server DSL, handlers, middleware
zio-grpc-client/   // client stubs + channel management
zio-grpc-codegen/  // proto codegen utilities (scaffold)
zio-grpc-examples/ // sample apps
```

## Example

A minimal hello-world example lives in `zio-grpc-examples` and shows how to define a handler, map typed errors to gRPC status codes, and start a scoped server.

## Development

- Format: `sbt scalafmtAll`
- Test: `sbt test`
- Compile: `sbt compile`

## Contributing

1. Follow the guidelines in `AGENTS.md`.
2. Keep effects typed and resource-safe.
3. Add tests for any new behavior.
