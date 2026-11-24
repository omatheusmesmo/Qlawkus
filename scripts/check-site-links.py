#!/usr/bin/env python3
"""Checks the internal links of the built docs site: every href, src and srcset that points inside
the site must resolve to a generated file, and every #fragment to an id on the target page.

Build first, then run (no network, external links are not followed):

    SITE_URL=http://localhost:9999 mvn -f site/pom.xml package quarkus:run -DskipTests
    python3 -I scripts/check-site-links.py site/target/roq http://localhost:9999
"""
import os
import re
import sys
from html.parser import HTMLParser
from urllib.parse import unquote, urljoin, urlparse


class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []
        self.ids = set()

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if a.get("id"):
            self.ids.add(a["id"])
        if tag == "a" and a.get("name"):
            self.ids.add(a["name"])
        for key in ("href", "src"):
            if a.get(key) and tag != "base":
                self.links.append(a[key])
        if a.get("srcset"):
            for part in a["srcset"].split(","):
                if part.strip():
                    self.links.append(part.strip().split()[0])


def main(root, site_url):
    root = os.path.abspath(root)
    prefix = urlparse(site_url).path.rstrip("/")
    pages = {}
    for dirpath, _, files in os.walk(root):
        for f in files:
            if f.endswith(".html"):
                path = os.path.join(dirpath, f)
                p = Page()
                p.feed(open(path, encoding="utf-8").read())
                pages[path] = p

    def locate(url_path):
        rel = unquote(url_path)
        if prefix and rel.startswith(prefix):
            rel = rel[len(prefix):]
        full = os.path.join(root, rel.lstrip("/"))
        if os.path.isdir(full):
            full = os.path.join(full, "index.html")
        return full if os.path.isfile(full) else None

    failures = []
    checked = 0
    for path, page in sorted(pages.items()):
        here = "/" + os.path.relpath(path, root).replace(os.sep, "/")
        here_url = site_url.rstrip("/") + here
        for link in page.links:
            if re.match(r"^(mailto:|tel:|javascript:|data:)", link):
                continue
            absolute = urljoin(here_url, link)
            parsed = urlparse(absolute)
            if parsed.scheme + "://" + parsed.netloc != urlparse(site_url).scheme + "://" + urlparse(site_url).netloc:
                continue
            checked += 1
            target = locate(parsed.path)
            if target is None:
                failures.append(f"{here}: broken link {link}")
                continue
            fragment = parsed.fragment
            if fragment and target in pages and fragment not in pages[target].ids:
                failures.append(f"{here}: missing anchor {link}")
    for f in failures:
        print("FAIL:", f)
    print(f"checked {checked} internal links on {len(pages)} pages, {len(failures)} problems")
    return 1 if failures else 0


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1], sys.argv[2]))
