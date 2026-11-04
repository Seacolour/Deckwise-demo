# Deckwise Demo · Web

This branch contains the Next.js interface for [Deckwise Demo](https://github.com/Seacolour/Deckwise-demo). See the [`backend`](https://github.com/Seacolour/Deckwise-demo/tree/backend) default branch for the full project overview and the [`agent`](https://github.com/Seacolour/Deckwise-demo/tree/agent) branch for the Agent service.

The workbench provides email login, BYOK model settings, conversations, workspace files, local file previews, and a Markdown editor.

With Node.js 24 and a compatible Backend running, copy [`env.example`](env.example) to `.env.local`, set `API_PROXY_TARGET`, then run:

```bash
npm ci
npm run dev
```

Open `http://localhost:3000`. When Backend API contracts change, run `npm run api:gen` while Backend is running.

For the Java Demo Backend, the example target is `http://localhost:8104`. To use the [FastAPI + LangGraph Python backend](https://github.com/Seacolour/Deckwise-backend-Python), set `API_PROXY_TARGET=http://127.0.0.1:8000` and follow that repository's PostgreSQL, Redis, and model credential setup. The workbench can then show project files, document editing, JSON tools, image questions, and conversation attachments.

The Python backend's optional Notion MCP connection starts from **Plugins → MCP plugins**. Set its `NOTION_MCP_REDIRECT_URI` to this Web origin plus `/oauth/notion-mcp/callback` (the local default is `http://localhost:3000/oauth/notion-mcp/callback`). Authorization is per user; the Notion connection and plugin switch are separate. When both are active, the Agent can search and read that user's Notion pages through two allowlisted MCP tools.

For a production build check, supply a non-`local` public Backend signature:

```powershell
$env:NEXT_PUBLIC_BACKEND_SIGNATURE='deckwise-demo-public'
npm run build
Remove-Item Env:NEXT_PUBLIC_BACKEND_SIGNATURE
```
