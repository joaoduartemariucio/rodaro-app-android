import io
import json
import os
import sys
import tempfile
import unittest
import urllib.error
import zipfile
from xml.sax.saxutils import escape

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import anp_prices  # noqa: E402

HEADER = [
    "DATA INICIAL",
    "DATA FINAL",
    "REGIÃO",
    "ESTADOS",
    "PRODUTO",
    "NÚMERO DE POSTOS PESQUISADOS",
    "UNIDADE DE MEDIDA",
    "PREÇO MÉDIO REVENDA",
    "DESVIO PADRÃO REVENDA",
    "PREÇO MÍNIMO REVENDA",
]

UFS = sorted(anp_prices.STATES)


def column(index):
    letters = ""
    index += 1
    while index:
        index, remainder = divmod(index - 1, 26)
        letters = chr(65 + remainder) + letters
    return letters


def workbook(rows, sheet="ESTADOS", inline=False, header_row=10):
    shared = []
    xml_rows = []
    all_rows = [[] for _ in range(header_row - 1)] + rows
    for number, row in enumerate(all_rows, start=1):
        cells = []
        for index, value in enumerate(row):
            if value is None:
                continue
            reference = f"{column(index)}{number}"
            if isinstance(value, (int, float)):
                cells.append(f'<c r="{reference}"><v>{value}</v></c>')
            elif inline:
                cells.append(f'<c r="{reference}" t="inlineStr"><is><t>{escape(value)}</t></is></c>')
            elif value.startswith("="):
                cells.append(f'<c r="{reference}" t="str"><f>X</f><v>{escape(value[1:])}</v></c>')
            else:
                shared.append(value)
                cells.append(f'<c r="{reference}" t="s"><v>{len(shared) - 1}</v></c>')
        xml_rows.append(f'<row r="{number}">{"".join(cells)}</row>')
    main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    relationships = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr(
            "xl/workbook.xml",
            f'<workbook xmlns="{main}" xmlns:r="{relationships}"><sheets>'
            f'<sheet name="CAPITAIS" sheetId="1" r:id="rId1"/><sheet name="{sheet}" sheetId="2" r:id="rId2"/></sheets></workbook>',
        )
        archive.writestr(
            "xl/_rels/workbook.xml.rels",
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Target="worksheets/sheet2.xml"/>'
            "</Relationships>",
        )
        archive.writestr("xl/worksheets/sheet1.xml", f'<worksheet xmlns="{main}"><sheetData/></worksheet>')
        archive.writestr("xl/worksheets/sheet2.xml", f'<worksheet xmlns="{main}"><sheetData>{"".join(xml_rows)}</sheetData></worksheet>')
        items = "".join(f"<si><t>{escape(text)}</t></si>" for text in shared)
        archive.writestr("xl/sharedStrings.xml", f'<sst xmlns="{main}">{items}</sst>')
    return buffer.getvalue()


def row(state, product, mean="6.3599", sd="0.38", start=46327, end=46333, unit="R$/l", stations=12):
    return [start, end, "SUDESTE", state, product, stations, unit, mean, sd, "5.0"]


def full_week(start=46327, end=46333, states=UFS):
    return [HEADER] + [row(state, "GASOLINA COMUM", start=start, end=end) for state in states]


class Response:
    def __init__(self, body):
        self.body = body

    def __enter__(self):
        return self

    def __exit__(self, *arguments):
        return False

    def read(self):
        return self.body


def opener_for(pages):
    def opener(request, timeout=None):
        url = request.full_url
        assert request.get_method() == "GET"
        value = pages[url]
        if isinstance(value, Exception):
            raise value
        return Response(value)

    return opener


def http_error(code):
    return urllib.error.HTTPError("https://rodaro.web.app/anp/precos-uf.json", code, "x", {}, None)


LISTING = (
    '<a href="https://x.gov.br/arquivos/resumo_semanal_lpc_2026_11_01-2026_11_07.xlsx">a</a>'
    '<a href="/arquivos/resumo_semanal_lpc-4.xlsx">b</a>'
    '<a href="https://x.gov.br/arquivos/resumo_semanal_lpc_2026_11_01-2026_11_07.xlsx">dup</a>'
    '<a href="https://x.gov.br/arquivos/outro.pdf">c</a>'
)


