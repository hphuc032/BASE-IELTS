import type { NextConfig } from "next";

const backend = new URL(process.env.BASE_BACKEND_ORIGIN ?? "http://127.0.0.1:8080");

if (
  !["http:", "https:"].includes(backend.protocol) ||
  backend.username ||
  backend.password ||
  backend.pathname !== "/" ||
  backend.search ||
  backend.hash
) {
  throw new Error("BASE_BACKEND_ORIGIN must be a plain HTTP(S) origin.");
}

const config: NextConfig = {
  poweredByHeader: false,
  async rewrites() {
    return [
      {
        source: "/api/v1/:path*",
        destination: backend.origin + "/api/v1/:path*",
      },
    ];
  },
};

export default config;
