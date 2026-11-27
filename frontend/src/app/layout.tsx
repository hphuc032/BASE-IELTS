import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: "BASE IELTS",
  description: "BASE IELTS learning platform",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body className="min-h-screen">
        <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:bg-white focus:p-3">
          Skip to main content
        </a>
        <header className="border-b border-[#dce3d8]">
          <nav aria-label="Site" className="mx-auto flex max-w-5xl items-center justify-between px-6 py-5">
            <Link href="/" className="text-lg font-bold tracking-tight">BASE IELTS</Link>
            <Link href="/account" className="rounded-full border border-[#bdccbf] px-4 py-2 text-sm font-semibold">
              Account
            </Link>
          </nav>
        </header>
        <main id="main" className="mx-auto max-w-5xl px-6 py-16 sm:py-24">
          {children}
        </main>
      </body>
    </html>
  );
}
