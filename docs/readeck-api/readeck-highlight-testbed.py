#!/usr/bin/env python3
"""
readeck-highlight-testbed.py - create bulk Readeck highlights for stress testing.

This utility walks loaded, non-error article bookmarks, randomly targets every 1-3
candidate bookmarks, and creates 1-3 highlights per target bookmark when that
bookmark does not already have highlights.

Usage:
  python3 readeck-highlight-testbed.py --url https://readeck.example.com --key TOKEN --dry-run --limit 10
  python3 readeck-highlight-testbed.py --url https://readeck.example.com --key TOKEN --limit 200
"""

import argparse
import html
import json
import os
import random
import re
import sys
from dataclasses import dataclass, field
from html.parser import HTMLParser
from typing import Dict, Iterable, List, Optional, Tuple
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


STATE_LOADED = 0
COLORS = ("yellow", "red", "blue", "green")
PARAGRAPH_TAGS = {"p"}
IGNORED_TEXT_TAGS = {"script", "style", "noscript", "template"}
NOTE_TEXTS = (
    "Synthetic note for highlight drawer stress testing.",
    "Generated load-test note. No semantic meaning intended.",
    "Placeholder annotation note created by testbed script.",
    "Meaningless note text for UI density testing.",
)
DEFAULT_USER_AGENT = "curl/8.7.1"


def _colour(code):
    if sys.stdout.isatty() and os.name != "nt":
        return lambda s: f"\033[{code}m{s}\033[0m"
    return lambda s: s


BOLD = _colour("1")
DIM = _colour("2")
GREEN = _colour("32")
YELLOW = _colour("33")
CYAN = _colour("36")
RED = _colour("31")


def make_headers(api_key: str, accept: str = "application/json", user_agent: str = DEFAULT_USER_AGENT) -> Dict[str, str]:
    return {
        "Authorization": f"Bearer {api_key}",
        "Accept": accept,
        "User-Agent": user_agent,
    }


def api_url(base_url: str, path: str) -> str:
    return base_url.rstrip("/") + "/api/" + path.lstrip("/")


def http_request(method: str, base_url: str, api_key: str, path: str, **kwargs) -> Tuple[int, str, bytes]:
    params = kwargs.pop("params", None)
    body = kwargs.pop("json", None)
    accept = kwargs.pop("accept", "application/json")
    user_agent = kwargs.pop("user_agent", DEFAULT_USER_AGENT)
    if kwargs:
        raise ValueError(f"Unsupported request options: {', '.join(kwargs.keys())}")

    url = api_url(base_url, path)
    if params:
        url += "?" + urlencode(params, doseq=True)

    headers = make_headers(api_key, accept=accept, user_agent=user_agent)
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode("utf-8")

    request = Request(url, data=data, headers=headers, method=method)
    try:
        with urlopen(request, timeout=45) as response:
            return response.status, response.headers.get("Content-Type", ""), response.read()
    except HTTPError as error:
        body_bytes = error.read()
        raise ApiError(method, path, error.code, body_bytes.decode("utf-8", errors="replace"))


def request_json(method: str, base_url: str, api_key: str, path: str, **kwargs):
    status_code, _content_type, body = http_request(method, base_url, api_key, path, **kwargs)
    if status_code == 204 or not body.strip():
        return None
    return json.loads(body.decode("utf-8"))


def request_text(base_url: str, api_key: str, path: str, user_agent: str = DEFAULT_USER_AGENT) -> str:
    _status_code, _content_type, body = http_request(
        "GET",
        base_url,
        api_key,
        path,
        accept="text/html",
        user_agent=user_agent,
    )
    return body.decode("utf-8", errors="replace")


class ApiError(Exception):
    def __init__(self, method: str, path: str, status_code: int, body: str):
        excerpt = body.replace("\n", " ")[:300]
        super().__init__(f"{method} {path} -> HTTP {status_code}: {excerpt}")
        self.method = method
        self.path = path
        self.status_code = status_code
        self.body = body


