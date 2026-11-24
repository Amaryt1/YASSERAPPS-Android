import unittest,io,tempfile,hashlib
from pathlib import Path
from urllib.robotparser import RobotFileParser
from verify_source_candidates import fetch_release,ResolutionError
class Response(io.BytesIO):
    def __init__(self,data,ctype):
        super().__init__(data);self.headers={"Content-Type":ctype,"Content-Length":str(len(data))}
class ResolverTest(unittest.TestCase):
    def test_public_chain(self):
        rp=RobotFileParser();rp.parse(["User-agent: *","Allow: /"])
        binary=b"PK\x03\x04fixture"
        def opener(req,timeout):
            if req.full_url.endswith("/landing"):return Response(b'<html><a href="/file">Download APK</a></html>',"text/html")
            self.assertEqual(req.full_url,"https://example.invalid/file")
            return Response(binary,"application/octet-stream")
        with tempfile.TemporaryDirectory() as tmp:
            url,digest,n=fetch_release("https://example.invalid/landing",Path(tmp)/"test.apk",rp,1,opener,lambda _:None)
            self.assertEqual(url,"https://example.invalid/file")
            self.assertEqual(digest.hexdigest(),hashlib.sha256(binary).hexdigest())
            self.assertEqual(n,len(binary))
    def test_ambiguous_or_cross_origin(self):
        rp=RobotFileParser();rp.parse(["User-agent: *","Allow: /"])
        def opener(req,timeout):return Response(b'<html><a href="https://other.invalid/file">Download APK</a></html>',"text/html")
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(ResolutionError,"ambiguous_or_missing"):
                fetch_release("https://example.invalid/landing",Path(tmp)/"test.apk",rp,1,opener,lambda _:None)
    def test_robots(self):
        rp=RobotFileParser();rp.parse(["User-agent: *","Disallow: /file"])
        def opener(req,timeout):return Response(b'<html><a href="/file">Download APK</a></html>',"text/html")
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(ResolutionError,"robots_disallowed"):
                fetch_release("https://example.invalid/landing",Path(tmp)/"test.apk",rp,1,opener,lambda _:None)
