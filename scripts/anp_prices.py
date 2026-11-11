#!/usr/bin/env python3
"""Builds hosting/public/anp/precos-uf.json from ANP's weekly price survey (LPC).

ANP has no API: every week it publishes a "resumo semanal" spreadsheet on gov.br. This script
scrapes the listing page for those links (their file names are irregular), reads the ESTADOS
sheet of the newest files by header name, merges the weeks into the JSON that is already
published and keeps the last 52 weeks. Standard library only, so CI needs no pip install.

Exit codes: 0 on success (the last line says changed=true|false), 1 when nothing must be
deployed (download failure, unexpected layout, data that fails the sanity check).
"""

import argparse
import datetime
import decimal
import html
import json
import os
import re
import sys
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ElementTree
import zipfile
from io import BytesIO

LISTING_URL = (
    "https://www.gov.br/anp/pt-br/assuntos/precos-e-defesa-da-concorrencia/precos/"
    "levantamento-de-precos-de-combustiveis-ultimas-semanas-pesquisadas"
)
PUBLISHED_URL = "https://rodaro.web.app/anp/precos-uf.json"
USER_AGENT = "Mozilla/5.0 (compatible; RodaroANP/1.0)"
FORMAT = "rodaro.anp.precos-uf"
FORMAT_VERSION = 1
SOURCE = "ANP - Levantamento de Preços de Combustíveis (LPC), resumo semanal por estado"
KEEP_WEEKS = 52
MIN_STATES = 20
MIN_MEAN = decimal.Decimal("1")
MAX_MEAN = decimal.Decimal("20")
EXCEL_EPOCH = datetime.date(1899, 12, 30)

MAIN = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
RELATIONSHIP = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"
PACKAGE_RELATIONSHIP = "{http://schemas.openxmlformats.org/package/2006/relationships}Relationship"

PRODUCTS = {
    "GASOLINA COMUM": "gasolinaComum",
    "GASOLINA ADITIVADA": "gasolinaAditivada",
    "ETANOL HIDRATADO": "etanol",
    "OLEO DIESEL S10": "dieselS10",
    "OLEO DIESEL": "dieselS500",
}

STATES = {
    "ACRE": "AC",
    "ALAGOAS": "AL",
    "AMAPA": "AP",
    "AMAZONAS": "AM",
    "BAHIA": "BA",
    "CEARA": "CE",
    "DISTRITO FEDERAL": "DF",
    "ESPIRITO SANTO": "ES",
    "GOIAS": "GO",
    "MARANHAO": "MA",
    "MATO GROSSO": "MT",
    "MATO GROSSO DO SUL": "MS",
    "MINAS GERAIS": "MG",
    "PARA": "PA",
    "PARAIBA": "PB",
    "PARANA": "PR",
    "PERNAMBUCO": "PE",
    "PIAUI": "PI",
    "RIO DE JANEIRO": "RJ",
    "RIO GRANDE DO NORTE": "RN",
    "RIO GRANDE DO SUL": "RS",
    "RONDONIA": "RO",
    "RORAIMA": "RR",
    "SANTA CATARINA": "SC",
    "SAO PAULO": "SP",
    "SERGIPE": "SE",
    "TOCANTINS": "TO",
}

COLUMNS = {
    "start": "DATA INICIAL",
    "end": "DATA FINAL",
    "state": "ESTADOS",
    "product": "PRODUTO",
    "stations": "NUMERO DE POSTOS PESQUISADOS",
    "unit": "UNIDADE DE MEDIDA",
    "mean": "PRECO MEDIO REVENDA",
    "sd": "DESVIO PADRAO REVENDA",
}


class LayoutError(Exception):
    pass


class NotPublished(Exception):
    pass


def normalized(text):
    plain = unicodedata.normalize("NFD", str(text or ""))
    plain = "".join(char for char in plain if unicodedata.category(char) != "Mn")
    return " ".join(plain.upper().split())


def fetch(url, opener=urllib.request.urlopen):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT}, method="GET")
    with opener(request, timeout=60) as response:
        return response.read()


def summary_links(page, base=LISTING_URL):
    links = []
    for match in re.finditer(r'href="([^"]*resumo_semanal[^"]*\.xlsx)"', page, re.IGNORECASE):
        link = urllib.parse.urljoin(base, html.unescape(match.group(1)))
        if link not in links:
            links.append(link)
    return links


