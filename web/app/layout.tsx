import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "BabyDataLog — Sync Server",
  description: "BabyDataLog sync server admin",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
