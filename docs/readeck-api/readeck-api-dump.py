#!/usr/bin/env python3
"""
readeck-api-dump.py — Readeck API endpoint output dumper

Fetches and formats output from Readeck API endpoints for inspection and comparison.
Supports:
  - GET /bookmarks/{id}/article         (legacy article HTML)
  - GET /bookmarks/{id}/annotations     (annotation list)
  - GET /bookmarks/{id}                 (single bookmark JSON)
  - GET /bookmarks/sync?since=          (delta sync IDs)
  - POST /bookmarks/sync                (multipart sync — content packages)

Usage:
  python3 readeck-api-dump.py --url https://your-readeck-server --key YOUR_API_KEY <command> [options]

Commands:
  article     <bookmark-id>                      Dump legacy article HTML
  annotations <bookmark-id>                      Dump annotation list as JSON
  bookmark    <bookmark-id>                       Dump single bookmark JSON
  sync-ids    [--since ISO8601]                   Dump changed IDs from GET sync
  sync        <bookmark-id> [bookmark-id ...]     Dump multipart sync response

Sync command options:
  --json          Request JSON metadata part (with_json=true)
  --html          Request HTML part (with_html=true)
  --resources     Request resource parts (with_resources=true)
  --prefix PREFIX Set resource_prefix (default "." when --resources is set)
  --save DIR      Save resource binary files to DIR instead of summarizing them
  --compare FILE  Compare this run's output against a previous dump (JSON diff)

Output options:
  --out FILE      Write output to FILE instead of stdout
  --raw           Output raw bytes (useful for binary inspection)

Examples:
  # Dump article HTML for a bookmark
  python3 readeck-api-dump.py --url https://readeck.example.com --key abc123 article bm-id-here

  # Dump full content package (JSON + HTML + resources) for two bookmarks
  python3 readeck-api-dump.py --url https://readeck.example.com --key abc123 \\
    sync bm-id-1 bm-id-2 --json --html --resources

  # Dump HTML-only sync, save to file for comparison
  python3 readeck-api-dump.py --url https://readeck.example.com --key abc123 \\
    sync bm-id-1 --html --out /tmp/sync-html.txt

  # Compare two sync dumps (run once before and once after a server change)
  python3 readeck-api-dump.py --url https://readeck.example.com --key abc123 \\
    sync bm-id-1 --html --compare /tmp/sync-html.txt
"""

import argparse
import json
import os
import re
import sys
import difflib
from datetime import datetime
from email import message_from_string
from email.policy import compat32

try:
    import requests
except ImportError:
    print("Error: 'requests' library not found. Install it with: pip install requests", file=sys.stderr)
    sys.exit(1)


# ── ANSI colours (auto-disabled when output is not a tty) ──────────────────────

def _colour(code):
    if sys.stdout.isatty() and os.name != 'nt':
        return lambda s: f"\033[{code}m{s}\033[0m"
    return lambda s: s

BOLD   = _colour("1")
DIM    = _colour("2")
GREEN  = _colour("32")
YELLOW = _colour("33")
CYAN   = _colour("36")
RED    = _colour("31")
MAGENTA = _colour("35")


# ── HTTP helpers ───────────────────────────────────────────────────────────────

def make_headers(api_key: str) -> dict:
    return {
        "Authorization": f"Bearer {api_key}",
        "Accept": "application/json",
    }


def api_get(base_url: str, api_key: str, path: str, params: dict = None, accept: str = None) -> requests.Response:
    headers = make_headers(api_key)
    if accept:
        headers["Accept"] = accept
    url = base_url.rstrip("/") + "/api/" + path.lstrip("/")
    resp = requests.get(url, headers=headers, params=params, timeout=30)
    return resp


def api_post(base_url: str, api_key: str, path: str, body: dict) -> requests.Response:
    headers = make_headers(api_key)
    headers["Content-Type"] = "application/json"
    url = base_url.rstrip("/") + "/api/" + path.lstrip("/")
    resp = requests.post(url, headers=headers, json=body, timeout=60, stream=True)
    return resp


