// /api/runtime-config 端点已废弃
// 配置现在通过 process.env 直接读取，不再需要运行时API端点

export async function GET() {
  return Response.json(
    { error: 'This endpoint is deprecated. Configuration is now handled via process.env.' },
    { status: 410 }
  );
}
