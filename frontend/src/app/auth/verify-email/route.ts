export function GET() {
  return new Response(`<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Verify your email | BASE IELTS</title>
<script src="/verification-bootstrap.js"></script>
<link rel="stylesheet" href="/verification.css">
</head>
<body>
<main>
<h1>Verify your email</h1>
<p id="result" role="status"></p>
<form id="verification" method="post" hidden>
<label>Choose a password <input type="password" name="password" autocomplete="new-password" minlength="15" required></label>
<button type="submit">Verify email</button>
</form>
<p><a href="/account/pending">Request another verification link</a></p>
<p><a href="/account/login">Sign in</a></p>
<noscript>Enable JavaScript and reopen the verification link from your email.</noscript>
</main>
</body>
</html>`, {
    headers: {
      "Cache-Control": "no-store",
      "Content-Type": "text/html; charset=utf-8",
      "Content-Security-Policy": "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'; worker-src 'none'",
      "Referrer-Policy": "no-referrer",
      "X-Content-Type-Options": "nosniff",
    },
  });
}