# ── Multipart parser ───────────────────────────────────────────────────────────

class MultipartPart:
    def __init__(self, headers: dict, content_type: str, body_bytes: bytes):
        self.headers = headers          # all part headers, lowercase keys
        self.content_type = content_type
        self.body_bytes = body_bytes

    @property
    def bookmark_id(self): return self.headers.get("bookmark-id", "")
    @property
    def part_type(self): return self.headers.get("type", "")
    @property
    def path(self): return self.headers.get("path", "")
    @property
    def group(self): return self.headers.get("group", "")
    @property
    def filename(self): return self.headers.get("filename", "")
    @property
    def text(self):
        try:
            return self.body_bytes.decode("utf-8")
        except UnicodeDecodeError:
            return None


def parse_multipart(content_type_header: str, body_bytes: bytes) -> list:
    """
    Parse a multipart/mixed response body into a list of MultipartPart objects.

    Handles CRLF and LF line endings. Resource (binary) parts are stored as raw bytes.
    """
    boundary = _extract_boundary(content_type_header)
    if not boundary:
        raise ValueError(f"Cannot extract boundary from Content-Type: {content_type_header!r}")

    dash_boundary = ("--" + boundary).encode()
    close_boundary = ("--" + boundary + "--").encode()

    parts = []
    lines = body_bytes.splitlines(keepends=True)
    i = 0

    # Skip to first boundary
    while i < len(lines) and dash_boundary not in lines[i]:
        i += 1
    i += 1  # skip the boundary line itself

    while i < len(lines):
        # Check for closing boundary
        stripped = lines[i].rstrip(b"\r\n")
        if stripped == close_boundary:
            break
        if stripped == dash_boundary:
            i += 1
            continue

        # Parse headers
        raw_headers = {}
        while i < len(lines):
            line = lines[i].rstrip(b"\r\n")
            i += 1
            if not line:
                break  # blank line ends headers
            if b":" in line:
                k, _, v = line.decode("utf-8", errors="replace").partition(":")
                raw_headers[k.strip().lower()] = v.strip()

        content_type = raw_headers.get("content-type", "application/octet-stream")

        # Collect body bytes until next boundary
        body_lines = []
        while i < len(lines):
            stripped = lines[i].rstrip(b"\r\n")
            if stripped == dash_boundary or stripped == close_boundary:
                break
            body_lines.append(lines[i])
            i += 1

        # Strip the final CRLF that precedes the boundary
        body = b"".join(body_lines)
        if body.endswith(b"\r\n"):
            body = body[:-2]
        elif body.endswith(b"\n"):
            body = body[:-1]

        parts.append(MultipartPart(raw_headers, content_type, body))

        if stripped == close_boundary:
            break

    return parts


def _extract_boundary(content_type: str) -> str:
    for token in content_type.split(";"):
        token = token.strip()
        if token.lower().startswith("boundary="):
            return token[9:].strip('"')
    return ""


# ── Output formatting ──────────────────────────────────────────────────────────

def separator(label: str = "", char: str = "─", width: int = 72):
    if label:
        pad = max(0, width - len(label) - 3)
        print(CYAN(f"── {label} " + char * pad))
    else:
        print(DIM(char * width))


def print_json(obj, indent: int = 2):
    print(json.dumps(obj, indent=indent, ensure_ascii=False))