@dataclass
class HtmlNode:
    tag: str
    attrs: Dict[str, str] = field(default_factory=dict)
    parent: Optional["HtmlNode"] = None
    children: List["HtmlNode"] = field(default_factory=list)
    content: List[object] = field(default_factory=list)

    def append_text(self, text: str):
        if text:
            self.content.append(html.unescape(text))

    def append_child(self, child: "HtmlNode"):
        self.children.append(child)
        self.content.append(child)

    def element_children(self) -> List["HtmlNode"]:
        return [child for child in self.children if child.tag]

    def text_content(self) -> str:
        chunks = []
        for item in self.content:
            if isinstance(item, str):
                chunks.append(item)
            elif item.tag.lower() not in IGNORED_TEXT_TAGS:
                chunks.append(item.text_content())
        return "".join(chunks)

    def has_ancestor_tag(self, tag: str) -> bool:
        current = self.parent
        while current is not None:
            if current.tag.lower() == tag:
                return True
            current = current.parent
        return False


class ArticleHtmlParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=False)
        self.root = HtmlNode("__document__")
        self.stack = [self.root]

    def handle_starttag(self, tag, attrs):
        node = HtmlNode(tag=tag.lower(), attrs=dict(attrs), parent=self.stack[-1])
        self.stack[-1].append_child(node)
        self.stack.append(node)

    def handle_startendtag(self, tag, attrs):
        node = HtmlNode(tag=tag.lower(), attrs=dict(attrs), parent=self.stack[-1])
        self.stack[-1].append_child(node)

    def handle_endtag(self, tag):
        tag = tag.lower()
        for index in range(len(self.stack) - 1, 0, -1):
            if self.stack[index].tag == tag:
                del self.stack[index:]
                return

    def handle_data(self, data):
        self.stack[-1].append_text(data)

    def handle_entityref(self, name):
        self.stack[-1].append_text(f"&{name};")

    def handle_charref(self, name):
        self.stack[-1].append_text(f"&#{name};")


@dataclass
class HighlightRange:
    selector: str
    start_offset: int
    end_offset: int
    text: str


@dataclass
class Summary:
    pages_fetched: int = 0
    bookmarks_scanned: int = 0
    candidate_bookmarks: int = 0
    target_bookmarks: int = 0
    skipped_existing: int = 0
    skipped_no_range: int = 0
    dry_run_bookmarks: int = 0
    impacted_bookmarks: int = 0
    highlights_added: int = 0
    notes_added: int = 0
    failures: List[Tuple[str, str]] = field(default_factory=list)


def find_article_root(document: HtmlNode) -> HtmlNode:
    by_id = find_first(document, lambda node: node.attrs.get("id") == "rd-article-content")
    if by_id:
        return by_id
    by_class = find_first(
        document,
        lambda node: "container" in node.attrs.get("class", "").split(),
    )
    return by_class or document


def find_first(node: HtmlNode, predicate) -> Optional[HtmlNode]:
    if node.tag != "__document__" and predicate(node):
        return node
    for child in node.children:
        found = find_first(child, predicate)
        if found:
            return found
    return None


def walk(node: HtmlNode) -> Iterable[HtmlNode]:
    for child in node.children:
        yield child
        yield from walk(child)


def xpath_for_element(node: HtmlNode, root: HtmlNode) -> Optional[str]:
    if node is root:
        return "/"

    segments = []
    current = node
    while current is not None and current is not root:
        if not current.tag or current.tag == "__document__":
            return None
        index = 1
        if current.parent:
            for sibling in current.parent.element_children():
                if sibling is current:
                    break
                if sibling.tag == current.tag:
                    index += 1
        segments.insert(0, f"{current.tag}[{index}]")
        current = current.parent

    if current is not root:
        return None
    return "/" + "/".join(segments)


