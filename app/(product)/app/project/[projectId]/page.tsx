import { redirect } from "next/navigation";
import { getProjectCanvasPath } from "@/src/lib/routing/projectRoutes";

export default async function DeckwiseProjectUniversalLinkFallback({
  params,
}: {
  params: Promise<{ projectId: string }>;
}) {
  const { projectId } = await params;
  redirect(getProjectCanvasPath(projectId));
}
