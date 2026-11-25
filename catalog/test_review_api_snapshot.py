import json, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parent/"api/review/v1"
class ReviewApiSnapshotTest(unittest.TestCase):
    def test_review_only(self):
        index=json.loads((ROOT/"index.json").read_text())
        self.assertEqual(index["installable"],0)
        self.assertEqual({s["name"] for s in index["sources"]},{"Android Oyun Club","APKVision"})
        for s in index["sources"]:
            doc=json.loads((ROOT/s["path"]).read_text())
            self.assertEqual(doc["total"],len(doc["records"]))
            self.assertEqual(doc["total"],s["reviewedRecords"])
            self.assertEqual(doc["installable"],0)
            self.assertEqual(doc["generatedAt"],index["generatedAt"])
            for row in doc["records"]:
                self.assertIs(row["installable"],False)
                self.assertNotIn("apkUrl",row)
                self.assertNotIn("pageUrl",row)
                self.assertNotIn("explicitApkUrl",row)
                self.assertTrue(row["name"])
                self.assertFalse(any("https://" in str(v) for v in row.values()))
