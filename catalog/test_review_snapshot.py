import unittest
from review_snapshot import snapshot,feed_titles
class SnapshotTest(unittest.TestCase):
    def test_feed(self):
        rows=feed_titles('<rss><channel><item><title>Fixture &amp; test</title><link>https://example.invalid/test.apk</link></item></channel></rss>',"Fixture")
        self.assertEqual(rows[0]["name"],"Fixture & test")
        self.assertNotIn("link",rows[0])
        self.assertFalse(rows[0]["installable"])
    def report(self):
        return {"mode":"capability_probe_only","generatedAt":1,"sources":[{"source":s,"status":"checked_metadata_only","html":"200","robots":"200","discovered":{"sitemapPageUrls":4},"reviewRows":[{"name":"Fixture","explicitApkUrl":"https://example.invalid/test.apk","fileSize":"https://example.invalid/test.apk"},{"name":"Fixture"}]} for s in ("Android Oyun Club","APKVision")]}
    def test_snapshot(self):
        docs=snapshot(self.report(),42)
        row=docs["apkvision.json"]["records"][0]
        self.assertEqual(docs["apkvision.json"]["total"],1)
        self.assertEqual(docs["index.json"]["installable"],0)
        self.assertNotIn("explicitApkUrl",row)
        self.assertNotIn("fileSize",row)
    def test_failed_probe(self):
        d=self.report();d["sources"][0]["html"]="403"
        with self.assertRaises(ValueError):snapshot(d,42)
