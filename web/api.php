<?php
/**
 * EinthuTv web — catalog API.
 * Fetches einthusan.tv movie list pages, reads the movie cards and returns JSON.
 * Only einthusan.tv results pages with whitelisted filters can be requested.
 *
 *   api.php?find=Year&lang=malayalam&year=2026&page=1&pages=3   -> movie list
 *   api.php?action=versions&lang=tamil&year=2026&director=ID...  -> same film in other languages
 *   add &debug=1 to a list request to see the raw HTML of the first card
 */

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

const SITE = 'https://einthusan.tv';
const CACHE_DIR = __DIR__ . '/cache';
const CACHE_TTL = 1800;          // seconds a fetched list page is reused
const MAX_PAGES = 4;             // site pages per request
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36';

$LANGS = ['tamil', 'hindi', 'telugu', 'malayalam', 'kannada', 'bengali', 'marathi', 'punjabi'];
$FINDS = ['Year', 'Decade', 'Alphabets', 'Numbers', 'Recent', 'StaffPick', 'RegionalHit', 'ComingSoon', 'Popularity'];
$PTYPES = ['view', 'comment', 'rating'];

function fail($msg, $code = 400) {
    http_response_code($code);
    echo json_encode(['ok' => false, 'error' => $msg]);
    exit;
}

function out(array $data) {
    echo json_encode(['ok' => true] + $data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

if (!is_dir(CACHE_DIR)) @mkdir(CACHE_DIR, 0755, true);

$lang = strtolower($_GET['lang'] ?? 'malayalam');
if (!in_array($lang, $LANGS, true)) fail('Unknown language');

// ---------- Other-language versions of one film ----------

if (($_GET['action'] ?? '') === 'versions') {
    $year = preg_match('/^(19|20)\d{2}$/', $_GET['year'] ?? '') ? $_GET['year'] : '';
    $imdb = preg_match('/^tt\d{5,10}$/', $_GET['imdb'] ?? '') ? $_GET['imdb'] : '';
    $director = preg_match('/^[A-Za-z0-9]{4,20}$/', $_GET['director'] ?? '') ? $_GET['director'] : '';
    $lead = preg_match('/^[A-Za-z0-9]{4,20}$/', $_GET['lead'] ?? '') ? $_GET['lead'] : '';
    $leadRole = preg_match('/^[A-Z_]{0,20}$/', $_GET['leadRole'] ?? '') ? ($_GET['leadRole'] ?? '') : '';
    $self = $_GET['url'] ?? '';

    // Look the film up through its director (or lead actor) in every other language
    $person = $director ?: $lead;
    $role = $director ? 'DIRECTOR' : $leadRole;
    if ($person === '' || ($year === '' && $imdb === '')) out(['versions' => [], 'reason' => 'not enough info']);

    $urls = [];
    foreach ($LANGS as $L) {
        if ($L === $lang) continue;
        $urls[$L] = pageUrl(['find' => 'Cast', 'id' => $person, 'lang' => $L, 'role' => $role], 1);
    }
    $htmls = fetchAll(array_values($urls));

    $versions = [];
    foreach ($urls as $L => $u) {
        $html = $htmls[$u] ?? null;
        if (!$html) continue;
        foreach (parseMovies($html) as $m) {
            if ($m['url'] === $self) continue;
            $match = ($imdb !== '' && $m['imdb'] !== '')
                ? $m['imdb'] === $imdb
                : ($year !== '' && $m['year'] === $year);
            if ($match) {
                $versions[] = ['lang' => $L, 'title' => $m['title'], 'url' => $m['url'], 'poster' => $m['poster']];
                break;
            }
        }
    }
    out(['versions' => $versions]);
}

// ---------- Movie lists ----------

$find = $_GET['find'] ?? 'Year';
if (!in_array($find, $FINDS, true)) fail('Unknown list');

$q = ['find' => $find, 'lang' => $lang];
switch ($find) {
    case 'Year':
        $y = (int)($_GET['year'] ?? date('Y'));
        if ($y < 1930 || $y > (int)date('Y') + 1) fail('Bad year');
        $q['year'] = $y;
        break;
    case 'Decade':
        $d = (int)($_GET['decade'] ?? 2020);
        if ($d < 1930 || $d > 2030 || $d % 10) fail('Bad decade');
        $q['decade'] = $d;
        break;
    case 'Alphabets':
        $a = strtoupper($_GET['alpha'] ?? 'A');
        if (!preg_match('/^[A-Z]$/', $a)) fail('Bad letter');
        $q['alpha'] = $a;
        break;
    case 'Popularity':
        $pt = strtolower($_GET['ptype'] ?? 'view');
        if (!in_array($pt, $PTYPES, true)) fail('Bad type');
        $tp = $_GET['tp'] ?? 'l30d';
        if (!preg_match('/^[A-Za-z0-9-]{2,12}$/', $tp)) fail('Bad period');
        $q['ptype'] = $pt;
        $q['tp'] = $tp;
        break;
}

$page = max(1, min(500, (int)($_GET['page'] ?? 1)));
$pages = max(1, min(MAX_PAGES, (int)($_GET['pages'] ?? 1)));

$urls = [];
for ($p = $page; $p < $page + $pages; $p++) $urls[] = pageUrl($q, $p);

$htmls = fetchAll($urls);

if (!empty($_GET['debug'])) {
    $html = $htmls[$urls[0]] ?? '';
    out(['url' => $urls[0], 'firstCardHtml' => firstCardHtml((string)$html), 'parsed' => array_slice(parseMovies((string)$html), 0, 2)]);
}

$movies = [];
$seen = [];
$failed = 0;
$lastCount = 0;
foreach ($urls as $u) {
    $html = $htmls[$u] ?? null;
    if ($html === null) { $failed++; $lastCount = 0; continue; }
    $list = parseMovies($html);
    $lastCount = count($list);
    foreach ($list as $m) {
        if (isset($seen[$m['url']])) continue;
        $seen[$m['url']] = 1;
        $movies[] = $m;
    }
}

if ($failed === count($urls)) fail('Could not reach einthusan.tv from this server', 502);

out([
    'page' => $page,
    'pages' => $pages,
    'hasMore' => $lastCount > 0,
    'movies' => $movies,
]);

// ---------- Fetch (cached, parallel) ----------

function pageUrl(array $q, int $p): string {
    $q['page'] = $p;
    ksort($q);
    return SITE . '/movie/results/?' . http_build_query($q);
}

function cachePath(string $url): string {
    return CACHE_DIR . '/' . md5($url) . '.html';
}

/** @return array<string, string|null> url => html (null on failure) */
function fetchAll(array $urls): array {
    $out = [];
    $todo = [];
    foreach ($urls as $u) {
        $f = cachePath($u);
        if (is_file($f) && time() - filemtime($f) < CACHE_TTL) {
            $out[$u] = file_get_contents($f);
        } else {
            $todo[] = $u;
        }
    }
    if (!$todo) return $out;

    $mh = curl_multi_init();
    $handles = [];
    foreach ($todo as $u) {
        $ch = curl_init($u);
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_FOLLOWLOCATION => true,
            CURLOPT_MAXREDIRS => 3,
            CURLOPT_CONNECTTIMEOUT => 8,
            CURLOPT_TIMEOUT => 20,
            CURLOPT_ENCODING => '',
            CURLOPT_USERAGENT => UA,
            CURLOPT_HTTPHEADER => ['Accept: text/html', 'Accept-Language: en'],
        ]);
        curl_multi_add_handle($mh, $ch);
        $handles[$u] = $ch;
    }
    do {
        $status = curl_multi_exec($mh, $running);
        if ($running) curl_multi_select($mh, 1.0);
    } while ($running && $status === CURLM_OK);

    foreach ($handles as $u => $ch) {
        $html = curl_multi_getcontent($ch);
        $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        if ($code === 200 && $html && stripos($html, '/movie/') !== false) {
            $out[$u] = $html;
            @file_put_contents(cachePath($u), $html, LOCK_EX);
        } else {
            // Fall back to an old cached copy if there is one
            $f = cachePath($u);
            $out[$u] = is_file($f) ? file_get_contents($f) : null;
        }
        curl_multi_remove_handle($mh, $ch);
        curl_close($ch);
    }
    curl_multi_close($mh);
    return $out;
}