def print_part_summary(part: MultipartPart, index: int):
    separator(f"Part {index + 1}: type={part.part_type!r}  bookmark={part.bookmark_id!r}")
    meta = {k: v for k, v in part.headers.items() if k not in ("bookmark-id", "type")}
    if meta:
        print(DIM("  Headers: ") + "  ".join(f"{k}={v!r}" for k, v in meta.items()))

    if part.part_type == "json":
        try:
            obj = json.loads(part.body_bytes)
            print(GREEN("  [JSON metadata]"))
            print_json(obj)
        except Exception as e:
            print(RED(f"  [JSON parse error: {e}]"))
            print(part.body_bytes.decode("utf-8", errors="replace"))

    elif part.part_type == "html":
        html = part.text or ""
        lines = html.splitlines()
        rd_ann = [l for l in lines if "rd-annotation" in l]
        img_src = re.findall(r'src=["\']([^"\']+)["\']', html)
        print(GREEN("  [HTML content]"))
        print(f"  Length: {len(html):,} chars, {len(lines)} lines")
        print(f"  <rd-annotation> occurrences: {html.count('<rd-annotation')}")
        print(f"  Bare <rd-annotation> (no attrs): {html.count('<rd-annotation>')}")
        print(f"  Enriched <rd-annotation> (has data-annotation-id-value): "
              f"{html.count('data-annotation-id-value')}")
        print(f"  Image src= references: {len(img_src)}")
        if img_src:
            for src in img_src[:5]:
                print(f"    {YELLOW(src)}")
            if len(img_src) > 5:
                print(f"    ... and {len(img_src) - 5} more")
        print()
        print(BOLD("  HTML preview (first 60 lines):"))
        for line in lines[:60]:
            print("  " + line)
        if len(lines) > 60:
            print(DIM(f"  ... ({len(lines) - 60} more lines)"))

    elif part.part_type == "resource":
        print(GREEN("  [Resource file]"))
        print(f"  Path:     {part.path!r}")
        print(f"  Group:    {part.group!r}")
        print(f"  Filename: {part.filename!r}")
        print(f"  Size:     {len(part.body_bytes):,} bytes")
        print(f"  MIME:     {part.content_type!r}")

    else:
        print(YELLOW(f"  [Unknown part type: {part.part_type!r}]"))
        print(f"  Size: {len(part.body_bytes):,} bytes")
        preview = part.body_bytes[:200].decode("utf-8", errors="replace")
        print(f"  Preview: {preview!r}")


def save_resources(parts: list, save_dir: str, bookmark_id: str):
    os.makedirs(save_dir, exist_ok=True)
    for part in parts:
        if part.part_type == "resource" and part.bookmark_id == bookmark_id:
            filename = part.filename or part.path.replace("/", "_") or "resource"
            dest = os.path.join(save_dir, filename)
            with open(dest, "wb") as f:
                f.write(part.body_bytes)
            print(f"  Saved resource: {dest} ({len(part.body_bytes):,} bytes)")


# ── Commands ───────────────────────────────────────────────────────────────────

def cmd_article(args):
    """Fetch legacy article HTML via GET /bookmarks/{id}/article"""
    print(BOLD(f"\n=== Legacy Article HTML: {args.bookmark_id} ===\n"))
    resp = api_get(args.url, args.key, f"bookmarks/{args.bookmark_id}/article",
                   accept="text/html")
    print(f"HTTP {resp.status_code}  Content-Type: {resp.headers.get('Content-Type', '?')}")
    separator()
    html = resp.text
    lines = html.splitlines()
    rd_ann_bare = html.count("<rd-annotation>")
    rd_ann_enriched = html.count("data-annotation-id-value")
    img_src = re.findall(r'src=["\']([^"\']+)["\']', html)
    print(f"Length: {len(html):,} chars, {len(lines)} lines")
    print(f"<rd-annotation> bare: {rd_ann_bare}  enriched: {rd_ann_enriched}")
    print(f"Image src= references: {len(img_src)}")
    separator("Full HTML")
    print(html)
    return html


def cmd_annotations(args):
    """Fetch annotation list via GET /bookmarks/{id}/annotations"""
    print(BOLD(f"\n=== Annotations: {args.bookmark_id} ===\n"))
    resp = api_get(args.url, args.key, f"bookmarks/{args.bookmark_id}/annotations")
    print(f"HTTP {resp.status_code}")
    separator()
    data = resp.json()
    print(f"Count: {len(data)}")
    print_json(data)
    return json.dumps(data, indent=2)