class ReadingTests(unittest.TestCase):
    def test_links_are_found_in_page_order_without_duplicates(self):
        links = anp_prices.summary_links(LISTING, "https://www.gov.br/anp/page")
        self.assertEqual(
            ["https://x.gov.br/arquivos/resumo_semanal_lpc_2026_11_01-2026_11_07.xlsx", "https://www.gov.br/arquivos/resumo_semanal_lpc-4.xlsx"],
            links,
        )

    def test_the_sheet_is_found_by_name_and_empty_cells_keep_columns(self):
        rows = anp_prices.read_sheet(workbook([["A", None, "C"]], header_row=1))
        self.assertEqual([["A", None, "C"]], rows)

    def test_inline_and_formula_strings_are_read(self):
        rows = anp_prices.read_sheet(workbook([["inline"]], inline=True, header_row=1))
        self.assertEqual([["inline"]], rows)
        rows = anp_prices.read_sheet(workbook([["=formula"]], header_row=1))
        self.assertEqual([["formula"]], rows)

    def test_a_missing_sheet_is_a_layout_error(self):
        with self.assertRaises(anp_prices.LayoutError):
            anp_prices.read_sheet(workbook([["A"]], sheet="OUTRA"))


class ParsingTests(unittest.TestCase):
    def parse(self, rows, **options):
        return anp_prices.parse_states(anp_prices.read_sheet(workbook(rows, **options)))

    def test_the_header_is_found_even_when_it_is_not_row_10(self):
        weeks = self.parse(full_week(), header_row=3)
        self.assertEqual("2026-11-01", weeks[0]["start"])
        self.assertEqual("2026-11-07", weeks[0]["end"])
        self.assertEqual({"mean": "6.360", "sd": "0.380", "stations": 12}, weeks[0]["states"]["SP"]["gasolinaComum"])

    def test_serial_and_text_dates_are_read(self):
        weeks = self.parse([HEADER, row("SÃO PAULO", "GASOLINA COMUM", start="01/11/2026", end="07/11/2026")])
        self.assertEqual("2026-11-01", weeks[0]["start"])
        self.assertEqual("2026-11-07", weeks[0]["end"])

    def test_diesel_products_and_accented_states_map_to_app_keys(self):
        weeks = self.parse(
            [
                HEADER,
                row("PARÁ", "ÓLEO DIESEL", mean="6.98"),
                row("pará", "OLEO DIESEL S10", mean="7.3"),
                row("AMAPÁ", "ETANOL HIDRATADO", mean="4.93", sd=None),
                row("SAO PAULO", "GNV", mean="4.5"),
                row("SAO PAULO", "GLP", mean="110", unit="R$/13kg"),
                row("ATLANTIDA", "GASOLINA COMUM"),
            ]
        )
        states = weeks[0]["states"]
        self.assertEqual("6.980", states["PA"]["dieselS500"]["mean"])
        self.assertEqual("7.300", states["PA"]["dieselS10"]["mean"])
        self.assertEqual({"mean": "4.930", "stations": 12}, states["AP"]["etanol"])
        self.assertEqual({"PA", "AP"}, set(states))

    def test_means_round_half_even_to_three_decimals(self):
        weeks = self.parse([HEADER, row("ACRE", "GASOLINA COMUM", mean="6.3625"), row("ALAGOAS", "GASOLINA COMUM", mean="6.3635")])
        self.assertEqual("6.362", weeks[0]["states"]["AC"]["gasolinaComum"]["mean"])
        self.assertEqual("6.364", weeks[0]["states"]["AL"]["gasolinaComum"]["mean"])

    def test_a_missing_column_raises(self):
        header = [title for title in HEADER if title != "DESVIO PADRÃO REVENDA"]
        with self.assertRaises(anp_prices.LayoutError):
            self.parse([header, ["x"]])

    def test_a_week_that_does_not_start_on_sunday_raises(self):
        with self.assertRaises(anp_prices.LayoutError):
            self.parse([HEADER, row("ACRE", "GASOLINA COMUM", start=46328)])

    def test_the_sanity_check_needs_twenty_states(self):
        self.assertTrue(anp_prices.sane(self.parse(full_week())[0]))
        self.assertFalse(anp_prices.sane(self.parse(full_week(states=UFS[:5]))[0]))
        silly = self.parse([HEADER] + [row(state, "GASOLINA COMUM", mean="63.5") for state in UFS])[0]
        self.assertFalse(anp_prices.sane(silly))


