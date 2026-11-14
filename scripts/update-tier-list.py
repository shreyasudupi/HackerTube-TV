#!/usr/bin/env python3
"""Regenerates the bundled "Tristan's Tier List" data from Tristan Hume's blog repo.

Source: https://github.com/trishume/trishume.github.com/blob/master/_data/youtube2026.json
(the data behind https://thume.ca/2026/11/01/youtube-tier-list-2026/).

Usage: scripts/update-tier-list.py path/to/youtube2026.json
Only channel names, tiers, channel ids and Tristan's showcase video are kept. His written
notes are left out on purpose (they're his prose; see README credits).
"""
import json
import re
import sys

OUT = "overlay/common/src/main/res/raw/ht_tier_list.json"


def channel_id(url):
    m = re.search(r"youtube\.com/channel/(UC[\w-]{22})", url or "")
    return m.group(1) if m else None


def main(src):
    data = json.load(open(src, encoding="utf-8"))
    tiers = []
    for t in data["tiers"]:
        channels = []
        for c in t.get("channels", []):
            video = c.get("video") or {}
            channels.append({
                "name": c["name"],
                "channelId": channel_id(c.get("url")),
                "videoId": video.get("id") or None,
                "videoTitle": video.get("title") or None,
            })
        if channels:
            tiers.append({"tier": t["tier"], "name": t["name"], "channels": channels})
    out = {
        "source": "https://thume.ca/2026/11/01/youtube-tier-list-2026/",
        "author": "Tristan Hume",
        "tiers": tiers,
    }
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
        f.write("\n")
    total = sum(len(t["channels"]) for t in tiers)
    print(f"wrote {OUT}: {len(tiers)} tiers, {total} channels")


if __name__ == "__main__":
    main(sys.argv[1])
