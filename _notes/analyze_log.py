#!/usr/bin/env python3
"""
Analyze MyDeck log file for performance issues
"""
import re
from datetime import datetime
from collections import defaultdict

def parse_timestamp(line):
    """Extract timestamp from log line"""
    match = re.match(r'(\d{2}-\d{2} \d{2}:\d{2}:\d{2}:\d{3})', line)
    if match:
        ts_str = match.group(1)
        # Parse the timestamp (01-26 19:54:18:946)
        return datetime.strptime(ts_str, '%m-%d %H:%M:%S:%f')
    return None

def analyze_log(filename):
    """Analyze the log file"""
    with open(filename, 'r') as f:
        lines = f.readlines()

    print(f"Total log lines: {len(lines)}")

    # Get first and last timestamp
    first_ts = parse_timestamp(lines[0])
    last_ts = parse_timestamp(lines[-1])

    if first_ts and last_ts:
        duration = (last_ts - first_ts).total_seconds()
        print(f"Log time span: {duration:.2f} seconds ({duration/60:.2f} minutes)")
        print(f"First timestamp: {first_ts}")
        print(f"Last timestamp: {last_ts}")

    print("\n" + "="*80)

    # Count different types of operations
    operation_counts = defaultdict(int)
    operation_timestamps = defaultdict(list)

    for line in lines:
        ts = parse_timestamp(line)

        if 'LoadArticleWorker' in line and 'Start loading article' in line:
            operation_counts['LoadArticleWorker'] += 1
            if ts:
                operation_timestamps['LoadArticleWorker'].append(ts)

        if 'LoadBookmarksUseCase' in line:
            operation_counts['LoadBookmarksUseCase'] += 1
            if ts:
                operation_timestamps['LoadBookmarksUseCase'].append(ts)

        if 'Update Bookmark' in line or 'updateBookmark' in line:
            operation_counts['UpdateBookmark'] += 1
            if ts:
                operation_timestamps['UpdateBookmark'].append(ts)
                print(f"\nUpdate Bookmark at {ts}: {line.strip()}")

        if 'onClickArchive' in line or 'onToggleArchive' in line:
            operation_counts['ArchiveAction'] += 1
            if ts:
                operation_timestamps['ArchiveAction'].append(ts)
                print(f"\nArchive Action at {ts}: {line.strip()}")

        if 'onClickFavorite' in line or 'onToggleFavorite' in line:
            operation_counts['FavoriteAction'] += 1
            if ts:
                operation_timestamps['FavoriteAction'].append(ts)
                print(f"\nFavorite Action at {ts}: {line.strip()}")

        if 'APP-INFO' in line:
            if ts:
                print(f"\nApp restart at {ts}: {line.strip()}")

    print("\n" + "="*80)
    print("\nOperation counts:")
    for op, count in sorted(operation_counts.items()):
        print(f"  {op}: {count}")

    print("\n" + "="*80)

    # Analyze timing of LoadArticleWorker
    if operation_timestamps['LoadArticleWorker']:
        article_times = operation_timestamps['LoadArticleWorker']
        print(f"\nLoadArticleWorker timing:")
        print(f"  First: {article_times[0]}")
        print(f"  Last: {article_times[-1]}")
        duration = (article_times[-1] - article_times[0]).total_seconds()
        print(f"  Duration: {duration:.2f} seconds")
        print(f"  Rate: {len(article_times)/duration:.2f} articles/second")

    # Find gaps in the log
    print("\n" + "="*80)
    print("\nLooking for significant time gaps...")

    prev_ts = None
    for i, line in enumerate(lines):
        ts = parse_timestamp(line)
        if ts and prev_ts:
            gap = (ts - prev_ts).total_seconds()
            if gap > 10:  # Gaps longer than 10 seconds
                print(f"\nGap of {gap:.2f} seconds between lines {i-1} and {i}:")
                print(f"  Before: {lines[i-1].strip()}")
                print(f"  After: {line.strip()}")
        if ts:
            prev_ts = ts

    # Search for specific patterns around update operations
    print("\n" + "="*80)
    print("\nContext around update operations:")

    for i, line in enumerate(lines):
        if 'Update Bookmark' in line or 'updateBookmark' in line:
            start = max(0, i-5)
            end = min(len(lines), i+10)
            print(f"\nContext around line {i}:")
            for j in range(start, end):
                marker = ">>> " if j == i else "    "
                print(f"{marker}{lines[j].strip()}")

if __name__ == '__main__':
    analyze_log('_notes/MyDeckAppLog.0.txt')