def cmd_bookmark(args):
    """Fetch single bookmark JSON via GET /bookmarks/{id}"""
    print(BOLD(f"\n=== Bookmark Detail: {args.bookmark_id} ===\n"))
    resp = api_get(args.url, args.key, f"bookmarks/{args.bookmark_id}")
    print(f"HTTP {resp.status_code}")
    separator()
    data = resp.json()
    # Highlight notable fields
    notable = ["id", "title", "type", "state", "has_article", "omit_description",
               "embed_domain", "embed", "updated", "errors"]
    for k in notable:
        if k in data:
            val = data[k]
            if val not in (None, "", [], False):
                print(f"  {BOLD(k)}: {YELLOW(repr(val))}")
    separator("Full JSON")
    print_json(data)
    return json.dumps(data, indent=2)


def cmd_sync_ids(args):
    """Fetch changed IDs via GET /bookmarks/sync?since="""
    params = {}
    if args.since:
        params["since"] = args.since
    print(BOLD(f"\n=== Sync IDs (since={args.since or 'all'}) ===\n"))
    resp = api_get(args.url, args.key, "bookmarks/sync", params=params)
    print(f"HTTP {resp.status_code}")
    separator()
    data = resp.json()
    print(f"Count: {len(data)}")
    print_json(data)
    return json.dumps(data, indent=2)


def cmd_sync(args):
    """Fetch multipart content packages via POST /bookmarks/sync"""
    body = {
        "id": args.bookmark_ids,
        "with_json": args.json_part,
        "with_html": args.html,
        "with_resources": args.resources,
    }
    if args.resources:
        body["resource_prefix"] = args.prefix if args.prefix else "."
    elif args.prefix:
        body["resource_prefix"] = args.prefix

    print(BOLD(f"\n=== Multipart Sync ==="))
    print(f"  IDs:       {args.bookmark_ids}")
    print(f"  with_json: {body['with_json']}")
    print(f"  with_html: {body['with_html']}")
    print(f"  with_resources: {body['with_resources']}")
    if "resource_prefix" in body:
        print(f"  resource_prefix: {body['resource_prefix']!r}")
    print()

    resp = api_post(args.url, args.key, "bookmarks/sync", body)
    print(f"HTTP {resp.status_code}  Content-Type: {resp.headers.get('Content-Type', '?')}")

    if resp.status_code != 200:
        print(RED(f"Error response:"))
        print(resp.text)
        return resp.text

    content_type = resp.headers.get("Content-Type", "")
    raw_body = resp.content

    print(f"Response size: {len(raw_body):,} bytes")
    separator()

    try:
        parts = parse_multipart(content_type, raw_body)
    except ValueError as e:
        print(RED(f"Multipart parse error: {e}"))
        if args.raw:
            sys.stdout.buffer.write(raw_body)
        return str(raw_body[:2000])

    # Group by bookmark
    by_bookmark = {}
    for part in parts:
        by_bookmark.setdefault(part.bookmark_id, []).append(part)

    print(f"Total parts: {len(parts)}")
    print(f"Bookmark IDs in response: {list(by_bookmark.keys())}")

    # Check for missing bookmarks
    for bid in args.bookmark_ids:
        if bid not in by_bookmark:
            print(YELLOW(f"  WARNING: No parts returned for requested bookmark {bid!r}"))

    output_lines = []
    for idx, part in enumerate(parts):
        print_part_summary(part, idx)
        if part.part_type == "html" and part.text:
            output_lines.append(f"=== PART {idx+1}: html  bookmark={part.bookmark_id} ===")
            output_lines.append(part.text)
        elif part.part_type == "json":
            output_lines.append(f"=== PART {idx+1}: json  bookmark={part.bookmark_id} ===")
            output_lines.append(part.body_bytes.decode("utf-8", errors="replace"))

    # Save resources if requested
    if args.save:
        for bid in args.bookmark_ids:
            save_resources(parts, args.save, bid)

    return "\n".join(output_lines)


# ── Comparison ─────────────────────────────────────────────────────────────────

