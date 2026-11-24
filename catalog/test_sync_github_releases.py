import unittest
from sync_github_releases import select_asset
class ReleaseTest(unittest.TestCase):
    def test_select(self):
        entry={"repository":"owner/repo","assetPattern":r"^app-\d+\.apk$"}
        a={"name":"app-1.apk","browser_download_url":"https://github.com/owner/repo/releases/download/v1/app-1.apk","size":100,"digest":"sha256:"+"a"*64}
        self.assertEqual(select_asset({"assets":[a]},entry),a)
        self.assertIsNone(select_asset({"assets":[a,a]},entry))
        self.assertIsNone(select_asset({"assets":[a],"prerelease":True},entry))
        self.assertIsNone(select_asset({"assets":[dict(a,digest=None)]},entry))
        self.assertIsNone(select_asset({"assets":[dict(a,browser_download_url="https://other.invalid/a.apk")]},entry))