def column_index(reference):
    letters = re.match(r"[A-Z]+", reference).group(0)
    index = 0
    for letter in letters:
        index = index * 26 + (ord(letter) - ord("A") + 1)
    return index - 1


def read_sheet(data, name="ESTADOS"):
    with zipfile.ZipFile(BytesIO(data)) as archive:
        workbook = ElementTree.fromstring(archive.read("xl/workbook.xml"))
        relations = ElementTree.fromstring(archive.read("xl/_rels/workbook.xml.rels"))
        targets = {item.get("Id"): item.get("Target") for item in relations.iter(PACKAGE_RELATIONSHIP)}
        sheet_id = next(
            (sheet.get(RELATIONSHIP) for sheet in workbook.iter(MAIN + "sheet") if normalized(sheet.get("name")) == normalized(name)),
            None,
        )
        if sheet_id is None or sheet_id not in targets:
            raise LayoutError(f"sheet {name} not found")
        target = targets[sheet_id].lstrip("/")
        path = target if target.startswith("xl/") else "xl/" + target
        shared = []
        if "xl/sharedStrings.xml" in archive.namelist():
            for item in ElementTree.fromstring(archive.read("xl/sharedStrings.xml")).iter(MAIN + "si"):
                shared.append("".join(text.text or "" for text in item.iter(MAIN + "t")))
        sheet = ElementTree.fromstring(archive.read(path))

    rows = []
    for row in sheet.iter(MAIN + "row"):
        values = {}
        for cell in row.iter(MAIN + "c"):
            kind = cell.get("t")
            if kind == "inlineStr":
                value = "".join(text.text or "" for text in cell.iter(MAIN + "t"))
            else:
                raw = cell.find(MAIN + "v")
                value = None if raw is None else raw.text
                if value is not None and kind == "s":
                    value = shared[int(value)]
            values[column_index(cell.get("r"))] = value
        rows.append([values.get(index) for index in range(max(values) + 1)] if values else [])
    return rows


def parse_date(value):
    text = str(value or "").strip()
    if re.fullmatch(r"\d+(\.0+)?", text):
        return EXCEL_EPOCH + datetime.timedelta(days=int(float(text)))
    match = re.fullmatch(r"(\d{1,2})/(\d{1,2})/(\d{4})", text)
    if match:
        return datetime.date(int(match.group(3)), int(match.group(2)), int(match.group(1)))
    try:
        return datetime.date.fromisoformat(text[:10])
    except ValueError as error:
        raise LayoutError(f"unreadable date {text!r}") from error


def rounded(value):
    text = str(value or "").strip().replace(",", ".")
    if not text or text == "-":
        return None
    try:
        number = decimal.Decimal(text)
    except decimal.InvalidOperation:
        return None
    return number.quantize(decimal.Decimal("0.001"), rounding=decimal.ROUND_HALF_EVEN)


def parse_states(rows):
    header_index = next(
        (index for index, row in enumerate(rows) if {"ESTADOS", "PRODUTO"} <= {normalized(cell) for cell in row}),
        None,
    )
    if header_index is None:
        raise LayoutError("header row not found")
    header = [normalized(cell) for cell in rows[header_index]]
    positions = {}
    for key, title in COLUMNS.items():
        if title not in header:
            raise LayoutError(f"column {title} not found")
        positions[key] = header.index(title)

    weeks = {}
    for row in rows[header_index + 1 :]:

        def cell(key, row=row):
            return row[positions[key]] if positions[key] < len(row) else None

        if not any(row):
            continue
        product = PRODUCTS.get(normalized(cell("product")))
        uf = STATES.get(normalized(cell("state")))
        if product is None or uf is None or normalized(cell("unit")).lower() != "r$/l":
            continue
        start, end = parse_date(cell("start")), parse_date(cell("end"))
        if start.weekday() != 6:
            raise LayoutError(f"week starting {start} is not a Sunday")
        mean = rounded(cell("mean"))
        if mean is None:
            continue
        price = {"mean": str(mean)}
        sd = rounded(cell("sd"))
        if sd is not None:
            price["sd"] = str(sd)
        stations = rounded(cell("stations"))
        if stations is not None:
            price["stations"] = int(stations)
        week = weeks.setdefault((start, end), {"start": start.isoformat(), "end": end.isoformat(), "states": {}})
        week["states"].setdefault(uf, {})[product] = price
    if not weeks:
        raise LayoutError("no state prices found")
    return [weeks[key] for key in sorted(weeks, reverse=True)]


