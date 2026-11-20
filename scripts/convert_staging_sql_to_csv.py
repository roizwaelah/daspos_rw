#!/usr/bin/env python3
"""
Convert migration staging SQL (INSERT statements) into CSV files per table.

Usage:
  python scripts/convert_staging_sql_to_csv.py \
    --input supabase/migration_batch_210426.sql \
    --outdir supabase/csv_210426
"""

import argparse
import csv
import os
import re
from typing import List, Tuple, Any


INSERT_RE = re.compile(
    r"^insert into\s+public\.(\w+)\s*\((.*?)\)\s*values\s*\((.*)\);\s*$",
    re.IGNORECASE,
)


def parse_values(raw: str) -> List[Any]:
    vals: List[Any] = []
    cur: List[str] = []
    in_str = False
    i = 0
    while i < len(raw):
        ch = raw[i]
        if in_str:
            if ch == "'":
                if i + 1 < len(raw) and raw[i + 1] == "'":
                    cur.append("'")
                    i += 2
                    continue
                in_str = False
                i += 1
                continue
            cur.append(ch)
            i += 1
            continue

        if ch == "'":
            in_str = True
            i += 1
            continue
        if ch == ",":
            vals.append(cast_token("".join(cur).strip()))
            cur = []
            i += 1
            continue

        cur.append(ch)
        i += 1

    if cur:
        vals.append(cast_token("".join(cur).strip()))
    return vals


def cast_token(token: str) -> Any:
    if token.lower() == "null":
        return ""
    if token.lower() in ("true", "false"):
        return token.lower()
    try:
        if "." in token:
            return float(token)
        return int(token)
    except ValueError:
        return token


def parse_insert(line: str) -> Tuple[str, List[str], List[Any]]:
    m = INSERT_RE.match(line.strip())
    if not m:
        return "", [], []
    table = m.group(1)
    columns = [c.strip() for c in m.group(2).split(",")]
    values = parse_values(m.group(3).strip())
    if len(columns) != len(values):
        raise ValueError(f"Column/value count mismatch in line: {line[:200]}")
    return table, columns, values


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, help="Input SQL file")
    parser.add_argument("--outdir", required=True, help="Output directory for CSV files")
    args = parser.parse_args()

    os.makedirs(args.outdir, exist_ok=True)

    table_rows = {}
    table_cols = {}

    with open(args.input, "r", encoding="utf-8") as f:
        for line in f:
            table, cols, vals = parse_insert(line)
            if not table:
                continue
            if table not in table_rows:
                table_rows[table] = []
                table_cols[table] = cols
            table_rows[table].append(vals)

    if not table_rows:
        raise ValueError("No INSERT statements found.")

    for table, rows in table_rows.items():
        out_path = os.path.join(args.outdir, f"{table}.csv")
        with open(out_path, "w", newline="", encoding="utf-8") as csv_file:
            writer = csv.writer(csv_file)
            writer.writerow(table_cols[table])
            writer.writerows(rows)
        print(f"OK: {out_path} ({len(rows)} rows)")


if __name__ == "__main__":
    main()

