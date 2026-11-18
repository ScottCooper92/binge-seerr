#!/usr/bin/env python3
"""Seeds a test server's Discover sliders so the app's hubs have a row of every kind to show.

Run by hand against a test server, never from CI. It needs two environment variables:

    SEERR_URL      the server's address, for example http://192.168.1.20:5055
    SEERR_API_KEY  an administrator's API key

    tools/seed-discover-sliders.py             set the sliders up and list them
    tools/seed-discover-sliders.py --dry-run   read, print what it would write, write nothing
    tools/seed-discover-sliders.py --clean     remove the sliders this script made

What it does:

  * switches every built-in slider on, leaving the server's order alone;
  * adds one custom slider of each kind the hubs map (keyword, genre, studio, network, streaming and search, for movies
    and TV where the kind has both), each titled "Seed: ..." so a second run updates them and does not add copies.

It never deletes a slider it did not make, never resets the list, and touches nothing but Discover settings. The
data each slider holds is looked up on the server by name, so it holds ids TMDB really has. SEED_REGION (default GB)
picks the streaming region. Standard library only.
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

PREFIX = "Seed: "
DEFAULT_REGION = "GB"
TIMEOUT_SECONDS = 30

# Netflix's TMDB network id. TMDB has no network search, so this is the one id that is not looked up by name.
NETWORK_ID = 213

# The server's DiscoverSliderType numbers, as the app reads them (DiscoverSliderModels.kt).
KINDS = {
    1: "Recently added",
    2: "Recent requests",
    3: "Watchlist",
    4: "Trending",
    5: "Popular movies",
    6: "Movie genres",
    7: "Upcoming movies",
    8: "Studios",
    9: "Popular TV",
    10: "TV genres",
    11: "Upcoming TV",
    12: "Networks",
    13: "Movie keyword",
    14: "TV keyword",
    15: "Movie genre",
    16: "TV genre",
    17: "Studio",
    18: "Network",
    19: "Search",
    20: "Movie streaming",
    21: "TV streaming",
}

# Which hub a kind shows on (HubRows.kt): a kind for both hubs is narrowed to the one asking.
BOTH = {1, 2, 3, 4, 19}
MOVIES = {5, 6, 7, 8, 13, 15, 17, 20}
SERIES = {9, 10, 11, 12, 14, 16, 18, 21}


def hubs(kind):
    if kind in BOTH:
        return "Movies, Series"
    if kind in MOVIES:
        return "Movies"
    if kind in SERIES:
        return "Series"
    return "none (unknown kind)"


class Failure(Exception):
    """A request the server refused or never answered."""


class Server:
    def __init__(self, base, key, dry_run):
        self.base = base.rstrip("/")
        self.key = key
        self.dry_run = dry_run

    def call(self, method, path, body=None, query=None):
        url = self.base + "/api/v1/" + path
        if query:
            url += "?" + urllib.parse.urlencode(query)
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(url, data=data, method=method)
        request.add_header("X-Api-Key", self.key)
        request.add_header("Accept", "application/json")
        if data is not None:
            request.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
                raw = response.read()
        except urllib.error.HTTPError as error:
            raise Failure(f"{method} {path} answered {error.code}") from None
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            raise Failure(f"{method} {path} could not reach the server: {error}") from None
        return json.loads(raw) if raw else None

    def read(self, path, query=None):
        return self.call("GET", path, query=query)

    def write(self, method, path, body):
        """A write, or in a dry run the line that says what it would have been."""
        if self.dry_run:
            print(f"  [dry run] {method} {path} {json.dumps(body) if body is not None else ''}".rstrip())
            return None
        return self.call(method, path, body=body)


def pick(results, wanted):
    """The result named [wanted], ignoring case, or the first one; None when there are none."""
    for result in results:
        if (result.get("name") or "").lower() == wanted.lower():
            return result
    return results[0] if results else None


def keyword(server, name):
    found = pick((server.read("search/keyword", {"query": name}) or {}).get("results") or [], name)
    return (str(found["id"]), found["name"]) if found else None


def company(server, name):
    found = pick((server.read("search/company", {"query": name}) or {}).get("results") or [], name)
    return (str(found["id"]), found["name"]) if found else None


def genre(server, media, name):
    found = pick([g for g in (server.read(f"genres/{media}", {"language": "en"}) or []) if g.get("name", "").lower() == name.lower()], name)
    return (str(found["id"]), found["name"]) if found else None


def network(server, network_id):
    try:
        named = server.read(f"network/{network_id}") or {}
    except Failure:
        named = {}
    return (str(network_id), named.get("name") or f"network {network_id}")


def streaming(server, media, region, wanted):
    """The region and the ids of the first providers in [wanted] the region lists, as the web client stores them."""
    listed = server.read(f"watchproviders/{media}", {"watchRegion": region}) or []
    by_name = {(p.get("name") or "").lower(): p for p in listed}
    chosen = [by_name[name.lower()] for name in wanted if name.lower() in by_name]
    if not chosen:
        return None
    return (f"{region}," + "|".join(str(p["id"]) for p in chosen), ", ".join(p["name"] for p in chosen) + f" in {region}")


def plan(server, region):
    """Each custom slider to set: (kind, title, lookup). A lookup answers (data, what it resolved to) or None."""
    return [
        (13, "Movie keyword: superhero", lambda: keyword(server, "superhero")),
        (14, "TV keyword: anime", lambda: keyword(server, "anime")),
        (15, "Movie genre: Science Fiction", lambda: genre(server, "movie", "Science Fiction")),
        (16, "TV genre: Sci-Fi & Fantasy", lambda: genre(server, "tv", "Sci-Fi & Fantasy")),
        (17, "Studio: Pixar", lambda: company(server, "Pixar")),
        (18, "Network: Netflix", lambda: network(server, NETWORK_ID)),
        (19, "Search: heist", lambda: ("heist", "search text")),
        (20, "Movie streaming", lambda: streaming(server, "movies", region, ["Netflix", "Amazon Prime Video", "Disney Plus"])),
        (21, "TV streaming", lambda: streaming(server, "tv", region, ["Netflix", "Amazon Prime Video", "Disney Plus"])),
    ]


def wire(slider):
    """A slider as the list write takes it: every key, with a built-in's missing title sent as null."""
    body = {
        "type": slider["type"],
        "title": slider.get("title"),
        "isBuiltIn": bool(slider.get("isBuiltIn")),
        "enabled": bool(slider.get("enabled", True)),
        "data": slider.get("data"),
    }
    if slider.get("id") is not None:
        body["id"] = slider["id"]
    return body


