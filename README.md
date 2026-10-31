# Deckwise Demo · Backend

This is the default branch of [Deckwise Demo](https://github.com/Seacolour/Deckwise-demo), a self-hosted document workspace where users collaborate with an AI Agent on Markdown files. The other modules live on the [`agent`](https://github.com/Seacolour/Deckwise-demo/tree/agent) and [`web`](https://github.com/Seacolour/Deckwise-demo/tree/web) branches.

The Backend provides email-code login, per-user BYOK model credentials, projects, documents, file storage, chat sessions, and the gRPC bridge to Agent. It uses Spring Boot, PostgreSQL, Flyway, jOOQ, Redis, Cloudflare R2, and Resend.

## Run locally

Use JDK 25, PostgreSQL, and Redis. Create a **new empty** database named `backend_db` on port `5432`. Export the values listed in [`env.example`](env.example) in your shell or IDE; Maven and Spring do not read `.env` automatically.

```bash
./mvnw flyway:migrate
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd`. Flyway defines a fresh V1–V12 schema. Maven generates jOOQ classes from that schema, so migrate before the first build. Do not apply this baseline to an older PaperPuppy database.

Start the Agent and Web modules from their branches. Default ports: Backend HTTP `8104`, Backend tool gRPC `40052`, Agent gRPC `41051`, Web `3000`.

### Pandoc for document conversion

Markdown collaboration does not require Pandoc. Importing a Word document as editable Markdown does. The default [`pandoc4j`](https://github.com/TinyCircle/Pandoc4j) dependency uses a Pandoc installation on your machine; check it with `pandoc --version` (Windows: `winget install JohnMacFarlane.Pandoc`). The Backend Docker image already installs Pandoc.

If you cannot install Pandoc on the machine, start Backend with `./mvnw -Pmanaged-pandoc spring-boot:run` (Windows: `.\mvnw.cmd -Pmanaged-pandoc spring-boot:run`). This adds `pandoc4j-binary` alongside the wrapper. It downloads and caches a managed Pandoc executable on **first conversion**, so an offline interview demo must run one DOCX conversion while online beforehand. Use the same profile for packaging if you plan to run the resulting JAR.

## Scope

The demo focuses on Markdown editing and reference-file reading. Slide editing, payment, and external cloud-drive connectors are outside this version. Model keys are entered in Web settings and encrypted by Backend.
