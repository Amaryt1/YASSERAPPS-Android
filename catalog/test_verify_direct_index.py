import unittest
from verify_direct_index import queue,DAY
class QueueTest(unittest.TestCase):
    def test_priority_and_balance(self):
        def row(pkg,kind,stamp=0):return dict(packageName=pkg,category=kind,updatedAt=stamp,versionCode=1)
        rows=[row("test.alpha","app"),row("test.game","game"),row("com.whatsapp","app"),row("test.beta","app")]
        result=queue(rows,{}, {},10*DAY)
        self.assertEqual(result[0]["packageName"],"com.whatsapp")
        self.assertEqual(len({x["packageName"] for x in result}),4)
        self.assertEqual(result[2]["category"],"game")
        result=queue(rows,{"com.whatsapp":10*DAY},{},10*DAY)
        self.assertNotIn("com.whatsapp",[x["packageName"] for x in result])
    def test_refresh(self):
        row=dict(packageName="test.app",category="app",updatedAt=0,versionCode=2)
        old={"test.app":dict(versionCode=1)}
        self.assertEqual(queue([row],{"test.app":8*DAY},old,10*DAY),[row])
        self.assertEqual(queue([row],{"test.app":10*DAY},old,10*DAY),[])
if __name__=="__main__":unittest.main()