def parse_article_ranges(article_html: str, rng: random.Random, count: int) -> List[HighlightRange]:
    parser = ArticleHtmlParser()
    parser.feed(article_html)
    root = find_article_root(parser.root)

    candidates = []
    for node in walk(root):
        if node.tag not in PARAGRAPH_TAGS:
            continue
        if node.has_ancestor_tag("rd-annotation") or contains_tag(node, "rd-annotation"):
            continue
        text = node.text_content()
        normalized = normalize_visible_text(text)
        if len(normalized) < 20:
            continue
        selector = xpath_for_element(node, root)
        if selector:
            candidates.append((node, text, selector))

    rng.shuffle(candidates)
    ranges: List[HighlightRange] = []
    used_selectors = set()
    for node, text, selector in candidates:
        if selector in used_selectors:
            continue
        highlight_range = range_from_paragraph(text, selector, rng)
        if highlight_range:
            ranges.append(highlight_range)
            used_selectors.add(selector)
        if len(ranges) >= count:
            break
    return ranges


def contains_tag(node: HtmlNode, tag: str) -> bool:
    return any(child.tag == tag or contains_tag(child, tag) for child in node.children)


def normalize_visible_text(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def range_from_paragraph(text: str, selector: str, rng: random.Random) -> Optional[HighlightRange]:
    start_match = re.search(r"\S", text)
    if not start_match:
        return None

    start = start_match.start()
    use_sentence = rng.choice((True, False))
    if use_sentence:
        sentence_match = re.search(r".*?[.!?](?:\s|$)", text[start:], flags=re.S)
        if sentence_match and len(normalize_visible_text(sentence_match.group(0))) <= 260:
            end = start + sentence_match.end()
        else:
            word_match = re.match(r"\S+", text[start:])
            if not word_match:
                return None
            end = start + word_match.end()
    else:
        word_match = re.match(r"\S+", text[start:])
        if not word_match:
            return None
        end = start + word_match.end()

    selected = normalize_visible_text(text[start:end])
    if not selected:
        return None
    return HighlightRange(selector=selector, start_offset=start, end_offset=end, text=selected)


def fetch_candidate_bookmarks(args, summary: Summary) -> List[dict]:
    offset = 0
    candidates = []
    while True:
        params = {
            "limit": args.page_size,
            "offset": offset,
            "type": "article",
            "has_errors": "false",
            "is_loaded": "true",
            "sort": "created",
        }
        batch = request_json(
            "GET",
            args.url,
            args.key,
            "bookmarks",
            params=params,
            user_agent=args.user_agent,
        ) or []
        summary.pages_fetched += 1
        summary.bookmarks_scanned += len(batch)
        batch_candidates = [
            bookmark for bookmark in batch
            if bookmark.get("type") == "article"
            and bookmark.get("state") == STATE_LOADED
            and bookmark.get("loaded") is True
            and not bookmark.get("is_deleted", False)
        ]
        candidates.extend(batch_candidates)
        summary.candidate_bookmarks = len(candidates)
        print(
            f"Fetched page {summary.pages_fetched}: "
            f"{len(batch)} bookmarks, {len(batch_candidates)} candidates "
            f"({len(candidates)} total)"
        )
        if len(batch) < args.page_size:
            break
        offset += args.page_size
    return candidates


def select_target_indexes(total: int, rng: random.Random) -> List[int]:
    indexes = []
    index = rng.randrange(0, 3) if total else 0
    while index < total:
        indexes.append(index)
        index += rng.randint(1, 3)
    return indexes


def should_add_note(highlights_seen: int, next_note_at: int) -> bool:
    return highlights_seen >= next_note_at


def http_excerpt(error: Exception) -> str:
    return str(error).replace("\n", " ")[:300]


def create_annotation(args, bookmark_id: str, highlight_range: HighlightRange, color: str) -> dict:
    body = {
        "start_selector": highlight_range.selector,
        "start_offset": highlight_range.start_offset,
        "end_selector": highlight_range.selector,
        "end_offset": highlight_range.end_offset,
        "color": color,
    }
    return request_json(
        "POST",
        args.url,
        args.key,
        f"bookmarks/{bookmark_id}/annotations",
        json=body,
        user_agent=args.user_agent,
    )


def patch_annotation_note(args, bookmark_id: str, annotation_id: str, color: str, note: str):
    body = {"color": color, "note": note}
    request_json(
        "PATCH",
        args.url,
        args.key,
        f"bookmarks/{bookmark_id}/annotations/{annotation_id}",
        json=body,
        user_agent=args.user_agent,
    )


def run(args) -> Summary:
    rng = random.Random(args.seed)
    summary = Summary()
    candidates = fetch_candidate_bookmarks(args, summary)
    target_indexes = select_target_indexes(len(candidates), rng)

    print()
    print(BOLD("Target selection"))
    print(f"Candidates: {len(candidates)}")
    print(f"Planned targets: {len(target_indexes)}")
    if args.limit:
        print(f"Limit: {args.limit} completed target bookmarks")
    if args.dry_run:
        print(YELLOW("Dry run: no highlights or notes will be created"))
    print()

    completed_targets = 0
    next_note_at = rng.randint(args.note_min, args.note_max)

    for target_number, candidate_index in enumerate(target_indexes, start=1):
        if args.limit and completed_targets >= args.limit:
            break

        bookmark = candidates[candidate_index]
        bookmark_id = bookmark.get("id")
        title = normalize_visible_text(bookmark.get("title") or "(untitled)")
        summary.target_bookmarks += 1

        print(CYAN(f"[{target_number}/{len(target_indexes)}] {bookmark_id} - {title[:90]}"))

        try:
            annotations = request_json(
                "GET",
                args.url,
                args.key,
                f"bookmarks/{bookmark_id}/annotations",
                user_agent=args.user_agent,
            ) or []
            if annotations:
                summary.skipped_existing += 1
                completed_targets += 1
                print(DIM(f"  existing highlights: {len(annotations)}; counted as complete and skipped"))
                continue

            html_text = request_text(
                args.url,
                args.key,
                f"bookmarks/{bookmark_id}/article",
                user_agent=args.user_agent,
            )
            desired_count = rng.randint(1, 3)
            ranges = parse_article_ranges(html_text, rng, desired_count)
            if not ranges:
                summary.skipped_no_range += 1
                print(YELLOW("  no suitable paragraph range found; skipped"))
                continue

            if args.dry_run:
                summary.dry_run_bookmarks += 1
                completed_targets += 1
                for index, highlight_range in enumerate(ranges, start=1):
                    note_preview = " + note" if should_add_note(summary.highlights_added + index, next_note_at) else ""
                    print(
                        f"  would create {index}/{len(ranges)} "
                        f"{highlight_range.selector} {highlight_range.start_offset}:{highlight_range.end_offset}"
                        f"{note_preview} text={highlight_range.text[:80]!r}"
                    )
                continue

            created_for_bookmark = 0
            for highlight_range in ranges:
                color = rng.choice(COLORS)
                created = create_annotation(args, bookmark_id, highlight_range, color)
                annotation_id = (created or {}).get("id")
                summary.highlights_added += 1
                created_for_bookmark += 1
                note_added = False

                if annotation_id and should_add_note(summary.highlights_added, next_note_at):
                    note = rng.choice(NOTE_TEXTS)
                    patch_annotation_note(args, bookmark_id, annotation_id, color, note)
                    summary.notes_added += 1
                    note_added = True
                    next_note_at = summary.highlights_added + rng.randint(args.note_min, args.note_max)

                suffix = f", note={note_added}" if note_added else ""
                print(
                    GREEN(
                        f"  created {created_for_bookmark}/{len(ranges)} id={annotation_id or '?'} "
                        f"color={color}{suffix} text={highlight_range.text[:80]!r}"
                    )
                )

            summary.impacted_bookmarks += 1
            completed_targets += 1

        except Exception as error:
            reason = http_excerpt(error)
            summary.failures.append((bookmark_id or "unknown", reason))
            print(RED(f"  failed: {reason}"))

    return summary


def print_summary(summary: Summary):
    print()
    print(BOLD("Summary"))
    print(f"Pages fetched:              {summary.pages_fetched}")
    print(f"Bookmarks scanned:          {summary.bookmarks_scanned}")
    print(f"Candidate article bookmarks:{summary.candidate_bookmarks:>9}")
    print(f"Target bookmarks attempted: {summary.target_bookmarks}")
    print(f"Skipped existing highlights:{summary.skipped_existing:>9}")
    print(f"Skipped no usable range:    {summary.skipped_no_range}")
    print(f"Dry-run bookmark updates:   {summary.dry_run_bookmarks}")
    print(f"Bookmarks impacted:         {summary.impacted_bookmarks}")
    print(f"Highlights added:           {summary.highlights_added}")
    print(f"Notes added:                {summary.notes_added}")
    print(f"Failures:                   {len(summary.failures)}")
    if summary.failures:
        print()
        print(BOLD("Failures"))
        for bookmark_id, reason in summary.failures[:25]:
            print(f"- {bookmark_id}: {reason}")
        if len(summary.failures) > 25:
            print(f"... and {len(summary.failures) - 25} more")


def parse_note_frequency(value: str) -> Tuple[int, int]:
    parts = [part.strip() for part in value.split(",") if part.strip()]
    if len(parts) == 1:
        low = high = int(parts[0])
    elif len(parts) == 2:
        low, high = int(parts[0]), int(parts[1])
    else:
        raise argparse.ArgumentTypeError("expected N or MIN,MAX")
    if low <= 0 or high <= 0:
        raise argparse.ArgumentTypeError("note frequency must be positive")
    if low > high:
        raise argparse.ArgumentTypeError("note frequency min must be <= max")
    return low, high


def build_parser():
    parser = argparse.ArgumentParser(
        description="Create bulk Readeck highlights for Highlights drawer stress testing.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("--url", required=True, help="Readeck server base URL, e.g. https://readeck.example.com")
    parser.add_argument("--key", required=True, help="Readeck API key / bearer token")
    parser.add_argument("--dry-run", action="store_true", help="Plan and print actions without mutating Readeck")
    parser.add_argument("--limit", type=int, help="Stop after N completed target bookmarks")
    parser.add_argument("--page-size", type=int, default=100, help="Bookmark page size (default: 100)")
    parser.add_argument("--seed", type=int, help="Random seed for repeatable target/range selection")
    parser.add_argument(
        "--user-agent",
        default=DEFAULT_USER_AGENT,
        help=f"HTTP User-Agent header (default: {DEFAULT_USER_AGENT})",
    )
    parser.add_argument(
        "--note-frequency",
        type=parse_note_frequency,
        default=(20, 50),
        metavar="MIN,MAX",
        help="Add one note every random MIN..MAX highlights (default: 20,50)",
    )
    return parser


def main():
    parser = build_parser()
    args = parser.parse_args()
    args.note_min, args.note_max = args.note_frequency

    if args.limit is not None and args.limit <= 0:
        parser.error("--limit must be positive")
    if args.page_size <= 0:
        parser.error("--page-size must be positive")

    print(BOLD("Readeck highlight testbed"))
    print(f"Server: {args.url}")
    print(f"Page size: {args.page_size}")
    print(f"Note frequency: every {args.note_min}..{args.note_max} highlights")
    print(f"User-Agent: {args.user_agent}")
    if args.seed is not None:
        print(f"Seed: {args.seed}")
    print()

    try:
        summary = run(args)
    except KeyboardInterrupt:
        print()
        print(YELLOW("Interrupted. Partial summary follows."))
        summary = Summary()
    except Exception as error:
        print(RED(f"Fatal error: {error}"), file=sys.stderr)
        return 1

    print_summary(summary)
    return 0 if not summary.failures else 2


if __name__ == "__main__":
    sys.exit(main())
