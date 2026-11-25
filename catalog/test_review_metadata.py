import unittest
from review_metadata import candidates,factual_rows,advertised_links
class ReviewTest(unittest.TestCase):
    def test_candidates(self):
        self.assertEqual(candidates(["https://x.invalid/tag/a","https://x.invalid/foo.apk","https://x.invalid/app/game"]),["https://x.invalid/app/game"])
    def test_explicit_extensionless_url(self):
        html='<script type="application/ld+json">{"@type":"MobileApplication","name":"Fixture MOD APK","downloadUrl":"/download?id=123","applicationCategory":"Game"}</script>'
        rows=factual_rows(html,"Fixture","https://x.invalid/app")
        self.assertEqual(rows[0]["explicitApkUrl"],"https://x.invalid/download?id=123")
        self.assertFalse(rows[0]["installable"])
    def test_advertised_download(self):
        html='<script type="application/ld+json">{"@type":"VideoGame","name":"Fixture MOD APK"}</script><a href="/public/download?id=1">Download MOD APK</a>'
        self.assertEqual(factual_rows(html,"Fixture","https://x.invalid/app")[0]["explicitApkUrl"],"https://x.invalid/public/download?id=1")
        self.assertEqual(advertised_links('<a href="https://other.invalid/file.apk">Download</a><a href="/original.apk">Download original APK</a>',"https://x.invalid/app"),[])
        self.assertNotIn("explicitApkUrl",factual_rows(html+'<a href="/another">Download APK</a>',"Fixture","https://x.invalid/app")[0])
    def test_mod_claim_from_page_title(self):
        html='<meta property="og:title" content="Example MOD APK"><script type="application/ld+json">{"@type":"MobileApplication","name":"Example"}</script>'
        self.assertTrue(factual_rows(html,"Fixture","https://x.invalid/app")[0]["modClaim"])
    def test_rows(self):
        html='<script type="application/ld+json">{"@graph":[{"@type":"MobileApplication","name":"Fixture","softwareVersion":"2.0","image":"https://x.invalid/image.png"},{"@type":"Article","name":"Not app"}]}</script>'
        rows=factual_rows(html,"Fixture","https://x.invalid/app")
        self.assertEqual(len(rows),1)
        self.assertEqual(rows[0]["softwareVersion"],"2.0")
        self.assertFalse(rows[0]["installable"])
        self.assertNotIn("apkUrl",rows[0])
        self.assertNotIn("modified",rows[0])

    def test_size_is_not_a_download_url(self):
        for size in ("https://example.invalid/fixture.apk","unknown"):
            html='<script type="application/ld+json">{"@type":"MobileApplication","name":"Fixture","fileSize":"'+size+'"}</script>'
            self.assertNotIn("fileSize",factual_rows(html,"Fixture","https://x.invalid/app")[0])
        html='<script type="application/ld+json">{"@type":"MobileApplication","name":"Fixture","fileSize":"1.68 GB"}</script>'
        self.assertEqual(factual_rows(html,"Fixture","https://x.invalid/app")[0]["fileSize"],"1.68 GB")
