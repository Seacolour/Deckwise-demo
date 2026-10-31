# Deckwise Demo · Web

This branch contains the Next.js interface for [Deckwise Demo](https://github.com/Seacolour/Deckwise-demo). See the [`backend`](https://github.com/Seacolour/Deckwise-demo/tree/backend) default branch for the full project overview and the [`agent`](https://github.com/Seacolour/Deckwise-demo/tree/agent) branch for the Agent service.

The workbench provides email login, BYOK model settings, conversations, workspace files, local file previews, and a Markdown editor.

With Node.js 24 and Backend running on port `8104`, copy [`env.example`](env.example) to `.env.local`, then run:

```bash
npm ci
npm run dev
```

Open `http://localhost:3000`. When Backend API contracts change, run `npm run api:gen` while Backend is running.