def sane(week):
    states = [uf for uf, prices in week["states"].items() if "gasolinaComum" in prices]
    if len(states) < MIN_STATES:
        return False
    return all(MIN_MEAN <= decimal.Decimal(price["mean"]) <= MAX_MEAN for prices in week["states"].values() for price in prices.values())


def merge(existing, new_weeks, keep=KEEP_WEEKS):
    weeks = {week["start"]: week for week in existing}
    for week in new_weeks:
        weeks[week["start"]] = week
    return [weeks[start] for start in sorted(weeks, reverse=True)][:keep]


def render(document):
    head = {key: document[key] for key in ("format", "formatVersion", "source", "unit", "updatedAt")}
    lines = [json.dumps(week, ensure_ascii=False, sort_keys=True, separators=(",", ":")) for week in document["weeks"]]
    prefix = json.dumps(head, ensure_ascii=False, separators=(",", ":"))[:-1]
    return prefix + ',"weeks":[\n' + ",\n".join(lines) + "\n]}\n"


def validated(document):
    if not isinstance(document, dict) or document.get("format") != FORMAT or document.get("formatVersion") != FORMAT_VERSION:
        raise LayoutError("published JSON has an unexpected format or version")
    if not isinstance(document.get("weeks"), list):
        raise LayoutError("published JSON has no weeks")
    return document


def load_existing(source, opener=urllib.request.urlopen):
    """Returns (document, raw bytes); (None, None) only when nothing is published yet (HTTP 404 or no local file)."""
    if source is None:
        return None, None
    if re.match(r"https?://", source):
        try:
            raw = fetch(source, opener)
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return None, None
            raise
    else:
        if not os.path.exists(source):
            return None, None
        with open(source, "rb") as file:
            raw = file.read()
    try:
        document = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, ValueError) as error:
        raise LayoutError("published JSON is unreadable") from error
    return validated(document), raw


def build(existing, new_weeks, now):
    previous = existing["weeks"] if existing else []
    weeks = merge(previous, new_weeks)
    unchanged = existing is not None and weeks == previous
    return {
        "format": FORMAT,
        "formatVersion": FORMAT_VERSION,
        "source": SOURCE,
        "unit": "BRL/L",
        "updatedAt": existing["updatedAt"] if unchanged else now,
        "weeks": weeks,
    }


def main(argv=None, opener=urllib.request.urlopen):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--output", required=True)
    parser.add_argument("--existing", default=PUBLISHED_URL, help="published JSON (URL or file) to merge into")
    parser.add_argument("--weeks", type=int, default=3, help="newest summaries to download")
    parser.add_argument("--backfill-weeks", type=int, default=KEEP_WEEKS, help="summaries to download when nothing is published")
    parser.add_argument("--now", default=None, help="timestamp for updatedAt (tests)")
    arguments = parser.parse_args(argv)
    now = arguments.now or datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    try:
        existing, raw = load_existing(arguments.existing, opener)
        count = arguments.weeks if existing is not None else arguments.backfill_weeks
        links = summary_links(fetch(LISTING_URL, opener).decode("utf-8", "replace"))[:count]
        if not links:
            raise LayoutError("no summary links on the listing page")
        new_weeks = []
        for link in links:
            new_weeks.extend(parse_states(read_sheet(fetch(link, opener))))
    except (urllib.error.URLError, OSError, LayoutError, zipfile.BadZipFile, ElementTree.ParseError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    for week in new_weeks:
        if not sane(week):
            print(f"error: week {week['start']} failed the sanity check", file=sys.stderr)
            return 1

    text = render(build(existing, new_weeks, now))
    changed = raw is None or text.encode("utf-8") != raw
    os.makedirs(os.path.dirname(os.path.abspath(arguments.output)), exist_ok=True)
    with open(arguments.output, "w", encoding="utf-8") as file:
        file.write(text)
    print(f"changed={'true' if changed else 'false'}")
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a", encoding="utf-8") as file:
            file.write(f"changed={'true' if changed else 'false'}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
