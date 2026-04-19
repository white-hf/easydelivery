#!/usr/bin/env python3
import argparse
import csv
import os
import random
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Sequence, Tuple


UNIT_KEYWORDS = (
    "apt", "apartment", "unit", "suite", "ste", "rm", "room", "ph",
    "buzzer", "fl", "floor", "lvl", "level", "entrance", "door", "code",
    "bldg", "building", "locker", "buzz"
)
PROVINCE_CODES = {"NS", "NB", "PE", "NL", "QC", "ON", "MB", "SK", "AB", "BC"}
COUNTRY_TOKENS = {"CA", "CANADA"}
CANADA_POSTAL_RE = re.compile(r"(?i)\b([A-Z]\d[A-Z])\s?(\d[A-Z]\d)\b")
GENERIC_NUMBER_RE = re.compile(r"\b(\d{1,5}[A-Za-z]?)\b")
LEADING_UNIT_HYPHEN_RE = re.compile(r"^\s*(\w{1,6})\s*-\s*(\d{1,5})\b", re.I)
UNIT_PREFIX_RE = re.compile(
    r"^\s*(?:apt|apartment|unit|suite|ste|rm|room|ph|buzzer|fl|floor|lvl|level|entrance|door|code|bldg|building|#)\s*[:#-]?\s*(\w{1,8})\s+(\d{1,5})\b",
    re.I,
)
DOUBLE_NUMBER_PREFIX_RE = re.compile(r"^\s*(\d{1,4})\s+(\d{1,5})\b")
HASH_ONLY_PREFIX_RE = re.compile(r"^\s*#\s*(\w{1,8})\b", re.I)
UNIT_KEYWORD_GLOBAL_RE = re.compile(
    r"(?i)(?:\b(?:apt|apartment|unit|suite|ste|rm|room|ph|buzzer|fl|floor|lvl|level|entrance|door|code|bldg|building|locker|buzz)\s*[:#-]?\s*(\w{1,8}))"
)
TRAILING_UNIT_RE = re.compile(r"(?i)(?:#|no\.?|unit)\s*(\w{1,8})\s*$")
INLINE_HYPHEN_UNIT_RE = re.compile(r"^(\d{1,4})-(\d{1,5})$", re.I)
EMBEDDED_UNIT_TOKEN_RE = re.compile(
    r"(?i)^(?:apt|apartment|unit|suite|ste|rm|room|fl|floor|lvl|level|locker|buzzer|buzz)[:#-]?\s*(\w{1,8})$"
)
HASHED_UNIT_TOKEN_RE = re.compile(r"^#\s*(\w{1,8})$", re.I)
CITY_NAMES = {
    "HALIFAX", "DARTMOUTH", "BEDFORD", "HAMMONDS PLAINS", "SACKVILLE",
    "LOWER SACKVILLE", "MIDDLE SACKVILLE", "UPPER SACKVILLE", "TIMBERLEA",
    "BEECHVILLE", "LAKESIDE", "COLE HARBOUR", "EASTERN PASSAGE",
    "PORTERS LAKE", "LAKE ECHO", "BEAVER BANK", "PROSPECT BAY",
    "BRIDGEWATER", "TRURO", "LUNENBURG", "WINDSOR", "KENTVILLE", "AMHERST",
}


@dataclass
class ParseResult:
    street_number: str
    unit: str
    is_apartment: bool
    source: str
    confidence: str
    notes: str


def load_env(env_path: Path) -> Dict[str, str]:
    values: Dict[str, str] = {}
    for line in env_path.read_text(encoding="utf-8").splitlines():
        raw = line.strip()
        if not raw or raw.startswith("#") or "=" not in raw:
            continue
        key, value = raw.split("=", 1)
        value = value.strip().strip('"').strip("'")
        values[key.strip()] = value
    return values