def compare_output(new_output: str, compare_file: str):
    """Diff new_output against a previous run saved to compare_file"""
    if not os.path.exists(compare_file):
        print(YELLOW(f"\nCompare file not found, saving current output to {compare_file!r}"))
        with open(compare_file, "w", encoding="utf-8") as f:
            f.write(new_output)
        return

    with open(compare_file, "r", encoding="utf-8") as f:
        old_output = f.read()

    old_lines = old_output.splitlines(keepends=True)
    new_lines = new_output.splitlines(keepends=True)

    diff = list(difflib.unified_diff(old_lines, new_lines,
                                     fromfile=f"previous ({compare_file})",
                                     tofile="current run",
                                     lineterm=""))

    separator("Diff against previous run")
    if not diff:
        print(GREEN("No differences found."))
    else:
        print(f"{len(diff)} diff lines")
        for line in diff:
            if line.startswith("+"):
                print(GREEN(line.rstrip()))
            elif line.startswith("-"):
                print(RED(line.rstrip()))
            elif line.startswith("@"):
                print(CYAN(line.rstrip()))
            else:
                print(DIM(line.rstrip()))


# ── Argument parsing ───────────────────────────────────────────────────────────

def build_parser():
    p = argparse.ArgumentParser(
        description="Readeck API output dumper for inspection and comparison",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__
    )
    p.add_argument("--url", required=True, help="Readeck server base URL (e.g. https://readeck.example.com)")
    p.add_argument("--key", required=True, help="Readeck API key (Bearer token)")
    p.add_argument("--out", metavar="FILE", help="Write text output to FILE")
    p.add_argument("--compare", metavar="FILE", help="Compare output against previous dump in FILE")
    p.add_argument("--raw", action="store_true", help="Print raw bytes for binary content")

    sub = p.add_subparsers(dest="command", required=True)

    # article
    art = sub.add_parser("article", help="GET /bookmarks/{id}/article (legacy HTML)")
    art.add_argument("bookmark_id")

    # annotations
    ann = sub.add_parser("annotations", help="GET /bookmarks/{id}/annotations")
    ann.add_argument("bookmark_id")

    # bookmark
    bm = sub.add_parser("bookmark", help="GET /bookmarks/{id}")
    bm.add_argument("bookmark_id")

    # sync-ids
    si = sub.add_parser("sync-ids", help="GET /bookmarks/sync?since= (delta IDs)")
    si.add_argument("--since", metavar="ISO8601", help="Only return IDs updated since this timestamp")

    # sync
    sy = sub.add_parser("sync", help="POST /bookmarks/sync (multipart content packages)")
    sy.add_argument("bookmark_ids", nargs="+", metavar="BOOKMARK_ID")
    sy.add_argument("--json-part", action="store_true", help="Request JSON metadata (with_json=true)")
    # Alias so --json works at the command line even though 'json' is a reserved word
    sy.add_argument("--json", dest="json_part", action="store_true", help=argparse.SUPPRESS)
    sy.add_argument("--html", action="store_true", help="Request HTML (with_html=true)")
    sy.add_argument("--resources", action="store_true", help="Request resource files (with_resources=true)")
    sy.add_argument("--prefix", metavar="PREFIX", default=None,
                    help="resource_prefix value (default '.' when --resources is set)")
    sy.add_argument("--save", metavar="DIR", help="Save resource binary files to DIR")

    return p


def main():
    parser = build_parser()
    args = parser.parse_args()

    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(DIM(f"readeck-api-dump  {timestamp}  server={args.url}"))

    output = None

    if args.command == "article":
        output = cmd_article(args)
    elif args.command == "annotations":
        output = cmd_annotations(args)
    elif args.command == "bookmark":
        output = cmd_bookmark(args)
    elif args.command == "sync-ids":
        output = cmd_sync_ids(args)
    elif args.command == "sync":
        output = cmd_sync(args)

    if output and args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(output)
        print(BOLD(f"\nOutput saved to {args.out!r}"))

    if output and args.compare:
        compare_output(output, args.compare)


if __name__ == "__main__":
    main()
