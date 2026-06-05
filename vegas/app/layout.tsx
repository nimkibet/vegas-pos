import "../app/globals.css";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Vegas POS - Owner's Dashboard",
  description: "Mobile-responsive dashboard for Vegas POS system",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}