// ---------- Parse ----------

function absUrl(string $u): string {
    $u = trim($u);
    if ($u === '') return '';
    if (str_starts_with($u, '//')) return 'https:' . $u;
    if (str_starts_with($u, '/')) return SITE . $u;
    return $u;
}

function watchKey(string $href): string {
    return strtok(strtok(absUrl($href), '#'), '?');
}

function clean(string $s): string {
    return trim(preg_replace('/\s+/u', ' ', $s));
}

function loadDoc(string $html): array {
    libxml_use_internal_errors(true);
    $doc = new DOMDocument();
    $doc->loadHTML('<?xml encoding="UTF-8">' . $html, LIBXML_NOWARNING | LIBXML_NOERROR);
    libxml_clear_errors();
    return [$doc, new DOMXPath($doc)];
}

/** @return DOMElement[] one element per movie card, keyed by watch URL */
function findCards(DOMXPath $xp): array {
    $distinct = function (DOMNode $node) use ($xp): int {
        $keys = [];
        foreach ($xp->query('.//a[contains(@href,"/movie/watch/")]', $node) as $a) {
            $keys[watchKey($a->getAttribute('href'))] = 1;
        }
        return count($keys);
    };
    $cards = [];
    foreach ($xp->query('//a[contains(@href,"/movie/watch/")]') as $a) {
        $key = watchKey($a->getAttribute('href'));
        if (isset($cards[$key])) continue;
        // Climb to the largest element that holds only this movie
        $card = $a;
        while ($card->parentNode instanceof DOMElement
            && $card->parentNode->nodeName !== 'body'
            && $distinct($card->parentNode) === 1) {
            $card = $card->parentNode;
        }
        $cards[$key] = [$card, $a->getAttribute('href')];
    }
    return $cards;
}

