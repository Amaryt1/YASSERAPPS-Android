import unittest
from provider_sync import read_source
from generate_live_catalog import accept

class FeedTest(unittest.TestCase):
    def config(self):
        return {"name":"Fixture","enabled":True,"authorizationConfirmed":True,"permissionUrl":"https://example.invalid/permission","apiUrl":"https://example.invalid/page1","fields":{"id":"key","name":"title"}}
    def test_pagination_mapping(self):
        pages={"https://example.invalid/page1":{"apps":[{"key":"a","title":"First"}],"next":"/page2"},"https://example.invalid/page2":{"apps":[{"key":"b","title":"Second"}],"next":None}}
        self.assertEqual(read_source(self.config(),lambda u,t:pages[u],lambda n:None),[{"id":"a","name":"First"},{"id":"b","name":"Second"}])
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