def mysql_query(mysql_bin: str, env: Dict[str, str], sql: str) -> List[str]:
    cmd = [
        mysql_bin,
        "-u", env["MYSQL_USER"],
        "-D", env["MYSQL_DATABASE"],
        "-N",
        "-B",
        "-e", sql,
    ]
    run_env = os.environ.copy()
    run_env["MYSQL_PWD"] = env["MYSQL_PASSWORD"]
    completed = subprocess.run(
        cmd,
        env=run_env,
        check=True,
        capture_output=True,
        text=True,
    )
    return [line.strip() for line in completed.stdout.splitlines() if line.strip()]


def normalize_address(raw: str) -> str:
    if not raw:
        return ""
    stripped = strip_non_address_prefix(raw)
    stripped = stripped.replace(",", " ")
    stripped = re.sub(r"\s+", " ", stripped).strip()
    return stripped


def strip_non_address_prefix(raw: str) -> str:
    match = re.search(r"\d", raw or "")
    if not match:
        return (raw or "").strip()
    idx = match.start()
    prefix = raw[:idx]
    if not prefix.strip() or " " not in prefix:
        return raw[idx:].strip()
    return raw.strip()


def numeric_hint(token: str) -> int:
    return int(re.sub(r"[A-Za-z]", "", token or "") or "-1")


def first_street_number(text: str) -> str:
    if not text:
        return ""
    match = GENERIC_NUMBER_RE.search(text)
    return match.group(1) if match else ""


def extract_leading_unit(address: str) -> Tuple[str, str, str]:
    for source, regex, unit_group, street_group in (
        ("leading_hyphen", LEADING_UNIT_HYPHEN_RE, 1, 2),
        ("unit_prefix", UNIT_PREFIX_RE, 1, 2),
        ("double_number_prefix", DOUBLE_NUMBER_PREFIX_RE, 1, 2),
    ):
        match = regex.search(address)
        if match:
            return match.group(unit_group), address[match.start(street_group):].strip(), source
    hash_only = HASH_ONLY_PREFIX_RE.search(address)
    if hash_only:
        return hash_only.group(1), address[hash_only.end():].strip(), "hash_prefix"
    return "", address, ""


def current_detect_unit_from_keywords(text: str, street_number: str) -> Tuple[str, str]:
    for match in UNIT_KEYWORD_GLOBAL_RE.finditer(text or ""):
        candidate = match.group(1)
        if candidate and candidate.lower() != street_number.lower():
            return candidate, "keyword_global"

    trailing = TRAILING_UNIT_RE.search(text or "")
    if trailing:
        candidate = trailing.group(1)
        if candidate.lower() != street_number.lower():
            return candidate, "trailing_keyword"

    if not CANADA_POSTAL_RE.search(text or ""):
        hash_tail = re.search(r"(?i)(\d+[A-Za-z]?)\s*$", text or "")
        if hash_tail:
            candidate = hash_tail.group(1)
            if candidate.lower() != street_number.lower():
                return candidate, "numeric_tail"
    return "", ""


def current_heuristic_unit_from_numbers(text: str, street_number: str) -> Tuple[str, str]:
    numbers = GENERIC_NUMBER_RE.findall(text or "")
    if len(numbers) < 2:
        return "", ""
    first, second = numbers[0], numbers[1]
    first_val = numeric_hint(first)
    second_val = numeric_hint(second)

    if street_number:
        if street_number.lower() == second.lower() and first_val > 0 and first_val < second_val and second_val >= 1000:
            return first, "heuristic_numbers"
        if street_number.lower() == first.lower() and second_val > 0 and second_val < first_val:
            return second, "heuristic_numbers"
    elif second_val >= 1000 and first_val > 0 and first_val < second_val:
        return first, "heuristic_numbers"

    return "", ""


def current_fallback_unit_from_tokens(text: str, street_number: str) -> Tuple[str, str]:
    tokens = re.split(r"\s+", text or "")
    for token in tokens:
        hyphen = INLINE_HYPHEN_UNIT_RE.match(token)
        if hyphen:
            candidate = hyphen.group(1)
            if candidate.lower() != street_number.lower():
                return candidate, "inline_hyphen"
    for token in tokens:
        embedded = EMBEDDED_UNIT_TOKEN_RE.match(token)
        if embedded:
            candidate = embedded.group(1)
            if candidate and candidate.lower() != street_number.lower():
                return candidate, "embedded_token"
        hashed = HASHED_UNIT_TOKEN_RE.match(token)
        if hashed:
            candidate = hashed.group(1)
            if candidate and candidate.lower() != street_number.lower():
                return candidate, "hashed_token"
    return "", ""


