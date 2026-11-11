"""Offline contract cases for the isolated DeepSeek price parser."""
import importlib.util
from pathlib import Path
import unittest

MODULE = Path(__file__).resolve().parents[2] / "workers" / "price-collectors" / "deepseek.py"
spec = importlib.util.spec_from_file_location("deepseek_price_collector", MODULE)
deepseek = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deepseek)

PAGE = b"""<html><body><table>
<tr><td colspan="3">MODEL</td><td>deepseek-flash <sup>(1)</sup></td><td>deepseek-v4-pro</td></tr>
<tr><td rowspan="6">PRICING <sup>(2)</sup></td><td rowspan="2">1M INPUT TOKENS (CACHE HIT)</td><td>OFF-PEAK</td><td>$0.003</td><td>$0.022</td></tr>
<tr><td>PEAK</td><td>$0.006</td><td>$0.044</td></tr>
<tr><td rowspan="2">1M INPUT TOKENS (CACHE MISS)</td><td>OFF-PEAK</td><td>$0.15</td><td>$0.66</td></tr>
<tr><td>PEAK</td><td>$0.3</td><td>$1.32</td></tr>
<tr><td rowspan="2">1M OUTPUT TOKENS</td><td>OFF-PEAK</td><td>$0.6</td><td>$1.98</td></tr>
<tr><td>PEAK</td><td>$1.2</td><td>$3.96</td></tr></table>
<p>Peak hours are 01:00 - 04:00 and 06:00 - 10:00 UTC, Monday through Friday, excluding Chinese public holidays.
All other hours are off-peak, including weekends and Chinese public holidays in full.</p></body></html>"""


class DeepSeekCollectorCases(unittest.TestCase):
    def test_pricing_observation_is_not_an_accounting_price(self):
        result = deepseek.parse(PAGE, 123456)
        self.assertFalse(result["accountingReady"])
        self.assertEqual("OBSERVED_ONLY", result["validity"])
        self.assertIn("CHINESE_PUBLIC_HOLIDAY_CALENDAR", result["missing"])
        self.assertEqual(3000, result["models"][0]["off_peak"]["cached"])
        self.assertEqual(1320000, result["models"][1]["peak"]["input"])

    def test_changed_peak_conditions_are_rejected(self):
        with self.assertRaises(deepseek.PriceStructureError):
            deepseek.parse(PAGE.replace(b"06:00 - 10:00", b"07:00 - 10:00"), 123456)

    def test_missing_or_ambiguous_price_rows_are_rejected(self):
        with self.assertRaises(deepseek.PriceStructureError):
            deepseek.parse(PAGE.replace(b"$0.15", b"unknown"), 123456)
        with self.assertRaises(deepseek.PriceStructureError):
            deepseek.parse(PAGE.replace(b"<p>Peak", PAGE.split(b"</table>")[0] + b"</table><p>Peak"), 123456)


if __name__ == "__main__":
    unittest.main()
