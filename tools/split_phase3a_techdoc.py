#!/usr/bin/env python3
"""Split docs/Phase3A TECHDOC.md into construction-order sub-documents.

The source document was exported from a chat UI, so its markdown is mangled:

  * markdown punctuation is backslash-escaped  (\\# , \\* , \\_ , \\- , \\. , \\[ , \\+)
  * leading indentation inside code blocks uses the HTML entity ``&#x20;``
  * every newline was doubled (``\\n`` -> ``\\n\\n``)

The doubled newline is exactly invertible (take every other line), and the
escape set is closed, so the whole cleaning pass is mechanical and lossless.
The script asserts that the cleaned text reconstructs the original byte for
byte before it writes anything.

Usage:
    python tools/split_phase3a_techdoc.py            # write docs/phase3a/*.md
    python tools/split_phase3a_techdoc.py --check     # verify only, write nothing

The source document is never modified.
"""

from __future__ import annotations

import argparse
import hashlib
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SRC = REPO / "docs" / "Phase3A TECHDOC.md"
OUT_DIR = REPO / "docs" / "phase3a"

ESCAPED = re.compile(r"\\([" + re.escape("_-. [*#+") + r"])")
HEADING = re.compile(r"^(#{1,6})\s+(.*)$")
NUMBERED = re.compile(r"^(\d+)((?:\.\d+)*)[.、]?\s+(.+)$")

# doc id, file stem, title, step, commit boundary, depends on, feeds, done criteria
DOCS: list[tuple[str, str, str, str, str, str, str, str]] = [
    (
        "00",
        "OVERVIEW",
        "总览与全局契约",
        "前置，Step A–K 全部适用",
        "docs（§72）",
        "——",
        "01–08",
        "无独立代码产出；后续任一册与 §73 不变量冲突时以本册为准",
    ),
    (
        "01",
        "IDENTITY",
        "Identity domain（Step A / B）",
        "Step A 冻结 identity domain：Normalizer / canonical collection / "
        "AlbumKey / TrackKey / Strength；Step B 完整 JVM tests",
        "identity model + tests",
        "00",
        "02 / 03 / 04",
        "§3–§11 规则实现完成，§58 identity 测试矩阵全部通过",
    ),
    (
        "02",
        "SOURCE_MODEL",
        "Domain source / observation / state model（Step C）",
        "Step C 定义 domain observation/state models："
        "ObservedTrackSource / SourceMetadata / states",
        "§72 未单列；建议并入 Room schema 之前的一次提交",
        "00 / 01",
        "03 / 04 / 05",
        "ObservedTrackSource、SourceMetadata 与三类 state 以纯 domain model 冻结，"
        "不含 Room 依赖",
    ),
    (
        "03",
        "ROOM_SCHEMA",
        "Room schema、FK、索引与 DAO（Step D）",
        "Step D 引入 Room，建立 schema、FK、index、DAO 和 migration baseline",
        "Room schema + DAO",
        "00 / 01 / 02",
        "05 / 07",
        "§48 全部实体与关系建成，§49 删除策略与 §50 索引生效，DAO 可读写",
    ),
    (
        "04",
        "RECONCILIATION",
        "Pure reconciliation decision layer（Step E）",
        "Step E 实现 pure reconciliation decision layer",
        "reconciliation core + tests",
        "00 / 01 / 02",
        "05 / 06",
        "§28–§39 决策规则实现完成，§59 / §60 测试矩阵全部通过，且不依赖 Room",
    ),
    (
        "05",
        "RECONCILIATION_TX",
        "Room-backed reconciliation transaction（Step F）",
        "Step F 实现 Room-backed reconciliation transaction",
        "reconciliation core + tests",
        "00 / 03 / 04",
        "06 / 07",
        "§40 的 active metadata 更新完整落在一个 transaction 内，"
        "§61 数据库不变量测试全部通过",
    ),
    (
        "06",
        "PIPELINE_INTEGRATION",
        "扫描管线接入与 logical graph 投影（Step G / H）",
        "Step G 接现有 SAF + Phase 2C metadata pipeline；"
        "Step H 实现 active source projection → logical graph",
        "scanner/metadata integration",
        "00 / 02 / 03 / 04 / 05",
        "07 / 08",
        "§44 pipeline 全链路打通；查询路径只读到由 active source 投影出的 logical graph",
    ),
    (
        "07",
        "ROOT_PERSISTENCE",
        "Root persistence 迁移（Step I）",
        "Step I 迁移 root persistence",
        "root persistence migration",
        "00 / 03",
        "08",
        "§55 三条保证全部满足：root 顺序不丢、SAF 持久权限不重新申请、"
        "tree URI 原样迁移；Room 成为 root 的唯一 source of truth",
    ),
    (
        "08",
        "DEBUG_ACCEPTANCE",
        "Debug inspection UI 与三层验收（Step J / K）",
        "Step J 加入 debug inspection UI；Step K JVM / Room / Find X9 三层验收",
        "debug/acceptance tooling",
        "00–07",
        "——",
        "§62 的 debug 字段可显示、真机场景逐条通过；§63 / §64 两个相反验收均成立",
    ),
]

