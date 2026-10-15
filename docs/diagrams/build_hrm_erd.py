"""Generate a complete Chen-style HRM ERD from Flyway migrations."""

from __future__ import annotations

import html
import math
import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "api/src/main/resources/db/migration"
SCHEMA_DOC = ROOT / "api/docs/database/DATABASE_SCHEMA.md"
OUTPUT = Path(__file__).with_name("hrm-erd.html")


def esc(value: object) -> str:
    return html.escape(str(value), quote=True)


def split_items(body: str) -> list[str]:
    """Split a CREATE TABLE body at top-level commas only."""
    parts, current, depth, quoted = [], [], 0, False
    for char in body:
        if char == "'":
            quoted = not quoted
        elif not quoted and char == "(":
            depth += 1
        elif not quoted and char == ")":
            depth -= 1
        if char == "," and depth == 0 and not quoted:
            parts.append("".join(current).strip())
            current = []
        else:
            current.append(char)
    if current:
        parts.append("".join(current).strip())
    return parts


tables: dict[str, dict] = {}
fks: list[dict] = []
migrations = sorted(MIGRATIONS.glob("V*__*.sql"))
for migration in migrations:
    sql = migration.read_text(encoding="utf-8")
    for match in re.finditer(r"(?im)^CREATE TABLE\s+([a-z_]+)\s*\((.*?)^\);", sql, re.S | re.M):
        table = match.group(1)
        assert table not in tables, table
        items = split_items(re.sub(r"(?m)--[^\n]*", "", match.group(2)))
        columns = []
        table_pks: set[str] = set()
        table_uniques: set[str] = set()
        for item in items:
            pk = re.search(r"\bPRIMARY KEY\s*\(([^)]+)\)", item, re.I)
            uq = re.search(r"\bUNIQUE\s*\(([^)]+)\)", item, re.I)
            if pk:
                table_pks.update(x.strip().lower() for x in pk.group(1).split(","))
            if uq:
                unique_columns = [x.strip().lower() for x in uq.group(1).split(",")]
                if len(unique_columns) == 1:
                    table_uniques.add(unique_columns[0])
        for item in items:
            column_match = re.match(r"^([a-z][a-z0-9_]*)\s+([A-Z]+(?:\([0-9, ]+\))?)", item, re.I)
            if re.match(r"^(CONSTRAINT|PRIMARY|FOREIGN|UNIQUE|CHECK)\b", item, re.I):
                continue
            assert column_match, f"Unparsed column in {table}: {item}"
            name, data_type = column_match.group(1).lower(), column_match.group(2).upper()
            is_pk = bool(re.search(r"\bPRIMARY KEY\b", item, re.I)) or name in table_pks
            is_unique = bool(re.search(r"\bUNIQUE\b", item, re.I)) or name in table_uniques
            is_required = is_pk or bool(re.search(r"\bNOT NULL\b", item, re.I))
            reference = re.search(r"\bREFERENCES\s+([a-z_]+)\s*\(\s*([a-z_]+)\s*\)", item, re.I)
            if reference:
                fks.append({"child": table, "column": name, "parent": reference.group(1).lower(), "parent_column": reference.group(2).lower()})
            columns.append({"name": name, "type": data_type, "pk": is_pk, "unique": is_unique, "required": is_required, "fk": bool(reference)})
        tables[table] = {"migration": migration.name, "columns": columns}
    for statement in re.findall(r"(?is)\bALTER TABLE\s+.*?;", sql):
        alter = re.search(r"(?is)\bALTER TABLE\s+([a-z_]+).*?\bFOREIGN KEY\s*\(\s*([a-z_]+)\s*\).*?\bREFERENCES\s+([a-z_]+)\s*\(\s*([a-z_]+)\s*\)", statement)
        if alter:
            child, column, parent, parent_column = [x.lower() for x in alter.groups()]
            fks.append({"child": child, "column": column, "parent": parent, "parent_column": parent_column})

assert len(tables) == 33, f"Expected 33 Flyway tables, found {len(tables)}"
assert len(fks) == 72, f"Expected 72 physical foreign keys, found {len(fks)}"
for fk in fks:
    assert fk["child"] in tables and fk["parent"] in tables, fk
    assert fk["column"] in {column["name"] for column in tables[fk["child"]]["columns"]}, fk
    for column in tables[fk["child"]]["columns"]:
        if column["name"] == fk["column"]:
            column["fk"] = True
            fk["required"] = column["required"]
            fk["unique"] = column["unique"]
            break

