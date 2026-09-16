#!/usr/bin/env python3
"""Convert the New Parliament State Duma candidate page to app JSON.

The source page is server-rendered, so the parser intentionally uses only the
Python standard library. It validates that every single-member district from 1
through 225 is present exactly once before writing either artifact.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import urllib.request
from html.parser import HTMLParser
from pathlib import Path
from typing import Any


DEFAULT_URL = "https://elections.newparliament.ru/candidates.html"
EXPECTED_DISTRICTS = set(range(1, 226))
KNOWN_PARTIES = {
    "Зелёные",
    "КПРФ",
    "ЛДПР",
    "НД",
    "Новые люди",
    "Справедливая Россия",
    "Яблоко",
}
DISTRICT_PATTERN = re.compile(r"^(.*?)\s*\(№\s*(\d+)\)\s*$")


class CandidatePageParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.region = ""
        self.candidates: list[dict[str, str]] = []
        self._stack: list[str | None] = []
        self._card: dict[str, str] | None = None
        self._chunks: dict[str, list[str]] = {}

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        classes = set(dict(attrs).get("class", "").split())
        marker: str | None = None
        if "candidates__card" in classes:
            if self._card is not None:
                raise ValueError("Nested candidate cards are not supported")
            self._card = {}
            marker = "card"
        elif "candidates__region__title" in classes:
            marker = "region"
        elif self._card is not None and "search__block__info" in classes:
            marker = "district"
        elif self._card is not None and "search__block__name" in classes:
            marker = "candidate"
        elif self._card is not None and "search__block__tag" in classes:
            marker = "party"
        self._stack.append(marker)
        if marker not in (None, "card"):
            self._chunks[marker] = []

    def handle_startendtag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        self.handle_starttag(tag, attrs)
        self.handle_endtag(tag)

    def handle_data(self, data: str) -> None:
        for marker in reversed(self._stack):
            if marker not in (None, "card"):
                self._chunks[marker].append(data)
                break

    def handle_endtag(self, tag: str) -> None:
        if not self._stack:
            return
        marker = self._stack.pop()
        if marker in ("region", "district", "candidate", "party"):
            value = " ".join("".join(self._chunks.pop(marker)).split())
            if marker == "region":
                self.region = value
            elif self._card is not None:
                self._card[marker] = value
        elif marker == "card":
            if self._card is None:
                raise ValueError("Candidate card ended without a start")
            self._card["region"] = self.region
            self.candidates.append(self._card)
            self._card = None


def load_source(source: str) -> tuple[bytes, str]:
    path = Path(source)
    if path.is_file():
        return path.read_bytes(), DEFAULT_URL
    request = urllib.request.Request(
        source,
        headers={"User-Agent": "YaNaVyborah data parser/1.0"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        final_url = response.geturl()
        if not final_url.startswith("https://"):
            raise ValueError("Source redirected to a non-HTTPS URL")
        return response.read(), final_url


def extract_candidates(source_bytes: bytes) -> list[dict[str, Any]]:
    parser = CandidatePageParser()
    parser.feed(source_bytes.decode("utf-8"))
    parser.close()

    candidates: list[dict[str, Any]] = []
    for card in parser.candidates:
        match = DISTRICT_PATTERN.fullmatch(card.get("district", ""))
        if match is None:
            raise ValueError(f"Unexpected district label: {card.get('district')!r}")
        candidates.append(
            {
                "region": card.get("region", ""),
                "districtNumber": int(match.group(2)),
                "electoralDistrict": match.group(1).removesuffix(" округ").strip(),
                "candidate": card.get("candidate", ""),
                "party": card.get("party", ""),
            }
        )
    validate_candidates(candidates)
    return sorted(candidates, key=lambda item: item["districtNumber"])


def validate_candidates(candidates: list[dict[str, Any]]) -> None:
    numbers = [item["districtNumber"] for item in candidates]
    if set(numbers) != EXPECTED_DISTRICTS or len(numbers) != len(EXPECTED_DISTRICTS):
        missing = sorted(EXPECTED_DISTRICTS - set(numbers))
        duplicates = sorted(number for number in set(numbers) if numbers.count(number) > 1)
        raise ValueError(
            f"Expected districts 1..225 exactly once; missing={missing}, duplicates={duplicates}"
        )
    for item in candidates:
        if not all(
            item[field]
            for field in ("region", "electoralDistrict", "candidate", "party")
        ):
            raise ValueError(f"Incomplete row for district {item['districtNumber']}")
        if item["party"] not in KNOWN_PARTIES:
            raise ValueError(
                f"Unknown party in district {item['districtNumber']}: {item['party']}"
            )


def app_region(source_region: str) -> tuple[str, str | None]:
    if source_region == "Город Москва":
        return "Москва", "Москва"
    if source_region == "Город Санкт-Петербург":
        return "Санкт-Петербург", "Санкт-Петербург"
    if source_region == "Город Севастополь":
        return "Севастополь", "Севастополь"
    return source_region, None


def source_document(
    source_bytes: bytes,
    source_url: str,
    candidates: list[dict[str, Any]],
) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "title": "Кандидаты «Нового парламента» на выборах в Госдуму 2026",
        "sourceUrl": source_url,
        "sourceHtmlSha256": hashlib.sha256(source_bytes).hexdigest(),
        "candidates": candidates,
    }


def app_document(source_url: str, candidates: list[dict[str, Any]]) -> dict[str, Any]:
    recommendations: list[dict[str, Any]] = []
    for item in candidates:
        region, city = app_region(item["region"])
        recommendation: dict[str, Any] = {"region": region}
        if city is not None:
            recommendation["city"] = city
        recommendation.update(
            {
                "district": item["electoralDistrict"],
                "districtNumber": str(item["districtNumber"]),
                "ballotType": "federal_single_mandate",
                "choice": item["candidate"],
                "party": item["party"],
            }
        )
        recommendations.append(recommendation)

    canonical = json.dumps(
        recommendations,
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")
    return {
        "schemaVersion": 1,
        "title": "Новый парламент — Госдума 2026",
        "publisher": "Новый парламент",
        "election": "Выборы в Государственную Думу 2026",
        "publishedAt": "2026-09-16",
        "sourceUrl": source_url,
        "contentSha256": hashlib.sha256(canonical).hexdigest(),
        "recommendations": recommendations,
    }


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "source",
        nargs="?",
        default=DEFAULT_URL,
        help="HTTPS URL or a previously downloaded HTML file",
    )
    parser.add_argument(
        "--source-output",
        type=Path,
        default=Path("data/recommendations/new-parliament-gosduma-2026.source.json"),
    )
    parser.add_argument(
        "--app-output",
        type=Path,
        default=Path("data/recommendations/new-parliament-gosduma-2026.yanavyborah.json"),
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    source_bytes, source_url = load_source(args.source)
    candidates = extract_candidates(source_bytes)
    write_json(args.source_output, source_document(source_bytes, source_url, candidates))
    write_json(args.app_output, app_document(source_url, candidates))
    print(f"Extracted {len(candidates)} district rows")
    print(f"Wrote {args.source_output}")
    print(f"Wrote {args.app_output}")


if __name__ == "__main__":
    main()
