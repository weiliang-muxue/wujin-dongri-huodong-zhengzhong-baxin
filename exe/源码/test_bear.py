import unittest
from bear_flow import MarchFlow,Frame,Row

class MarchTests(unittest.TestCase):
    def ready(self,used=-1):
        flow=MarchFlow();flow.dry=False;flow.start(0)
        f=Frame(map=True,marchHidden=True,marchUsed=used,shortcutX=999,shortcutY=1060)
        flow.next(f,0);d=flow.next(f,700)
        if d.kind=='NAVIGATE':flow.complete(True,700)
        return flow,d
    def test_counter_priority_and_all_capacities(self):
        for used in range(7):
            flow,d=self.ready(used);self.assertEqual(d.kind,'WAIT' if used==6 else 'NAVIGATE')
            if used<6:self.assertEqual(flow.remaining,6-used)
    def test_hidden_direct_no_expand(self):
        flow,d=self.ready();self.assertEqual((d.kind,d.x,d.y),('NAVIGATE',999,1060));self.assertEqual(flow.remaining,6)
    def test_six_presets_and_stay_list(self):
        flow,_=self.ready();now=1400;presets=[]
        listing=Frame(header=True,rallyList=True,autoJoin=True,rows=[Row(0,950,800,False),Row(1,950,1400)])
        formation=Frame(formation=True,deploy=True,savedPreset=[True]*6,troops=(1,150000),deployX=832,deployY=2301)
        for i in range(6):
            d=flow.next(listing,now);self.assertEqual((d.kind,d.y),('JOIN',1400));flow.complete(True,now);now+=700
            d=flow.next(formation,now);self.assertEqual(d.kind,'PRESET');presets.append(d.x);flow.complete(True,now);now+=700
            d=flow.next(formation,now);self.assertEqual(d.kind,'DEPLOY');flow.complete(True,now);now+=700
            d=flow.next(listing,now);self.assertEqual(d.kind,'WAIT');now+=700
            self.assertEqual(flow.phase,'RETURN_MAP' if i==5 else 'FIND')
        self.assertEqual(presets,[98,209,319,431,542,654]);self.assertEqual(flow.nextPreset,0)
    def test_stop_and_failed_action(self):
        flow,d=self.ready();flow.stop();flow.complete(True,1000);self.assertFalse(flow.running)
        flow=MarchFlow();flow.dry=False;flow.start(0);f=Frame(map=True,marchHidden=True,shortcutX=999,shortcutY=1060)
        flow.next(f,0);flow.next(f,700);flow.complete(False,700);self.assertFalse(flow.running)
    def test_dry_never_clicks(self):
        flow=MarchFlow();flow.start(0)
        for t in range(0,1800000,1000):self.assertEqual(flow.next(Frame(map=True,marchHidden=True),t).kind,'WAIT')
        self.assertEqual(flow.next(Frame(),1800000).kind,'PAUSE')

if __name__=='__main__':unittest.main()
