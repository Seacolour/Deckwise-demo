import Image, { type ImageProps } from "next/image";
import { cn } from "@/src/lib/utils";

type BrandLogoProps = Omit<ImageProps, "src" | "alt"> & {
    alt?: string;
};

export function BrandLogo({ alt, className, draggable = false, ...props }: BrandLogoProps) {
    const ariaHidden = props["aria-hidden"] === true || props["aria-hidden"] === "true";
    const label = typeof props["aria-label"] === "string" ? props["aria-label"] : "Deckwise";

    return (
        <Image
            src="/icon.png"
            alt={ariaHidden ? "" : alt ?? label}
            width={512}
            height={512}
            className={cn("block object-contain", className)}
            draggable={draggable}
            {...props}
        />
    );
}
