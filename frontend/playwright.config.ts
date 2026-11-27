import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests", workers: 1, timeout: 120_000, reporter: "dot",
  use: {
    baseURL: process.env.BASE_ACCEPTANCE_TLS_DIR ? "https://127.0.0.1:3443" : "http://127.0.0.1:3301",
    ignoreHTTPSErrors: Boolean(process.env.BASE_ACCEPTANCE_TLS_DIR),
    launchOptions: process.env.BASE_ACCEPTANCE_TLS_DIR
      ? { args: ["--host-resolver-rules=MAP identity.test 127.0.0.1", "--no-proxy-server"] } : undefined,
    channel: "msedge", trace: "off", screenshot: "off", video: "off",
  },
});