source = SCHEMA_DOC.read_text(encoding="utf-8")
mermaid = re.search(r"```mermaid\s*(erDiagram.*?)```", source, re.S).group(1)
documented: dict[tuple[str, str, str], tuple[str, str]] = {}
for parent, left, right, child, column in re.findall(
    r"^\s*([A-Z_]+)\s+(\|\||\|o)--(o\{|o\|)\s+([A-Z_]+)\s*:\s*(\w+)\s*$", mermaid, re.M
):
    documented[(child.lower(), column.lower(), parent.lower())] = (left, right)
assert len(documented) == 43
physical = {(fk["child"], fk["column"], fk["parent"]) for fk in fks}
assert set(documented).issubset(physical), sorted(set(documented) - physical)
for fk in fks:
    fk["business"] = (fk["child"], fk["column"], fk["parent"]) in documented
    if fk["business"]:
        fk["left_mark"], fk["right_mark"] = documented[(fk["child"], fk["column"], fk["parent"])]
    else:
        fk["left_mark"] = "||" if fk["required"] else "|o"
        fk["right_mark"] = "o|" if fk["unique"] else "o{"

domain_names = {
    "V1": "Cơ cấu tổ chức",
    "V2": "Nhân viên và tài khoản",
    "V3": "Phân quyền",
    "V4": "Phân công và lương cơ bản",
    "V5": "Nghỉ phép",
    "V6": "Chấm công",
    "V7": "Tính lương",
    "V8": "Dữ liệu tham chiếu",
    "V9": "Thông báo",
}


def cardinality(mark: str) -> str:
    return {"||": "1", "|o": "0..1", "o{": "0..N", "o|": "0..1"}[mark]


def overview_svg() -> str:
    """A compact, connected Chen example using the core employment entities."""
    entities = [(40, "employees", 200), (440, "employee_assignments", 220), (860, "organization_units", 200)]
    attrs = [
        (100, 120, 96, 290, "id", True),
        (240, 120, 184, 290, "employee_code", False),
        (100, 520, 96, 342, "full_name", False),
        (240, 520, 184, 342, "hire_date", False),
        (480, 120, 492, 290, "id", True),
        (620, 120, 608, 290, "effective_from", False),
        (480, 520, 492, 342, "effective_to", False),
        (620, 520, 608, 342, "is_primary", False),
        (900, 120, 912, 290, "id", True),
        (1040, 120, 1016, 290, "code", False),
        (970, 520, 968, 342, "name", False),
    ]
    lines = [f'<line x1="{cx}" y1="{cy + (20 if cy < 316 else -20)}" x2="{tx}" y2="{ty}" class="edge"/>' for cx, cy, tx, ty, _, _ in attrs]
    lines += [
        '<line x1="240" y1="316" x2="260" y2="316" class="edge"/>',
        '<line x1="420" y1="316" x2="440" y2="316" class="edge"/>',
        '<line x1="660" y1="316" x2="680" y2="316" class="edge"/>',
        '<line x1="840" y1="316" x2="860" y2="316" class="edge"/>',
    ]
    boxes = [f'<rect x="{x}" y="290" width="{width}" height="52" class="entity"/><text x="{x+width/2}" y="321" class="entity-text" text-anchor="middle">{name}</text>' for x, name, width in entities]
    ovals = [f'''<ellipse cx="{cx}" cy="{cy}" rx="65" ry="20" fill="#fff" stroke="{'#bf4520' if pk else '#4f5d75'}"/>
        <text x="{cx}" y="{cy+4}" class="attribute-name" text-anchor="middle"{' text-decoration="underline"' if pk else ''}>{name}</text>''' for cx, cy, _, _, name, pk in attrs]
    return f'''<section class="panel" id="overview">
      <div class="panel-head"><span class="section-no">00</span><h3>Lõi nhân sự · tổng quan kiểu Chen</h3><span class="panel-count">3 thực thể · 2 quan hệ</span></div>
      <div class="diagram-container"><svg viewBox="0 0 1100 620" role="img" aria-labelledby="hrm-overview-title hrm-overview-desc" xmlns="http://www.w3.org/2000/svg" class="overview-svg">
        <title id="hrm-overview-title">Quan hệ giữa nhân viên, phân công và đơn vị tổ chức</title>
        <desc id="hrm-overview-desc">Thực thể hình chữ nhật, thuộc tính hình oval, hai quan hệ hình thoi nối nhân viên với phân công và phân công với đơn vị tổ chức.</desc>
        <rect width="1100" height="620" fill="#f5f5f5"/>
        {''.join(lines)}
        <polygon points="340,280 420,316 340,352 260,316" class="diamond"/>
        <text x="340" y="320" class="diamond-text" text-anchor="middle">has</text>
        <polygon points="760,280 840,316 760,352 680,316" class="diamond"/>
        <text x="760" y="320" class="diamond-text" text-anchor="middle">assigned to</text>
        {''.join(boxes)}
        {''.join(ovals)}
        <text x="250" y="304" class="cardinality" text-anchor="middle">1</text>
        <text x="430" y="304" class="cardinality" text-anchor="middle">0..N</text>
        <text x="670" y="304" class="cardinality" text-anchor="middle">0..N</text>
        <text x="850" y="304" class="cardinality" text-anchor="middle">1</text>
      </svg></div>
    </section>'''


