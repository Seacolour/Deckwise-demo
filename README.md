# Deckwise Demo · Agent

This branch contains the Agent service for [Deckwise Demo](https://github.com/Seacolour/Deckwise-demo). See the [`backend`](https://github.com/Seacolour/Deckwise-demo/tree/backend) default branch for the full project overview and the [`web`](https://github.com/Seacolour/Deckwise-demo/tree/web) branch for the interface.

The service runs a tool-calling loop over Backend documents and files. It receives per-user BYOK credentials from Backend over local gRPC. Its default gRPC port is `41051`; it calls Backend tools on port `40052`.

With JDK 25 and Backend running, export any desired overrides from [`env.example`](env.example), then run `./mvnw spring-boot:run` (or `mvnw.cmd` on Windows).
