import unittest,threading,time,os
from unittest.mock import patch
from pathlib import Path
from PIL import Image,ImageDraw
from vision import Vision,auto_config
from engine import Runner

ROOT=Path(__file__).resolve().parent.parent
USE_LOCAL_FIXTURES=os.environ.get('BULLSEYE_TEST_NO_PRIVATE_FIXTURES')!='1'
HAS_HOME=USE_LOCAL_FIXTURES and (ROOT/'tests'/'fixtures'/'home-zero.png').is_file()
HAS_VIDEO=USE_LOCAL_FIXTURES and all((ROOT/'tests'/'video-review'/f'frame-{i}.jpg').is_file() for i in (2,3,4,5))
class CoreTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.home=Image.new('RGB',(1920,1080),'#202020')
        if HAS_HOME:
            cls.home.paste(Image.open(ROOT/'tests'/'fixtures'/'home-zero.png').convert('RGB'),(1373,52))
        else:
            d=ImageDraw.Draw(cls.home)
            d.rectangle((1373,52,1877,984),fill='#507698')
            d.rounded_rectangle((1520,845,1720,940),radius=15,fill='#E7B81C',outline='white',width=5)
            d.text((1607,865),'START',fill='black')
        cls.board=cls.home.copy();d=ImageDraw.Draw(cls.board)
        d.rectangle((1400,550,1850,960),fill='#352B28')
        for y in [556,692,822]:
            for x in [85,196,310,421]:
                cx,cy=x+1373,y+52
                d.ellipse((cx-45,cy-45,cx+45,cy+45),fill='#B84532')
                d.ellipse((cx-35,cy-35,cx+35,cy+35),fill='#E4E9E7')
                d.ellipse((cx-25,cy-25,cx+25,cy+25),fill='#B84532')
        (ROOT/'tests').mkdir(exist_ok=True)
        cls.home.save(ROOT/'tests'/'synthetic-home.png');cls.board.save(ROOT/'tests'/'synthetic-board.png')
    def test_twelve_target_grid(self):
        points=Vision.targets(self.board)
        self.assertEqual(len(points),12)
        self.assertAlmostEqual(points[0][0]*1920,1458,delta=3)
        self.assertAlmostEqual(points[-1][1]*1080,874,delta=3)
    def test_reject_incomplete_board(self):
        im=self.board.copy();d=ImageDraw.Draw(im);d.rectangle((1740,550,1860,950),fill='black')
        self.assertEqual(Vision.targets(im),[])
    @unittest.skipUnless(HAS_HOME,'Local home screenshot is not included in the source repository')
    def test_auto_calibration_ocr(self):
        c=auto_config(self.home,self.board,Vision())
        self.assertEqual(c['count'],0)
        self.assertEqual(len(c['targets']),12)
        self.assertAlmostEqual(c['start'][0]*1920,1626,delta=20)
        self.assertNotIn('multiplier_point',c)
    @unittest.skipUnless(HAS_HOME,'Local home screenshot is not included in the source repository')
    def test_hidden_multiplier_does_not_block_setup(self):
        home=self.home.copy();ImageDraw.Draw(home).rectangle((1735,825,1835,935),fill='black')
        c=auto_config(home,self.board,Vision())
        self.assertEqual(c['count'],0)
        self.assertEqual(len(c['targets']),12)
        self.assertNotIn('multiplier_point',c)
    def test_pause_and_stop_block_clicks(self):
        actions=[];r=Runner({'count':3},self.home,self.board,lambda:self.home,lambda p:actions.append(p),lambda *a:None)
        r.pause();self.assertFalse(r.click((.5,.5)));self.assertEqual(actions,[])
        r.resume();self.assertTrue(r.click((.5,.5)));self.assertEqual(len(actions),1)
        r.stop();r.resume();self.assertFalse(r.click((.5,.5)));self.assertEqual(len(actions),1)
    def test_dark_overlay_not_target(self):
        a=Vision.patch(self.board,(1458/1920,608/1080))
        b=Image.eval(a,lambda x:int(x*.3))
        self.assertLess(Vision.similarity(b,a),.5)
    def test_blank_count_is_not_zero(self):
        self.assertIsNone(Vision().number(Image.new('RGB',(80,40),'#567080')))
    def test_full_round_stops_at_budget(self):
        self.check_round(False)
    def test_user_selected_five_without_multiplier_ocr(self):
        self.check_round(False,5)
    def test_settlement_returns_and_starts_next_round(self):
        self.check_round(False,1,2)
    def test_resume_initial_full_board_and_sync_count(self):
        self.check_round(True)
    def flow_config(self):
        points=Vision.targets(self.board)
        return dict(region=(.716,.084,.978,.920),targets=points,start=(1626/1920,871/1080),count_region=(.9175,.263,.9476,.3016),exit_region=Vision.exit_region(points))
    def check_round(self,initial_board,multiplier=1,total_rounds=1):
        config=self.flow_config();config.update(count=multiplier*total_rounds,multiplier=multiplier)
        state={'screen':'board' if initial_board else 'home','completed':0,'time':0};actions=[];reports=[]
        # The reward can open while many targets remain. The exit footer must
        # take precedence over those target matches, just as in the user's video.
        def capture():return self.board if state['screen'] in ('board','reward') else self.home
        def tap(p):
            actions.append(p)
            if state['screen']=='home':state['screen']='board'
            elif state['screen']=='board':state['screen']='reward'
            else:state.update(screen='home',completed=state['completed']+1)
        class FakeText:
            def dismiss_point(self,im,region):
                return ((region[0]+region[2])/2,(region[1]+region[3])/2) if state['screen']=='reward' else None
            def text(self,im):
                return []
            def number(self,im):return (total_rounds-state['completed'])*multiplier
        def now():state['time']+=3;return state['time']
        runner=Runner(config,self.home,self.board,capture,tap,lambda *a:reports.append(a));runner.vision=FakeText()
        with patch('engine.time.monotonic',side_effect=now):runner.run()
        self.assertEqual(len(actions),3*total_rounds-int(initial_board))
        if not initial_board:self.assertEqual(actions[0],config['start'])
        self.assertIn(actions[0 if initial_board else 1],config['targets'])
        self.assertEqual(reports[-1][0],'done')
        self.assertEqual((runner.remaining,runner.rounds),(0,total_rounds))
        self.assertEqual(sum('自动返回活动首页' in item[1] for item in reports),total_rounds)
    def test_less_than_selected_multiplier_stops_without_click(self):
        config=self.flow_config();config.update(count=3,multiplier=5)
        actions=[];reports=[]
        class FakeText:
            def dismiss_point(self,*args):return None
            def text(self,*args):return []
            def number(self,*args):return 3
        runner=Runner(config,self.home,self.board,lambda:self.home,actions.append,lambda *a:reports.append(a));runner.vision=FakeText();runner.run()
        self.assertEqual(actions,[])
        self.assertEqual(reports[-1][0],'stopped')
        self.assertEqual(runner.remaining,3)
        self.assertIn('都改为 1 倍',reports[-1][1])
    @unittest.skipUnless(HAS_VIDEO,'Local video frames are not included in the source repository')
    def test_video_settlement_footer_and_animation(self):
        ps=[(x/720,y/1280) for y in [751,858,966] for x in [225,312,395,483]]
        v=Vision();r=v.exit_region(ps)
        for i,expected in [(2,False),(3,True),(4,True),(5,False)]:
            with self.subTest(frame=i):
                im=Image.open(ROOT/'tests'/'video-review'/f'frame-{i}.jpg')
                self.assertEqual(v.dismiss_point(im,r) is not None,expected)

if __name__=='__main__':unittest.main()