def relationship_svg(number: int, title: str, links: list[dict]) -> str:
    height = 92 + len(links) * 100
    strokes, nodes = [], []
    for row, fk in enumerate(links):
        y = 54 + row * 100
        dashed = ' stroke-dasharray="5 4"' if not fk["business"] else ""
        strokes.append(f'''<line x1="300" y1="{y}" x2="348" y2="{y}" class="edge"{dashed}/>
          <line x1="612" y1="{y}" x2="660" y2="{y}" class="edge"{dashed}/>''')
        nodes.append(f'''<rect x="40" y="{y-26}" width="260" height="52" class="entity"/>
          <text x="170" y="{y+4}" class="entity-text" text-anchor="middle">{esc(fk['parent'])}</text>
          <polygon points="480,{y-32} 612,{y} 480,{y+32} 348,{y}" class="diamond"/>
          <text x="480" y="{y+4}" class="diamond-text" text-anchor="middle">{esc(fk['column'])}</text>
          <rect x="660" y="{y-26}" width="260" height="52" class="entity"/>
          <text x="790" y="{y+4}" class="entity-text" text-anchor="middle">{esc(fk['child'])}</text>
          <text x="323" y="{y-10}" class="cardinality" text-anchor="middle">{cardinality(fk['left_mark'])}</text>
          <text x="637" y="{y-10}" class="cardinality" text-anchor="middle">{cardinality(fk['right_mark'])}</text>''')
    slug = f"hrm-rel-{number:02d}"
    return f'''<section class="panel" id="rel-{number}">
      <div class="panel-head"><span class="section-no">{number:02d}</span><h3>{esc(title)}</h3><span class="panel-count">{len(links)} quan hệ</span></div>
      <div class="diagram-container"><svg viewBox="0 0 960 {height}" role="img" aria-labelledby="{slug}-title {slug}-desc" xmlns="http://www.w3.org/2000/svg">
        <title id="{slug}-title">{esc(title)}</title>
        <desc id="{slug}-desc">Sơ đồ Chen gồm {len(links)} quan hệ khóa ngoại; thực thể là hình chữ nhật và quan hệ là hình thoi.</desc>
        <rect width="960" height="{height}" fill="#f5f5f5"/>
        {''.join(strokes)}
        {''.join(nodes)}
        <line x1="40" y1="{height-34}" x2="920" y2="{height-34}" class="rule"/>
        <text x="40" y="{height-14}" class="legend">TRÁI: BẢNG ĐƯỢC THAM CHIẾU</text>
        <text x="390" y="{height-14}" class="legend">HÌNH THOI: CỘT FK</text>
        <text x="690" y="{height-14}" class="legend">PHẢI: BẢNG CHỨA FK</text>
      </svg></div>
    </section>'''


by_migration: dict[str, list[dict]] = defaultdict(list)
for fk in fks:
    prefix = tables[fk["child"]]["migration"].split("__", 1)[0]
    by_migration[prefix].append(fk)
