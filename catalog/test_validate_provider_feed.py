import unittest
from validate_provider_feed import validate
class ProviderFeedPreflight(unittest.TestCase):
    def row(self):
        return {"id":"fixture","name":"Synthetic fixture","packageName":"org.example.fixture","category":"app","version":"1.0","versionCode":1,"modified":True,"modDescription":"بيانات اختبار فقط","modEvidenceUrl":"https://example.invalid/changes","publisherUrl":"https://example.invalid/release"}
    def doc(self,items):
        return {"schemaVersion":1,"source":"APKVision","reviewedByOwner":True,"permissionUrl":"https://example.invalid/permission","apps":items}
    def test_metadata_only(self):
        self.assertEqual(validate(self.doc([self.row()]))["metadataOnly"],1)
    def test_binary_candidate_not_verified(self):
        row=dict(self.row(),apkUrl="https://example.invalid/test.apk",sha256="a"*64,signerSha256="b"*64,sizeBytes=100)
        r=validate(self.doc([row]))
        self.assertEqual(r["binaryCandidates"],1)
        self.assertEqual(r["verifiedBinaries"],0)
        self.assertEqual(r["published"],0)
    def test_bad_records(self):
        for row in [dict(self.row(),modified=False),dict(self.row(),apkUrl="https://example.invalid/a.apk"),dict(self.row(),versionCode=0)]:
            with self.assertRaises(ValueError):validate(self.doc([row]))
    def test_duplicate(self):
        with self.assertRaises(ValueError):validate(self.doc([self.row(),self.row()]))
    def test_unreviewed_and_unknown_source(self):
        for doc in [dict(self.doc([]),reviewedByOwner=False),dict(self.doc([]),source="Unknown"),dict(self.doc([]),permissionUrl="")]:
            with self.assertRaises(ValueError):validate(doc)
