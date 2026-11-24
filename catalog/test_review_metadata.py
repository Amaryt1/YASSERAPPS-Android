import unittest
from review_metadata import candidates,factual_rows
class ReviewTest(unittest.TestCase):
    def test_candidates(self):
        self.assertEqual(candidates(["https://x.invalid/tag/a","https://x.invalid/foo.apk","https://x.invalid/app/game"]),["https://x.invalid/app/game"])
    def test_rows(self):
        html='<script type="application/ld+json">{"@graph":[{"@type":"MobileApplication","name":"Fixture","softwareVersion":"2.0","image":"https://x.invalid/image.png"},{"@type":"Article","name":"Not app"}]}</script>'
        rows=factual_rows(html,"Fixture","https://x.invalid/app")
        self.assertEqual(len(rows),1)
        self.assertEqual(rows[0]["softwareVersion"],"2.0")
        self.assertFalse(rows[0]["installable"])
        self.assertNotIn("apkUrl",rows[0])
        self.assertNotIn("modified",rows[0])