relationship_panels = []
relationship_index = []
number = 0
for prefix in sorted(by_migration):
    links = sorted(by_migration[prefix], key=lambda fk: (not fk["business"], fk["child"], fk["column"], fk["parent"]))
    chunks = [links[i:i+4] for i in range(0, len(links), 4)]
    for part, chunk in enumerate(chunks, 1):
        number += 1
        title = domain_names[prefix] + (f" · {part}/{len(chunks)}" if len(chunks) > 1 else "")
        relationship_panels.append(relationship_svg(number, title, chunk))
        relationship_index.append((number, title))
assert sum(len(items) for items in by_migration.values()) == len(fks)


def attribute_svg(table: str, columns: list[dict]) -> str:
    rows = math.ceil(len(columns) / 2)
    height = max(240, 88 + rows * 56)
    center_y = height // 2
    lines, ovals = [], []
    for index, column in enumerate(columns):
        left = index % 2 == 0
        x = 24 if left else 676
        y = 48 + (index // 2) * 56
        oval_cy = y + 20
        start_x = 370 if left else 590
        end_x = 284 if left else 676
        lines.append(f'<line x1="{start_x}" y1="{center_y}" x2="{end_x}" y2="{oval_cy}" class="edge"/>')
        flags = []
        if column["pk"]:
            flags.append("PK")
        if column["fk"]:
            flags.append("FK")
        if column["unique"] and not column["pk"]:
            flags.append("UQ")
        if column["required"] and not column["pk"]:
            flags.append("NN")
        note = column["type"] + (" · " + " · ".join(flags) if flags else "")
        stroke = "#bf4520" if column["pk"] else "#4f5d75"
        dash = ' stroke-dasharray="4 3"' if column["fk"] else ""
        underline = ' text-decoration="underline"' if column["pk"] else ""
        ovals.append(f'''<ellipse cx="{x+130}" cy="{oval_cy}" rx="130" ry="20" fill="#ffffff" stroke="{stroke}" stroke-width="1.1"{dash}/>
          <text x="{x+130}" y="{oval_cy-2}" class="attribute-name" text-anchor="middle"{underline}>{esc(column['name'])}</text>
          <text x="{x+130}" y="{oval_cy+11}" class="attribute-type" text-anchor="middle">{esc(note)}</text>''')
    slug = "hrm-attr-" + table.replace("_", "-")
    return f'''<div class="diagram-container"><svg viewBox="0 0 960 {height}" role="img" aria-labelledby="{slug}-title {slug}-desc" xmlns="http://www.w3.org/2000/svg">
      <title id="{slug}-title">Thuộc tính bảng {esc(table)}</title>
      <desc id="{slug}-desc">Thực thể {esc(table)} với đầy đủ {len(columns)} cột từ Flyway; khóa chính gạch dưới và khóa ngoại có oval nét đứt.</desc>
      <rect width="960" height="{height}" fill="#f5f5f5"/>
      {''.join(lines)}
      <rect x="370" y="{center_y-33}" width="220" height="66" class="entity"/>
      <text x="480" y="{center_y+4}" class="entity-text" text-anchor="middle">{esc(table)}</text>
      {''.join(ovals)}
    </svg></div>'''


table_index = "".join(f'<a href="#table-{esc(name)}">{esc(name)}</a>' for name in sorted(tables))
attribute_panels = "".join(
    f'''<details class="table-panel" id="table-{esc(table)}"{' open' if table == 'employees' else ''}>
      <summary><span>{esc(table)}</span><small>{len(info['columns'])} cột · {esc(info['migration'])}</small></summary>
      {attribute_svg(table, info['columns'])}
    </details>'''
    for table, info in sorted(tables.items())
)
relationship_nav = "".join(f'<a href="#rel-{number}">{number:02d} {esc(title)}</a>' for number, title in relationship_index)
full_migrations = "".join(
    f'<details><summary><span>{esc(migration.name)}</span></summary><pre>{esc(migration.read_text(encoding="utf-8"))}</pre></details>'
    for migration in migrations
)
column_count = sum(len(table["columns"]) for table in tables.values())
document = f'''<!doctype html>
<html lang="vi">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>HRM · Sơ đồ ERD kiểu Chen đầy đủ</title>
  <link href="https://fonts.googleapis.com/css2?family=Instrument+Serif:ital@0;1&family=Geist:wght@400;500;600&family=Geist+Mono:wght@400;500;600&display=swap" rel="stylesheet">
  <style>
    :root {{ --paper:#f5f5f5; --ink:#2d3142; --muted:#4f5d75; --accent:#bf4520; --rule:#bfc0c0; }}
    * {{ box-sizing:border-box; }}
    html {{ scroll-behavior:auto; }}
    body {{ margin:0; color:var(--ink); background:var(--paper); font-family:'Geist',system-ui,sans-serif; }}
    .frame {{ width:min(1200px,100%); margin:auto; padding:46px 24px 80px; }}
    .eyebrow {{ margin:0 0 9px; color:var(--accent); font:500 10px 'Geist Mono',monospace; letter-spacing:.16em; text-transform:uppercase; }}
    h1 {{ margin:0; font:400 clamp(34px,5vw,56px) 'Instrument Serif',serif; line-height:1.08; }}
    .lede {{ max-width:850px; color:var(--muted); font-size:14px; line-height:1.65; margin:15px 0 22px; }}
    .stats {{ display:flex; flex-wrap:wrap; gap:9px; margin-bottom:22px; }}
    .stat {{ background:#fff; border:1px solid var(--rule); padding:11px 15px; border-radius:4px; font-size:12px; }}
    .stat strong {{ color:var(--accent); font-size:18px; padding-right:5px; }}
    .legend-bar {{ display:flex; flex-wrap:wrap; gap:16px; align-items:center; padding:14px 16px; background:#fff; border:1px solid var(--rule); border-radius:4px; font-size:12px; }}
    .sample-entity {{ display:inline-block; border:1px solid var(--ink); padding:4px 10px; }}
    .sample-attr {{ display:inline-block; border:1px solid var(--ink); border-radius:50%; padding:4px 10px; }}
    .sample-relation {{ display:inline-block; transform:rotate(45deg); border:1px solid var(--ink); width:15px; height:15px; margin:0 8px; }}
    .sample-audit {{ display:inline-block; width:26px; border-top:1px dashed var(--muted); vertical-align:middle; }}
    h2 {{ font:400 34px 'Instrument Serif',serif; margin:48px 0 8px; }}
    .section-note {{ color:var(--muted); font-size:13px; line-height:1.6; max-width:860px; margin:0 0 16px; }}
    nav {{ display:flex; flex-wrap:wrap; gap:7px; margin:13px 0 25px; }}
    nav a {{ color:var(--ink); text-decoration:none; font-size:11px; background:#fff; border:1px solid var(--rule); padding:7px 9px; border-radius:3px; }}
    nav a:hover,nav a:focus-visible {{ color:var(--accent); border-color:var(--accent); }}
    .panel,.table-panel {{ border:1px solid var(--rule); background:#fff; border-radius:4px; margin:0 0 18px; overflow:hidden; }}
    .panel-head {{ display:flex; align-items:baseline; gap:12px; padding:15px 18px; border-bottom:1px solid var(--rule); }}
    .panel-head h3 {{ font-size:15px; font-weight:600; margin:0; }}
    .section-no {{ color:var(--accent); font:500 10px 'Geist Mono',monospace; }}
    .panel-count {{ color:var(--muted); margin-left:auto; font:400 10px 'Geist Mono',monospace; }}
    .diagram-container {{ width:100%; overflow-x:auto; }}
    svg {{ display:block; width:100%; min-width:960px; }}
    svg.overview-svg {{ min-width:1100px; }}
    svg .entity,svg .diamond {{ fill:#fff; stroke:var(--ink); stroke-width:1.2; }}
    svg .edge {{ stroke:var(--muted); stroke-width:1.2; }}
    svg .rule {{ stroke:var(--rule); stroke-width:1; }}
    svg .entity-text {{ fill:var(--ink); font:600 12px 'Geist',system-ui,sans-serif; }}
    svg .diamond-text {{ fill:var(--ink); font:500 10px 'Geist Mono',monospace; }}
    svg .cardinality,svg .legend {{ fill:var(--muted); font:500 8px 'Geist Mono',monospace; }}
    svg .attribute-name {{ fill:var(--ink); font:500 10px 'Geist Mono',monospace; }}
    svg .attribute-type {{ fill:var(--muted); font:400 8px 'Geist Mono',monospace; }}
    .table-panel summary, .appendix summary {{ cursor:pointer; display:flex; justify-content:space-between; gap:14px; padding:14px 18px; }}
    .table-panel summary span {{ font-size:14px; font-weight:600; }}
    .table-panel summary small {{ color:var(--muted); font:400 10px 'Geist Mono',monospace; }}
    .table-panel[open] summary {{ border-bottom:1px solid var(--rule); }}
    .appendix details {{ background:#fff; border:1px solid var(--rule); border-radius:4px; margin-bottom:8px; }}
    .appendix summary {{ font:500 12px 'Geist Mono',monospace; }}
    pre {{ margin:0; padding:16px; border-top:1px solid var(--rule); overflow-x:auto; font:400 11px/1.55 'Geist Mono',monospace; }}
    @media print {{ .frame {{ max-width:none; padding:0; }} nav {{ display:none; }} .panel,.table-panel {{ break-inside:avoid; }} .diagram-container {{ overflow-x:visible; }} svg {{ min-width:0; }} .table-panel:not([open]) {{ display:none; }} }}
  </style>
</head>
<body><main class="frame">
  <p class="eyebrow">Hệ thống quản lý nhân sự · PostgreSQL</p>
  <h1>Sơ đồ ERD kiểu Chen</h1>
  <p class="lede">Sơ đồ đầy đủ từ migration Flyway: thực thể là hình chữ nhật, thuộc tính là hình oval, quan hệ khóa ngoại là hình thoi. Mô hình được chia thành các phần để đọc được từng tên bảng, cột và lực lượng quan hệ.</p>
  <div class="stats"><div class="stat"><strong>{len(tables)}</strong>bảng</div><div class="stat"><strong>{column_count}</strong>cột</div><div class="stat"><strong>{len(fks)}</strong>khóa ngoại</div><div class="stat"><strong>{len(documented)}</strong>quan hệ nghiệp vụ</div></div>
  <div class="legend-bar"><span><span class="sample-entity">THỰC THỂ</span> bảng</span><span><span class="sample-attr">thuộc tính</span> cột</span><span><span class="sample-relation"></span> quan hệ</span><span><u>Gạch dưới</u> khóa chính</span><span><span class="sample-audit"></span> FK kiểm toán / quy trình</span></div>
  <h2>Ví dụ kết nối</h2>
  <p class="section-note">Ba thực thể cốt lõi được trình bày cùng thuộc tính và quan hệ như mẫu. Phần tiếp theo mở rộng ra toàn bộ bảng, cột và khóa ngoại.</p>
  {overview_svg()}
  <h2 id="relationships">Quan hệ giữa các thực thể</h2>
  <p class="section-note">Mỗi dòng đọc từ bảng được tham chiếu → cột khóa ngoại → bảng chứa khóa ngoại. Số ở hai đầu là lực lượng quan hệ. Nét đứt chỉ quan hệ kiểm toán hoặc quy trình chưa có trong ERD nghiệp vụ của tài liệu schema.</p>
  <nav aria-label="Các phần quan hệ">{relationship_nav}</nav>
  {''.join(relationship_panels)}
  <h2 id="attributes">Đầy đủ thuộc tính của {len(tables)} thực thể</h2>
  <p class="section-note">Mở từng bảng để xem tất cả cột. Oval có nét đứt là khóa ngoại; tên gạch dưới là khóa chính. Kiểu dữ liệu và dấu PK/FK/UQ/NN nằm trong oval. Quan hệ và thuộc tính đều lấy trực tiếp từ V1–V10.</p>
  <nav aria-label="Các bảng">{table_index}</nav>
  {attribute_panels}
  <section class="appendix" id="migrations"><h2>SQL gốc</h2><p class="section-note">Toàn bộ migration đi kèm để đối chiếu CHECK, INDEX, ALTER TABLE và các ràng buộc khác.</p>{full_migrations}</section>
</main></body></html>'''

OUTPUT.write_text(document, encoding="utf-8")
print(f"Wrote {OUTPUT} ({len(tables)} tables, {column_count} columns, {len(fks)} FKs, {len(relationship_panels)} relation panels)")