# section number -> doc id. Every section of the source must appear exactly once.
SECTIONS: dict[str, list[int]] = {
    "00": [1, 2, 53, 54, 56, 57, 65, 66, 67, 68, 69, 70, 71, 72, 73],
    "01": [3, 4, 5, 6, 7, 8, 9, 10, 11, 58],
    "02": [12, 13, 22, 23, 24, 45],
    "03": [14, 15, 16, 17, 18, 19, 20, 21, 25, 48, 49, 50, 51, 52],
    "04": [26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 59, 60],
    "05": [40, 41, 42, 46, 47, 61],
    "06": [43, 44],
    "07": [55],
    "08": [62, 63, 64],
}

PREAMBLE = """本方案是 Phase 3A 的最终设计规格。目标是把后续数据库与 reconciliation 实现所需要的语义全部冻结下来，使 Agent 在下一阶段只按确定规则施工，不再自行决定 Track identity、Album identity、duplicate、删除恢复或数据库关系。本阶段本身仍不进入 Room 代码施工。"""


def die(msg: str) -> None:
    print(f"ERROR: {msg}", file=sys.stderr)
    raise SystemExit(1)


def read_source(path: Path) -> tuple[str, str]:
    """Read raw bytes and normalise line endings. -> (LF text, newline style)"""
    data = path.read_bytes()
    if data.startswith(b"\xef\xbb\xbf"):
        die("source has a UTF-8 BOM")
    try:
        raw = data.decode("utf-8")
    except UnicodeDecodeError as exc:
        die(f"source is not valid UTF-8: {exc}")

    if "\r\n" in raw:
        style = "CRLF"
        raw = raw.replace("\r\n", "\n")
    elif "\r" in raw:
        die("source mixes lone CR with other line endings")
    else:
        style = "LF"
    if "\r" in raw:
        die("source still contains CR after normalisation")
    return raw, style


def clean(raw: str) -> str:
    """Undo the chat-UI export mangling on LF-normalised text."""
    lines = raw.split("\n")
    odd = [(i, l) for i, l in enumerate(lines[1::2]) if l != ""]
    if odd:
        die(f"odd lines are not all empty, cannot de-double: {odd[:5]}")

    text = "\n".join(lines[0::2])
    if text.replace("\n", "\n\n") != raw:
        same = text.replace("\n", "\n\n")
        for i, (a, b) in enumerate(zip(same.split("\n"), raw.split("\n"))):
            if a != b:
                die(f"de-doubling is not invertible; first divergence at line {i + 1}")
        die(f"de-doubling is not invertible; lengths {len(same)} vs {len(raw)}")

    text = ESCAPED.sub(r"\1", text)
    text = text.replace("&#x20;", " ")
    return "\n".join(line.rstrip() for line in text.split("\n"))


