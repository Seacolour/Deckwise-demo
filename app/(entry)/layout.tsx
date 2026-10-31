import type { ReactNode } from "react";
import { RuntimeRootDocument } from "@/app/_root/RootDocument";

export default function EntryLayout({ children }: { children: ReactNode }) {
  return <RuntimeRootDocument>{children}</RuntimeRootDocument>;
}