class MergingTests(unittest.TestCase):
    def week(self, start, mean="6.000"):
        return {"start": start, "end": start, "states": {"SP": {"gasolinaComum": {"mean": mean}}}}

    def test_merge_keeps_52_newest_first_and_replaces_the_same_week(self):
        existing = [self.week(f"2025-{month:02d}-{day:02d}") for month in range(1, 13) for day in (1, 8, 15, 22, 28)]
        merged = anp_prices.merge(existing, [self.week("2025-12-28", "7.000"), self.week("2026-01-04")])
        self.assertEqual(52, len(merged))
        self.assertEqual("2026-01-04", merged[0]["start"])
        self.assertEqual("7.000", merged[1]["states"]["SP"]["gasolinaComum"]["mean"])

    def test_unchanged_input_gives_the_same_bytes_and_keeps_updated_at(self):
        first = anp_prices.build(None, [self.week("2026-11-01")], "2026-11-10T13:00:00Z")
        text = anp_prices.render(first)
        again = anp_prices.build(json.loads(text), [self.week("2026-11-01")], "2026-11-17T13:00:00Z")
        self.assertEqual(text, anp_prices.render(again))
        newer = anp_prices.build(json.loads(text), [self.week("2026-11-08")], "2026-11-17T13:00:00Z")
        self.assertEqual("2026-11-17T13:00:00Z", newer["updatedAt"])
        self.assertEqual(2, anp_prices.render(newer).count("\n{"))


class MainTests(unittest.TestCase):
    def run_main(self, pages, existing=anp_prices.PUBLISHED_URL, weeks=1):
        directory = tempfile.mkdtemp()
        output = os.path.join(directory, "anp", "precos-uf.json")
        code = anp_prices.main(
            ["--output", output, "--existing", existing, "--weeks", str(weeks), "--backfill-weeks", "2", "--now", "2026-11-10T13:00:00Z"],
            opener_for(pages),
        )
        if not os.path.exists(output):
            return code, None
        with open(output, encoding="utf-8") as file:
            return code, file.read()

    def pages(self, published):
        return {
            anp_prices.PUBLISHED_URL: published,
            anp_prices.LISTING_URL: LISTING.encode(),
            "https://x.gov.br/arquivos/resumo_semanal_lpc_2026_11_01-2026_11_07.xlsx": workbook(full_week()),
            "https://www.gov.br/arquivos/resumo_semanal_lpc-4.xlsx": workbook(full_week(46320, 46326)),
        }

    def test_nothing_published_yet_backfills(self):
        code, text = self.run_main(self.pages(http_error(404)))
        self.assertEqual(0, code)
        document = json.loads(text)
        self.assertEqual(["2026-11-01", "2026-10-25"], [week["start"] for week in document["weeks"]])
        self.assertEqual("rodaro.anp.precos-uf", document["format"])

    def test_a_server_error_on_the_published_file_stops_everything(self):
        code, text = self.run_main(self.pages(http_error(500)))
        self.assertEqual(1, code)
        self.assertIsNone(text)

    def test_a_published_file_with_another_version_stops_everything(self):
        code, _ = self.run_main(self.pages(json.dumps({"format": "rodaro.anp.precos-uf", "formatVersion": 2, "weeks": []}).encode()))
        self.assertEqual(1, code)
        code, _ = self.run_main(self.pages(b"not json"))
        self.assertEqual(1, code)

    def test_a_week_already_published_is_byte_identical(self):
        _, first = self.run_main(self.pages(http_error(404)))
        code, second = self.run_main(self.pages(first.encode()))
        self.assertEqual(0, code)
        self.assertEqual(first, second)

    def test_a_failing_sanity_check_writes_nothing(self):
        pages = self.pages(http_error(404))
        pages["https://x.gov.br/arquivos/resumo_semanal_lpc_2026_11_01-2026_11_07.xlsx"] = workbook(full_week(states=UFS[:5]))
        code, text = self.run_main(pages)
        self.assertEqual(1, code)
        self.assertIsNone(text)

    def test_a_local_existing_file_is_merged(self):
        _, first = self.run_main(self.pages(http_error(404)))
        directory = tempfile.mkdtemp()
        path = os.path.join(directory, "precos-uf.json")
        with open(path, "w", encoding="utf-8") as file:
            file.write(first)
        code, second = self.run_main(self.pages(None), existing=path)
        self.assertEqual(0, code)
        self.assertEqual(first, second)
        code, _ = self.run_main(self.pages(http_error(404)), existing=os.path.join(directory, "missing.json"), weeks=1)
        self.assertEqual(0, code)


if __name__ == "__main__":
    unittest.main()
