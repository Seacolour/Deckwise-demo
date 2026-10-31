import type { ReactNode } from "react";

// `/app` is the authenticated product home. Settings normally opens through
// `#settings/...` so it can overlay any active surface, including canvas. Keep
// this slot as the direct-route fallback for `/app/settings/...` links, and add
// future sections to the shared Settings Center instead of creating new sheets.
export default function AppLayout({
  children,
  modal,
}: {
  children: ReactNode;
  modal: ReactNode;
}) {
  return (
    <>
      {children}
      {modal}
    </>
  );
}