def parse_current(raw_address: str) -> ParseResult:
    normalized = normalize_address(raw_address)
    unit, working, source = extract_leading_unit(normalized)
    street_number = first_street_number(working) or first_street_number(normalized)

    if not unit:
        unit, source = current_detect_unit_from_keywords(normalized, street_number)
    if not unit:
        unit, source = current_heuristic_unit_from_numbers(normalized, street_number)
    if not unit:
        unit, source = current_fallback_unit_from_tokens(normalized, street_number)

    confidence = "high" if source in {"leading_hyphen", "unit_prefix", "hash_prefix", "keyword_global"} else "low" if source else "none"
    notes = "mirrors current mobile address heuristics"
    return ParseResult(street_number, unit, bool(unit and street_number), source or "none", confidence, notes)


def split_street_line(raw_address: str) -> str:
    text = (raw_address or "").strip()
    if "," in text:
        return text.split(",", 1)[0].strip()

    text = CANADA_POSTAL_RE.sub("", text).strip(", ")
    tokens = [tok for tok in re.split(r"\s+", text) if tok]
    while tokens:
        upper = tokens[-1].upper().strip(",")
        if upper in PROVINCE_CODES or upper in COUNTRY_TOKENS:
            tokens.pop()
            continue
        break
    candidate = " ".join(tokens).strip(", ")
    return candidate or text


def remove_tail_noise(text: str) -> str:
    working = CANADA_POSTAL_RE.sub("", text or "")
    working = re.sub(r"\b(?:CA|CANADA|NS|NB|PE|NL|QC|ON|MB|SK|AB|BC)\b", "", working, flags=re.I)
    working = re.sub(r"\s+", " ", working).strip(" ,")
    return working


def detect_candidate_unit(text: str, street_number: str) -> Tuple[str, str, str, str]:
    unit, remaining, source = extract_leading_unit(text)
    if unit:
        confidence = "medium" if source == "double_number_prefix" else "high"
        return unit, source, confidence, remaining

    keyword = UNIT_KEYWORD_GLOBAL_RE.search(text)
    if keyword:
        candidate = keyword.group(1)
        if candidate and candidate.lower() != street_number.lower():
            return candidate, "keyword_global", "high", text

    trailing = TRAILING_UNIT_RE.search(text)
    if trailing:
        candidate = trailing.group(1)
        if candidate and candidate.lower() != street_number.lower():
            return candidate, "trailing_keyword", "high", text

    tokenized = re.split(r"\s+", text)
    for token in tokenized:
        hyphen = INLINE_HYPHEN_UNIT_RE.match(token)
        if hyphen:
            candidate = hyphen.group(1)
            if candidate.lower() != street_number.lower():
                return candidate, "inline_hyphen", "high", text

    return "", "none", "none", text


def parse_candidate(raw_address: str) -> ParseResult:
    normalized = normalize_address(raw_address)
    street_line = remove_tail_noise(split_street_line(normalized))
    initial_street_number = first_street_number(street_line) or first_street_number(normalized)
    unit, source, confidence, remaining = detect_candidate_unit(street_line, initial_street_number)
    street_number = first_street_number(remaining) or initial_street_number
    notes = "postal code, province, country stripped before unit inference"

    if not unit and street_line and street_number:
        numbers = GENERIC_NUMBER_RE.findall(street_line)
        if len(numbers) >= 2:
            first, second = numbers[0], numbers[1]
            if street_number.lower() == second.lower() and "-" in street_line:
                unit = first
                source = "hyphen_number_hint"
                confidence = "medium"
                notes = "numeric hint allowed only on street line with hyphen structure"

    is_apartment = bool(unit and street_number and confidence in {"high", "medium"})
    return ParseResult(street_number, unit, is_apartment, source, confidence, notes)