def normalise_heading(text: str, is_title: bool) -> str:
    if is_title:
        return f"# {text}"
    m = NUMBERED.match(text)
    if m:
        # "5" -> ##, "5.1" -> ###
        return "#" * (2 + m.group(2).count(".")) + f" {text}"
    # unnumbered sub-heading inside a numbered section, e.g. "Strong 条件"
    return f"### {text}"


def normalise_headings(text: str) -> str:
    """Re-level every heading. The only edit the split is allowed to make."""
    out = []
    for i, line in enumerate(text.split("\n")):
        m = HEADING.match(line)
        out.append(normalise_heading(m.group(2), is_title=(i == 0)) if m else line)
    return "\n".join(out)


def split_sections(text: str) -> tuple[str, str, dict[int, str]]:
    """-> (title, preamble, {section number: body})"""
    lines = text.split("\n")
    heads: list[tuple[int, str, str]] = []  # (line index, raw heading text, normalised)
    for i, line in enumerate(lines):
        m = HEADING.match(line)
        if not m:
            continue
        body = m.group(2)
        heads.append((i, body, normalise_heading(body, is_title=(i == 0))))

    if not heads or heads[0][0] != 0:
        die("first line is not the document title")
    if heads[0][1] != lines[0][2:]:
        die("unexpected title line")

    # a section starts at a numbered heading with no dot part
    starts: list[tuple[int, int]] = []
    for idx, body, _ in heads:
        m = NUMBERED.match(body)
        if m and not m.group(2):
            starts.append((idx, int(m.group(1))))

    numbers = [n for _, n in starts]
    if numbers != list(range(1, len(numbers) + 1)):
        die(f"section numbering is not 1..N in order: {numbers[:10]} ...")

    preamble_end = starts[0][0]
    preamble = "\n".join(lines[1:preamble_end]).strip("\n")
    if preamble.endswith("---"):
        # the separator that precedes the first numbered section is not content
        preamble = preamble[:-3].rstrip()

    sections: dict[int, str] = {}
    for pos, (start, number) in enumerate(starts):
        end = starts[pos + 1][0] if pos + 1 < len(starts) else len(lines)
        chunk = list(lines[start:end])
        # re-level this chunk's headings
        for j, line in enumerate(chunk):
            m = HEADING.match(line)
            if m:
                chunk[j] = normalise_heading(m.group(2), is_title=False)
        body = "\n".join(chunk).rstrip()
        if body.endswith("---"):
            body = body[:-3].rstrip()
        sections[number] = body
    return lines[0][2:], preamble, sections


BODY_MARKER = "<!-- PHASE3A:BODY -->"


