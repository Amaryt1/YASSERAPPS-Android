import unittest
from provider_sync import read_source
from generate_live_catalog import accept,apk_endpoint
from provider_formats import decode

class FeedTest(unittest.TestCase):
    def config(self):
        return {"name":"Fixture","enabled":True,"authorizationConfirmed":True,"permissionUrl":"https://example.invalid/permission","apiUrl":"https://example.invalid/page1","fields":{"id":"key","name":"title"}}
    def test_pagination_mapping(self):
        pages={"https://example.invalid/page1":{"apps":[{"key":"a","title":"First"}],"next":"/page2"},"https://example.invalid/page2":{"apps":[{"key":"b","title":"Second"}],"next":None}}
        self.assertEqual(read_source(self.config(),lambda u,t:pages[u],lambda n:None),[{"id":"a","name":"First"},{"id":"b","name":"Second"}])
    def test_xml_rss_atom_html(self):
        self.assertEqual(decode('<catalog><app><name>Example</name></app></catalog>',"xml")["apps"][0]["name"],"Example")
        self.assertEqual(decode('<rss><channel><item><title>Example</title></item></channel></rss>',"rss")["apps"][0]["title"],"Example")
        self.assertEqual(decode('<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Example</title></entry><link rel="next" href="/p2"/></feed>',"atom")["next"],"/p2")
        html='<meta property="og:image" content="https://example.invalid/i.png"><script type="application/ld+json">{"@graph":[{"@type":"MobileApplication","name":"Example"},{"@type":"Article","name":"Not app"}]}</script>'
        rows=decode(html,"html-jsonld")["apps"]
        self.assertEqual(len(rows),1)
        self.assertEqual(rows[0]["name"],"Example")
        self.assertEqual(rows[0]["openGraph"]["og:image"],"https://example.invalid/i.png")
        with self.assertRaises(ValueError):decode('<!DOCTYPE root><root/>',"xml")
    def test_rss_uses_mapping(self):
        cfg=dict(self.config(),format="rss",fields={"name":"title"})
        self.assertEqual(read_source(cfg,lambda u,t,k:decode("<rss><channel><item><title>Example</title></item></channel></rss>",k),lambda n:None),[{"name":"Example"}])
    def test_extensionless_apk_endpoint(self):
        self.assertTrue(apk_endpoint("https://example.invalid/download?id=123"))
        self.assertFalse(apk_endpoint("http://example.invalid/download"))
        self.assertFalse(apk_endpoint("https://example.invalid/file.xapk"))
        self.assertFalse(apk_endpoint("https://example.invalid/download#fragment"))
    def test_disabled(self):
        self.assertEqual(read_source({"name":"Fixture","enabled":False}),[])
    def test_permission(self):
        c=self.config();c["authorizationConfirmed"]=False
        with self.assertRaises(ValueError): read_source(c)
    def test_other_origin(self):
        with self.assertRaises(ValueError): read_source(self.config(),lambda u,t:{"apps":[],"next":"https://other.invalid/page"},lambda n:None)
    def test_original_and_invalid_download_rejected(self):
        row={"id":"fixture","name":"Fixture","packageName":"org.example.fixture","category":"app","version":"1","versionCode":1,"modified":True,"modDescription":"نسخة معدلة تجريبية","publisherUrl":"https://example.invalid/release","modEvidenceUrl":"https://example.invalid/changes"}
        self.assertIsNotNone(accept(row,"Fixture","https://example.invalid/license"))
        self.assertIsNone(accept(dict(row,modified=False),"Fixture","https://example.invalid/license"))
        self.assertIsNone(accept(dict(row,apkUrl="https://example.invalid/test.apk"),"Fixture","https://example.invalid/license"))

if __name__=="__main__": unittest.main()