def build_bucket_queries(per_bucket: int) -> Dict[str, str]:
    safe_limit = max(per_bucket * 4, per_bucket)
    base = "SELECT DISTINCT raw_address_text FROM address_raw_history WHERE raw_address_text IS NOT NULL AND raw_address_text <> ''"
    return {
        "leading_hyphen": f"""{base} AND raw_address_text REGEXP '^[[:space:]]*[[:alnum:]]{{1,6}}[[:space:]]*-[[:space:]]*[0-9]{{1,5}}' ORDER BY RAND() LIMIT {safe_limit}""",
        "explicit_unit_keyword": f"""{base} AND LOWER(raw_address_text) REGEXP '(^|[^a-z])(apt|apartment|unit|suite|ste|rm|room|#)([^a-z]|$)' ORDER BY RAND() LIMIT {safe_limit}""",
        "postal_with_space": f"""{base} AND raw_address_text REGEXP '[A-Za-z][0-9][A-Za-z][[:space:]]+[0-9][A-Za-z][0-9]' ORDER BY RAND() LIMIT {safe_limit}""",
        "multi_number_no_keyword": f"""{base} AND raw_address_text REGEXP '[0-9].*[0-9]' AND LOWER(raw_address_text) NOT REGEXP '(apt|apartment|unit|suite|ste|rm|room|#)' ORDER BY RAND() LIMIT {safe_limit}""",
        "plain_house_like": f"""{base} AND raw_address_text REGEXP '^[[:space:]]*[0-9]{{1,5}}[A-Za-z]?[[:space:]]+[A-Za-z]' AND LOWER(raw_address_text) NOT REGEXP '(apt|apartment|unit|suite|ste|rm|room|#|-)' ORDER BY RAND() LIMIT {safe_limit}""",
    }


def sample_addresses(mysql_bin: str, env: Dict[str, str], per_bucket: int, seed: int) -> List[Tuple[str, str]]:
    rng = random.Random(seed)
    seen = set()
    sampled: List[Tuple[str, str]] = []
    for bucket, sql in build_bucket_queries(per_bucket).items():
        rows = mysql_query(mysql_bin, env, sql)
        rng.shuffle(rows)
        taken = 0
        for raw in rows:
            if raw in seen:
                continue
            seen.add(raw)
            sampled.append((bucket, raw))
            taken += 1
            if taken >= per_bucket:
                break
    return sampled


def write_rows(output_path: Path, rows: Sequence[Tuple[str, str]]) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", encoding="utf-8", newline="") as fp:
        writer = csv.writer(fp)
        writer.writerow([
            "sample_bucket",
            "raw_address_text",
            "current_street_number",
            "current_unit",
            "current_is_apartment",
            "current_source",
            "candidate_street_number",
            "candidate_unit",
            "candidate_is_apartment",
            "candidate_source",
            "candidate_confidence",
            "candidate_notes",
            "label_address_type",
            "label_correct_unit",
            "label_parse_ok",
            "label_notes",
        ])
        for bucket, raw in rows:
            current = parse_current(raw)
            candidate = parse_candidate(raw)
            writer.writerow([
                bucket,
                raw,
                current.street_number,
                current.unit,
                "apartment" if current.is_apartment else "house",
                current.source,
                candidate.street_number,
                candidate.unit,
                "apartment" if candidate.is_apartment else "house",
                candidate.source,
                candidate.confidence,
                candidate.notes,
                "",
                "",
                "",
                "",
            ])


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export a stratified address annotation batch from local MySQL.")
    parser.add_argument("--env-file", default="/Users/whitetang/Desktop/work/AddressesSystem/.env.local")
    parser.add_argument("--mysql-bin", default="/usr/local/mysql/bin/mysql")
    parser.add_argument("--per-bucket", type=int, default=20)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--output", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    env = load_env(Path(args.env_file))
    rows = sample_addresses(args.mysql_bin, env, args.per_bucket, args.seed)
    write_rows(Path(args.output), rows)
    print(f"exported_rows={len(rows)} output={args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