function firstCardHtml(string $html): string {
    if ($html === '') return '';
    [$doc, $xp] = loadDoc($html);
    foreach (findCards($xp) as [$card]) {
        if ($xp->query('.//img', $card)->length) return mb_substr($doc->saveHTML($card), 0, 6000);
    }
    return '';
}

function parseMovies(string $html): array {
    [$doc, $xp] = loadDoc($html);

    $movies = [];
    foreach (findCards($xp) as $key => [$card, $href]) {
        $img = $xp->query('.//img[contains(@src,"moviecovers") or contains(@data-src,"moviecovers") or contains(@src,"default-img")]', $card)->item(0)
            ?: $xp->query('.//img[@src or @data-src]', $card)->item(0);
        if (!$img) continue;

        $poster = $img->getAttribute('data-src') ?: $img->getAttribute('src');

        $title = '';
        $h = $xp->query('.//h3|.//h2|.//h4', $card)->item(0);
        if ($h) $title = clean($h->textContent);
        if ($title === '') {
            foreach ($xp->query('.//a[contains(@href,"/movie/watch/")]', $card) as $x) {
                $t = clean($x->textContent);
                if ($t !== '') { $title = $t; break; }
            }
        }
        if ($title === '') $title = clean($img->getAttribute('alt'));
        if ($title === '') continue;

        // Year: prefer a text node that is only a year, else any 4-digit year in the card
        $year = '';
        foreach ($xp->query('.//text()', $card) as $tn) {
            if (preg_match('/^\s*((?:19|20)\d{2})\s*$/', $tn->nodeValue, $ym)) { $year = $ym[1]; break; }
        }
        if ($year === '' && preg_match('/(?<!\d)((?:19|20)\d{2})(?!\d)/', $card->textContent, $ym)) $year = $ym[1];

        $synopsis = '';
        foreach ($xp->query('.//p', $card) as $p) {
            $t = clean($p->textContent);
            if (mb_strlen($t) > 40 && mb_strlen($t) > mb_strlen($synopsis)) $synopsis = $t;
        }

        // IMDb id and cast/crew ids (used to find the same film in other languages)
        $imdb = '';
        foreach ($xp->query('.//a[contains(@href,"imdb.com/title/")]', $card) as $x) {
            if (preg_match('~/title/(tt\d+)~', $x->getAttribute('href'), $im)) { $imdb = $im[1]; break; }
        }
        $director = '';
        $lead = '';
        $leadRole = '';
        // Cast/crew are shown as photos: .../prof/<personId>-<hash>.jpg with a <label> (Director / Lead / ...)
        foreach ($xp->query('.//img[contains(@src,"/prof/") or contains(@data-src,"/prof/")]', $card) as $pi) {
            $src = $pi->getAttribute('src') ?: $pi->getAttribute('data-src');
            if (!preg_match('~/prof/([A-Za-z0-9]{4,20})-~', $src, $pm)) continue;
            $box = $pi->parentNode instanceof DOMElement && $pi->parentNode->parentNode instanceof DOMElement
                ? $pi->parentNode->parentNode : $pi;
            $lab = $xp->query('.//label', $box)->item(0);
            $label = strtolower(clean($lab ? $lab->textContent : ''));
            if (str_contains($label, 'music')) continue;
            if (str_contains($label, 'director')) { if ($director === '') $director = $pm[1]; }
            elseif ($lead === '') { $lead = $pm[1]; $leadRole = ''; }
        }
        foreach ($xp->query('.//a[contains(@href,"find=Cast")]', $card) as $x) {
            parse_str((string)parse_url(absUrl($x->getAttribute('href')), PHP_URL_QUERY), $cq);
            $id = $cq['id'] ?? '';
            $role = strtoupper($cq['role'] ?? '');
            if (!preg_match('/^[A-Za-z0-9]{4,20}$/', $id)) continue;
            if ($role === 'DIRECTOR' && $director === '') $director = $id;
            elseif ($lead === '' && $role !== 'DIRECTOR' && $role !== 'MUSIC_DIRECTOR') { $lead = $id; $leadRole = $role; }
        }

        $q = parse_url(absUrl($href), PHP_URL_QUERY);
        $movies[] = [
            'title' => $title,
            'url' => $key . ($q ? '?' . $q : ''),
            'poster' => absUrl($poster),
            'year' => $year,
            'synopsis' => $synopsis,
            'subs' => stripos($doc->saveHTML($card), 'subtitle') !== false,
            'imdb' => $imdb,
            'director' => $director,
            'lead' => $lead,
            'leadRole' => $leadRole,
        ];
    }
    return $movies;
}