def render(doc_id: str, meta: tuple[str, ...], bodies: list[str]) -> str:
    _, stem, title, step, commit, deps, feeds, done = meta
    listed = " ".join(f"§{n}" for n in sorted(SECTIONS[doc_id]))

    head = "\n".join(
        [
            f"# Phase 3A 施工册 {doc_id} · {title}",
            "",
            "> 来源：`docs/Phase3A TECHDOC.md`（原文档保留不动，为唯一原始来源）。",
            "> 本册正文是原文对应章节的逐字内容，只还原了 markdown 转义"
            "（`\\#` → `#`、`&#x20;` → 空格、双倍空行折叠），未改动任何文字。",
            "> 下方「施工导读」仅为导航，按原文 §71/§72 归纳；规范内容一律以正文为准，"
            "两者冲突时以原文为准。",
            "",
            "## 施工导读",
            "",
            "| 项 | 内容 |",
            "| --- | --- |",
            f"| 施工步骤 | {step} |",
            f"| 提交边界 | {commit} |",
            f"| 本册章节 | {listed} |",
            f"| 依赖 | {deps} 册 |",
            f"| 供下游 | {feeds} 册 |",
            f"| 完成判据 | {done} |",
            "",
        ]
    )
    body = "\n\n---\n\n".join(bodies)
    return f"{head}\n{BODY_MARKER}\n\n---\n\n{body}\n"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="verify only, write nothing")
    args = ap.parse_args()

    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    if not SRC.exists():
        die(f"source document not found: {SRC}")

    src_digest = hashlib.sha256(SRC.read_bytes()).hexdigest()
    raw, style = read_source(SRC)
    text = clean(raw)
    title, preamble, sections = split_sections(text)

    covered: dict[int, str] = {}
    for doc_id, numbers in SECTIONS.items():
        for n in numbers:
            if n in covered:
                die(f"§{n} assigned to both {covered[n]} and {doc_id}")
            covered[n] = doc_id
    missing = sorted(set(sections) - set(covered))
    extra = sorted(set(covered) - set(sections))
    if missing or extra:
        die(f"section coverage mismatch; unassigned={missing} unknown={extra}")

    print(f"source           : {SRC.relative_to(REPO)}")
    print(f"line endings     : {style} (sub-documents are written as LF)")
    print(f"title            : {title}")
    print(f"sections         : {len(sections)} (1..{max(sections)}) all assigned")
    print(f"preamble chars   : {len(preamble)}")
    print(f"raw chars        : {len(raw)}  ->  cleaned chars: {len(text)}")
    print(f"escapes removed  : {len(ESCAPED.findall(raw))}")

    if args.check:
        print("\n--check: nothing written")
        return 0

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for meta in DOCS:
        doc_id, stem = meta[0], meta[1]
        bodies = [sections[n] for n in sorted(SECTIONS[doc_id])]
        if doc_id == "00":
            bodies = ["## 原文前言\n\n" + preamble] + bodies
        out = OUT_DIR / f"{doc_id}_{stem}.md"
        out.write_text(render(doc_id, meta, bodies), encoding="utf-8", newline="\n")
        print(
            f"  wrote {out.relative_to(REPO)}  "
            f"({out.stat().st_size} bytes, {len(bodies)} blocks)"
        )

    # ---- lossless verification -------------------------------------------------
    # Rebuild each sub-document's body straight from the untouched source and
    # compare it with what actually landed on disk. Sections that carry internal
    # "---" separators (§5, §8) make block counting useless, so compare text.
    marker = "\n\n---\n\n"
    split_at = f"{BODY_MARKER}\n\n---\n\n"
    # the only permitted difference from the source is the heading re-levelling
    text_norm = normalise_headings(text)

    for n, body in sections.items():
        if body.strip() not in text_norm:
            die(f"§{n} is not a verbatim slice of the cleaned source")
    if preamble.strip() not in text_norm:
        die("preamble is not a verbatim slice of the cleaned source")

    for meta in DOCS:
        doc_id, stem = meta[0], meta[1]
        doc_text = (OUT_DIR / f"{doc_id}_{stem}.md").read_text(encoding="utf-8")
        if doc_text.count(split_at) != 1:
            die(f"{doc_id}: expected exactly one body marker, found "
                f"{doc_text.count(split_at)}")
        body_text = doc_text.split(split_at, 1)[1].rstrip("\n")

        blocks = [sections[n] for n in sorted(SECTIONS[doc_id])]
        if doc_id == "00":
            blocks = ["## 原文前言\n\n" + preamble] + blocks
        expected = "\n\n---\n\n".join(blocks)
        if body_text != expected:
            die(f"{doc_id}: body on disk differs from the source-derived body "
                f"({len(body_text)} vs {len(expected)} chars)")

    chain = "\n\n---\n\n".join(sections[n] for n in range(1, max(sections) + 1))
    if chain not in text_norm:
        die("reassembled chain is not present verbatim in the cleaned source")

    print(
        f"\nverification     : {len(sections)}/{len(sections)} sections match the "
        "source verbatim; every 册 body rebuilt from source == body on disk"
    )
    if hashlib.sha256(SRC.read_bytes()).hexdigest() != src_digest:
        die("the source document changed during the run")
    print(f"source untouched : sha256 {src_digest[:16]}…")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
