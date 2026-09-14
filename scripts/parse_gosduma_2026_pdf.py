#!/usr/bin/env python3
"""Convert the GOSDUMA-2026-UMG PDF table to JSON artifacts.

The parser uses the table's fixed column coordinates instead of plain text
extraction. This avoids the misplaced characters produced by pdftotext for
several rows in the signed source PDF.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any, Iterable

import pdfplumber


TITLE = "Список кандидатов Умного голосования на выборах в Госдуму 2026"
PARTY_LIST_INSTRUCTION = (
    "По спискам: за любую партию, которая имеет шанс пройти барьер в 5% "
    "(КПРФ, «Новые люди» или ЛДПР)."
)
PARTY_LIST_CHOICE = "КПРФ, «Новые люди» или ЛДПР"
KNOWN_PARTIES = {
    "Зелёные",
    "КПРФ",
    "ЛДПР",
    "Новые люди",
    "Партия пенсионеров",
    "Справедливая Россия",
    "Яблоко",
}
EXPECTED_ROWS = 214


def group_lines(words: list[dict[str, Any]], tolerance: float = 1.0) -> list[list[dict[str, Any]]]:
    lines: list[list[dict[str, Any]]] = []
    for word in sorted(words, key=lambda item: (item["top"], item["x0"])):
        for line in lines:
            if abs(line[0]["top"] - word["top"]) <= tolerance:
                line.append(word)
                break
        else:
            lines.append([word])
    for line in lines:
        line.sort(key=lambda item: item["x0"])
    return lines


def join_words(words: Iterable[dict[str, Any]]) -> str:
    return " ".join(word["text"] for word in words).strip()


def extract_candidates(pdf_path: Path) -> list[dict[str, Any]]:
    candidates: list[dict[str, Any]] = []
    current_region: str | None = None

    with pdfplumber.open(pdf_path) as pdf:
        for page_number, page in enumerate(pdf.pages, start=1):
            words = page.extract_words(
                x_tolerance=2,
                y_tolerance=2,
                use_text_flow=False,
                keep_blank_chars=False,
            )
            after_header = False
            for line in group_lines(words):
                line_text = join_words(line)
                if line_text == "Округ № Избирательный округ Кандидат Партия":
                    after_header = True
                    continue
                if not after_header:
                    continue

                number_words = [word for word in line if word["x0"] < 130]
                number_text = "".join(word["text"] for word in number_words)
                if number_text.isdigit():
                    if current_region is None:
                        raise ValueError(f"Page {page_number}: candidate row has no region")
                    candidate = {
                        "region": current_region,
                        "districtNumber": int(number_text),
                        "electoralDistrict": join_words(
                            word for word in line if 130 <= word["x0"] < 300
                        ),
                        "candidate": join_words(
                            word for word in line if 300 <= word["x0"] < 610
                        ),
                        "party": join_words(word for word in line if word["x0"] >= 610) or None,
                    }
                    candidates.append(candidate)
                elif line and line[0]["x0"] < 100 and line[0]["top"] > 70:
                    current_region = line_text

    validate_candidates(candidates)
    return candidates


def validate_candidates(candidates: list[dict[str, Any]]) -> None:
    if len(candidates) != EXPECTED_ROWS:
        raise ValueError(f"Expected {EXPECTED_ROWS} candidate rows, extracted {len(candidates)}")

    district_numbers = [item["districtNumber"] for item in candidates]
    if len(district_numbers) != len(set(district_numbers)):
        raise ValueError("The PDF contains duplicate district numbers")
    if not all(1 <= number <= 225 for number in district_numbers):
        raise ValueError("A district number is outside the expected 1..225 range")

    for item in candidates:
        if not item["region"] or not item["electoralDistrict"] or not item["candidate"]:
            raise ValueError(f"Incomplete row for district {item['districtNumber']}")
        if item["party"] is not None and item["party"] not in KNOWN_PARTIES:
            raise ValueError(
                f"Unknown party in district {item['districtNumber']}: {item['party']}"
            )


def source_document(pdf_path: Path, candidates: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "title": TITLE,
        "sourceFile": pdf_path.name,
        "sourcePdfSha256": hashlib.sha256(pdf_path.read_bytes()).hexdigest(),
        "partyListRecommendation": {
            "instruction": PARTY_LIST_INSTRUCTION,
            "parties": ["КПРФ", "Новые люди", "ЛДПР"],
        },
        "candidates": candidates,
    }


def app_region(source_region: str) -> tuple[str, str | None]:
    if source_region == "Город Москва":
        return "Москва", "Москва"
    if source_region == "Город Санкт-Петербург":
        return "Санкт-Петербург", "Санкт-Петербург"
    return source_region, None


def app_document(candidates: list[dict[str, Any]]) -> dict[str, Any]:
    recommendations: list[dict[str, Any]] = [
        {
            "region": "*",
            "ballotType": "federal_party_list",
            "choice": PARTY_LIST_CHOICE,
            "note": PARTY_LIST_INSTRUCTION,
        }
    ]
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
            }
        )
        if item["party"] is not None:
            recommendation["party"] = item["party"]
        recommendations.append(recommendation)

    canonical = json.dumps(
        recommendations,
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")
    return {
        "schemaVersion": 1,
        "title": "Умное голосование — Госдума 2026",
        "election": "Выборы в Государственную Думу 2026",
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
    parser.add_argument("pdf", type=Path, help="Path to GOSDUMA-2026-UMG.pdf")
    parser.add_argument(
        "--source-output",
        type=Path,
        default=Path("data/recommendations/gosduma-2026-umg.source.json"),
        help="Output with the PDF's original table fields",
    )
    parser.add_argument(
        "--app-output",
        type=Path,
        default=Path("data/recommendations/gosduma-2026-umg.yanavyborah.json"),
        help="Output compatible with the YaNaVyborah recommendation importer",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    candidates = extract_candidates(args.pdf)
    write_json(args.source_output, source_document(args.pdf, candidates))
    write_json(args.app_output, app_document(candidates))
    print(f"Extracted {len(candidates)} district rows")
    print(f"Wrote {args.source_output}")
    print(f"Wrote {args.app_output}")


if __name__ == "__main__":
    main()