def enable_built_ins(server, sliders):
    """Switches every built-in slider on, keeping the list's order and every other slider as it is."""
    off = [s for s in sliders if s.get("isBuiltIn") and not s.get("enabled", True)]
    if not off:
        return 0
    server.write("POST", "settings/discover", [wire({**s, "enabled": True} if s in off else s) for s in sliders])
    return len(off)


def seed(server, region):
    sliders = server.read("settings/discover") or []
    report = {"added": [], "updated": [], "skipped": [], "resolved": {}}
    report["enabled"] = enable_built_ins(server, sliders)
    existing = {s.get("title"): s for s in sliders if not s.get("isBuiltIn") and (s.get("title") or "").startswith(PREFIX)}
    for kind, name, lookup in plan(server, region):
        title = PREFIX + name
        found = lookup()
        if found is None:
            report["skipped"].append((title, "the server's lists have nothing to match it"))
            continue
        data, resolved = found
        report["resolved"][title] = resolved
        body = {"title": title, "type": kind, "data": data}
        if title in existing:
            server.write("PUT", f"settings/discover/{existing[title]['id']}", body)
            report["updated"].append(title)
        else:
            server.write("POST", "settings/discover/add", body)
            report["added"].append(title)
    return report


def clean(server):
    sliders = server.read("settings/discover") or []
    made = [s for s in sliders if not s.get("isBuiltIn") and (s.get("title") or "").startswith(PREFIX)]
    for slider in made:
        server.write("DELETE", f"settings/discover/{slider['id']}", None)
    return [s["title"] for s in made]


def listing(server, resolved):
    print("\nDiscover sliders on the server now:")
    print(f"  {'id':>4}  {'kind':<16} {'on':<3} {'title':<34} {'shows on':<14} data")
    for slider in server.read("settings/discover") or []:
        title = slider.get("title") or "(built in)"
        data = slider.get("data") or ""
        note = resolved.get(slider.get("title") or "")
        shown = f"{data}  ({note})" if note and data else data
        kind = KINDS.get(slider["type"], f"unknown {slider['type']}")
        print(f"  {slider['id']:>4}  {kind:<16} {'yes' if slider.get('enabled') else 'no':<3} {title:<34} {hubs(slider['type']):<14} {shown}")


def main():
    parser = argparse.ArgumentParser(description="Seed a test server's Discover sliders for every kind the hubs map.")
    parser.add_argument("--dry-run", action="store_true", help="read from the server, print what would be written, write nothing")
    parser.add_argument("--clean", action="store_true", help="remove the sliders this script made")
    args = parser.parse_args()

    base = os.environ.get("SEERR_URL", "").strip()
    key = os.environ.get("SEERR_API_KEY", "").strip()
    if not base or not key:
        print("Set SEERR_URL and SEERR_API_KEY (an administrator's API key) and run again.", file=sys.stderr)
        return 2
    region = os.environ.get("SEED_REGION", DEFAULT_REGION).strip().upper() or DEFAULT_REGION
    server = Server(base, key, args.dry_run)

    try:
        if args.clean:
            removed = clean(server)
            print(f"{'Would remove' if args.dry_run else 'Removed'} {len(removed)} seeded slider(s).")
            for title in removed:
                print(f"  {title}")
            print("Built-in sliders that were switched on are left on.")
            return 0
        report = seed(server, region)
        print(f"Built-in sliders {'that would be switched on' if args.dry_run else 'switched on'}: {report['enabled']}")
        for label in ("added", "updated"):
            print(f"{label.capitalize()} {len(report[label])}: " + (", ".join(report[label]) or "none"))
        print(f"Skipped {len(report['skipped'])}:" + ("" if report["skipped"] else " none"))
        for title, why in report["skipped"]:
            print(f"  {title}: {why}")
        if not args.dry_run:
            listing(server, report["resolved"])
        return 0
    except Failure as failure:
        print(f"Stopped: {failure}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
