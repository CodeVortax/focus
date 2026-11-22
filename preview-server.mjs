import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const PORT = Number(process.env.APP_PORT || 3000);
const ROOT = process.cwd();

const APK_FILES = {
  '/download/focus-app-debug.apk': [
    path.join(ROOT, 'app/build/outputs/apk/debug/app-debug.apk'),
    path.join(ROOT, '.build-outputs/app-debug.apk'),
  ],
  '/download/focus-study-mate-debug.apk': [
    path.join(ROOT, 'ai/build/outputs/apk/debug/ai-debug.apk'),
    path.join(ROOT, '.build-outputs/ai-debug.apk'),
  ],
};

function findExisting(candidates) {
  for (const p of candidates) {
    try {
      const st = fs.statSync(p);
      if (st.isFile()) return { path: p, size: st.size, mtime: st.mtime };
    } catch {}
  }
  return null;
}

function formatSize(bytes) {
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function renderHtml() {
  const appApk = findExisting(APK_FILES['/download/focus-app-debug.apk']);
  const aiApk = findExisting(APK_FILES['/download/focus-study-mate-debug.apk']);

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Focus — Android Build Preview</title>
  <style>
    :root { color-scheme: dark; }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
      background: #0e1117;
      color: #e6edf3;
      line-height: 1.5;
    }
    main {
      max-width: 720px;
      margin: 0 auto;
      padding: 40px 20px 64px;
    }
    .badge {
      display: inline-block;
      padding: 4px 10px;
      border-radius: 999px;
      background: #1f2937;
      color: #93c5fd;
      font-size: 12px;
      font-weight: 600;
      letter-spacing: .03em;
    }
    h1 { margin: 12px 0 8px; font-size: 28px; font-weight: 700; }
    p.sub { margin: 0 0 24px; color: #9ca3af; }
    .card {
      background: #161b22;
      border: 1px solid #30363d;
      border-radius: 14px;
      padding: 20px;
      margin-bottom: 16px;
    }
    .card h2 { margin: 0 0 10px; font-size: 16px; }
    .btn {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 10px 16px;
      border-radius: 10px;
      background: #2563eb;
      color: #fff;
      text-decoration: none;
      font-weight: 600;
      font-size: 14px;
      margin-right: 10px;
      margin-top: 6px;
    }
    .btn.secondary {
      background: #21262d;
      border: 1px solid #30363d;
      color: #e6edf3;
    }
    .muted { color: #8b949e; font-size: 13px; }
    pre {
      background: #0d1117;
      border: 1px solid #30363d;
      border-radius: 10px;
      padding: 12px 14px;
      overflow-x: auto;
      font-size: 13px;
      color: #c9d1d9;
      margin: 8px 0 0;
    }
    ul { margin: 8px 0 0; padding-left: 18px; color: #cbd5e1; font-size: 14px; }
    li { margin: 6px 0; }
  </style>
</head>
<body>
  <main>
    <span class="badge">ANDROID APK · COMPILE SDK 34 · MIN SDK 26</span>
    <h1>Focus Study Gate</h1>
    <p class="sub">Native Android Kotlin &amp; Jetpack Compose project (<code>:app</code> + <code>:ai</code>). Install the APK on an Android device or build directly with Gradle.</p>

    <div class="card">
      <h2>Compiled APKs</h2>
      <div>
        ${appApk
          ? `<a class="btn" href="/download/focus-app-debug.apk" download>Download Focus APK (${formatSize(appApk.size)})</a>`
          : `<span class="muted">Focus debug APK not built yet — run <code>gradle assembleDebug</code>.</span>`}
        ${aiApk
          ? `<a class="btn secondary" href="/download/focus-study-mate-debug.apk" download>Download Study Mate APK (${formatSize(aiApk.size)})</a>`
          : ''}
      </div>
    </div>

    <div class="card">
      <h2>Build &amp; Test Commands (Termux / Local)</h2>
      <pre>git pull --rebase
gradle test --console=plain
gradle assembleDebug</pre>
      <p class="muted" style="margin-top:10px;">Install via ADB:</p>
      <pre>adb install -r app/build/outputs/apk/debug/app-debug.apk</pre>
    </div>

    <div class="card">
      <h2>Active Modules</h2>
      <ul>
        <li><strong>:app</strong> — Study planner, treat bank, Shorts &amp; app gate, Home launcher lock, multi-shape Focus-only top pill, live timer notification progress, PDF reader, notes, and offline music player.</li>
        <li><strong>:ai</strong> — Companion Study Mate module.</li>
      </ul>
    </div>
  </main>
</body>
</html>`;
}

const server = http.createServer((req, res) => {
  const urlPath = (req.url || '/').split('?')[0];

  if (urlPath === '/health' || urlPath === '/healthz') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ ok: true }));
    return;
  }

  const apkCandidates = APK_FILES[urlPath];
  if (apkCandidates) {
    const found = findExisting(apkCandidates);
    if (!found) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('APK not built yet. Run gradle assembleDebug first.');
      return;
    }
    const filename = path.basename(urlPath);
    res.writeHead(200, {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Length': String(found.size),
      'Content-Disposition': `attachment; filename="${filename}"`,
    });
    fs.createReadStream(found.path).pipe(res);
    return;
  }

  const html = renderHtml();
  res.writeHead(200, {
    'Content-Type': 'text/html; charset=utf-8',
    'Cache-Control': 'no-cache',
  });
  res.end(html);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Focus Android preview server listening on http://0.0.0.0:${PORT}`);
});
