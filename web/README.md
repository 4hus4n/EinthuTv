# EinthuTv — web version

Same dark poster design as the TV app, for phones, laptops and TV browsers.
Movie lists come from einthusan.tv; **Watch** opens the movie on einthusan.tv in a new tab.

## Install on cPanel
1. Upload everything in this folder to e.g. `public_html/einthutv/` (keep the `assets/` and `cache/` folders).
2. Make sure `cache/` is writable (permission 755 is usually fine).
3. Open `https://yourdomain/einthutv/`.

Needs PHP 7.4+ with the curl and dom extensions (standard on cPanel).

## Files
- `index.html` — the page
- `assets/app.css`, `assets/app.js` — design and logic
- `api.php` — fetches and reads einthusan.tv list pages (only whitelisted lists), caches them 30 min in `cache/`

## If lists don't load
`api.php` shows "Could not reach einthusan.tv from this server" when the host can't fetch the site
(some hosts block outgoing requests, or the site blocks the host). Test by opening
`api.php?find=Year&lang=malayalam&year=2026` in the browser.
