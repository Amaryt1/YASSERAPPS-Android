import unittest,urllib.robotparser
from import_liteapks import batch
class ImportTest(unittest.TestCase):
    def test_incremental_dedupe_and_rate_limit(self):
        urls=["https://liteapks.com/a.html","https://liteapks.com/b.html"]
        rp=urllib.robotparser.RobotFileParser();rp.parse(["User-agent: *","Allow: /"])
        html='<meta property="og:title" content="Fixture MOD APK"><script type="application/ld+json">{"@type":"MobileApplication","name":"Fixture","softwareVersion":"1.0","applicationCategory":"GameApplication"}</script>'
        records={};done=set()
        def fetch(url,domain):return ("200",html) if url==urls[0] else ("429",None)
        n,status=batch(urls,records,done,rp,1,5,100,fetch,lambda _:None)
        self.assertEqual((n,status),(2,"source_denied_or_rate_limited"))
        self.assertEqual(done,{urls[0]})
        self.assertFalse(records[urls[0]]["installable"])
        n,status=batch(urls,records,done,rp,1,5,100,fetch,lambda _:None)
        self.assertEqual(n,1)
        self.assertEqual(len(records),1)
