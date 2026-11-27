import { createServer } from "node:https";
import { createServer as createHttpServer, request } from "node:http";
import { readFileSync, existsSync, appendFileSync } from "node:fs";
import { join } from "node:path";

const dir = process.env.BASE_ACCEPTANCE_TLS_DIR;
const authority = "127.0.0.1:3443";
const browserAuthority = "identity.test:3443";

const server = createServer({
  cert: readFileSync(join(dir, "certificate.pem")),
  key: readFileSync(join(dir, "private.key")),
}, (incoming, outgoing) => {
  if (![authority, browserAuthority].includes(incoming.headers.host)) {
    outgoing.writeHead(421).end();
    return;
  }
  if (existsSync(join(dir, "cutover-closed"))) {
    outgoing.writeHead(503).end();
    return;
  }
  const headers = { ...incoming.headers };
  for (const key of Object.keys(headers)) {
    if (key === "forwarded" || key.startsWith("x-forwarded-")) delete headers[key];
  }
  headers["x-forwarded-proto"] = "https";
  headers["x-forwarded-host"] = incoming.headers.host;
  const api = incoming.url.startsWith("/api/v1/");
  const upstream = request({
    hostname: "127.0.0.1", port: api ? 18083 : 3301, path: incoming.url,
    method: incoming.method, headers,
  }, response => {
    const marker = join(dir, "drop-profile-response");
    if (incoming.method === "PATCH" && incoming.url === "/api/v1/users/me"
        && response.statusCode === 200 && existsSync(marker)) {
      response.resume();
      response.on("end", () => {
        appendFileSync(join(dir, "dropped-profile-responses"), "committed\n");
        outgoing.destroy();
      });
      return;
    }
    outgoing.writeHead(response.statusCode, response.headers);
    response.pipe(outgoing);
  });
  upstream.setTimeout(5000, () => upstream.destroy());
  upstream.on("error", () => {
    if (outgoing.headersSent) outgoing.destroy();
    else outgoing.writeHead(503).end();
  });
  incoming.pipe(upstream);
});
server.listen(3443, "127.0.0.1");

createHttpServer((incoming, outgoing) => {
  if (incoming.headers.host !== "identity.test:3080") {
    outgoing.writeHead(421).end();
    return;
  }
  const cookieNames = (incoming.headers.cookie ?? "").split(";").map(part => part.trim().split("=")[0]);
  const authCookiePresent = cookieNames.includes("__Secure-BASEIELTS-AUTH");
  appendFileSync(join(dir, "cleartext-wire-observations"), authCookiePresent ? "auth-cookie\n" : "no-auth-cookie\n");
  outgoing.writeHead(426, { "Cache-Control": "no-store", "Content-Type": "text/plain; charset=utf-8" })
    .end("HTTPS is required.\n");
}).listen(3080, "127.0.0.1");
