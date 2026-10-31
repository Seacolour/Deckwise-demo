// The deployment origin remains configurable while the Demo uses private Web routes.
const DEFAULT_SITE_URL = "http://localhost:3000";

function normalizeSiteUrl(value: string | undefined): string {
  const trimmed = value?.trim();
  if (!trimmed) {
    return DEFAULT_SITE_URL;
  }

  try {
    return new URL(trimmed).origin;
  } catch {
    return DEFAULT_SITE_URL;
  }
}

export const siteMetadata = {
  name: "Deckwise Demo",
  shortName: "Deckwise Demo",
  url: normalizeSiteUrl(process.env.NEXT_PUBLIC_SITE_URL),
  title: "Deckwise Demo | AI Document Workspace",
  description:
    "A workspace for collaborating with an AI agent on documents, ideas, and research.",
  ogImagePath: "/opengraph-image",
  themeColor: "#0f62fe",
  backgroundColor: "#ffffff",
};

export function absoluteSiteUrl(path = "/"): string {
  return new URL(path, siteMetadata.url).toString();
}
