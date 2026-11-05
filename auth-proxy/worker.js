// Token exchange for Manuscript's "Continue with Notion" sign-in.
// Notion's token endpoint requires the public connection's client secret, which must not ship inside the app.

const NOTION_TOKEN_URL = "https://api.notion.com/v1/oauth/token";
const NOTION_VERSION = "2026-03-11";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/token" && request.method === "POST") return exchange(request, env);
    if (url.pathname === "/callback" && request.method === "GET") return bounce(url, env);
    return new Response("Not found", { status: 404 });
  },
};

async function exchange(request, env) {
  if (!env.NOTION_CLIENT_ID || !env.NOTION_CLIENT_SECRET) {
    return json({ error: "server_error", error_description: "The sign-in service isn't configured." }, 500);
  }
  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "invalid_request" }, 400);
  }

  let payload;
  if (body.grant_type === "authorization_code" && typeof body.code === "string") {
    payload = { grant_type: "authorization_code", code: body.code };
    if (typeof body.redirect_uri === "string") {
      const allowed = (env.ALLOWED_REDIRECT_URIS || "").split(",").map((s) => s.trim()).filter(Boolean);
      if (allowed.length && !allowed.includes(body.redirect_uri)) return json({ error: "invalid_grant", error_description: "Unknown redirect URI." }, 400);
      payload.redirect_uri = body.redirect_uri;
    }
  } else if (body.grant_type === "refresh_token" && typeof body.refresh_token === "string") {
    payload = { grant_type: "refresh_token", refresh_token: body.refresh_token };
  } else {
    return json({ error: "unsupported_grant_type" }, 400);
  }

  const response = await fetch(NOTION_TOKEN_URL, {
    method: "POST",
    headers: {
      Authorization: "Basic " + btoa(`${env.NOTION_CLIENT_ID}:${env.NOTION_CLIENT_SECRET}`),
      "Content-Type": "application/json",
      "Notion-Version": NOTION_VERSION,
    },
    body: JSON.stringify(payload),
  });
  return new Response(await response.text(), {
    status: response.status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

// For connections whose redirect URI must be https: hands Notion's response back to the app.
function bounce(url, env) {
  const target = (env.APP_CALLBACK || "manuscript://oauth/callback") + url.search;
  const href = target.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
  const script = JSON.stringify(target).replace(/</g, "\\u003c");
  const html = `<!doctype html>
<html lang="en">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Back to Manuscript</title>
<body style="margin:0;min-height:100vh;display:grid;place-items:center;font-family:system-ui,sans-serif;background:#fcf8ff;color:#1b1b21">
  <main style="text-align:center;padding:24px">
    <p style="font-size:18px;margin:0 0 20px">Finishing sign-in…</p>
    <a href="${href}" style="display:inline-block;padding:14px 28px;border-radius:999px;background:#5b5bd6;color:#fff;text-decoration:none;font-weight:600">Return to Manuscript</a>
  </main>
  <script>location.replace(${script});</script>
</body>
</html>`;
  return new Response(html, { headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" } });
}

function json(value, status) {
  return new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" } });
}
