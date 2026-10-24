import unittest,threading,time,os
from unittest.mock import patch
from pathlib import Path
from PIL import Image,ImageDraw
from vision import Vision,auto_config
from engine import Runner

ROOT=Path(__file__).resolve().parent
FIXTURES=Path(os.environ.get('BULLSEYE_TEST_FIXTURES',str(ROOT/'tests')))
USE_LOCAL_FIXTURES=os.environ.get('BULLSEYE_TEST_NO_PRIVATE_FIXTURES')!='1'
HAS_HOME=USE_LOCAL_FIXTURES and (FIXTURES/'fixtures'/'home-zero.png').is_file()
HAS_VIDEO=USE_LOCAL_FIXTURES and all((FIXTURES/'video-review'/f'frame-{i}.jpg').is_file() for i in (2,3,4,5))
class CoreTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.home=Image.new('RGB',(1920,1080),'#202020')
        if HAS_HOME:
            cls.home.paste(Image.open(FIXTURES/'fixtures'/'home-zero.png').convert('RGB'),(1373,52))
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
    def test_isolated_one_fallback_and_blank(self):
        im=Image.new('RGB',(80,40),'#567080');d=ImageDraw.Draw(im)
        d.rectangle((36,8,42,31),fill='white');d.polygon([(31,12),(36,8),(36,15)],fill='white')
        v=Vision()
        with patch.object(v,'text',return_value=[]):
            self.assertEqual(v.number(im),1)
            self.assertIsNone(v.number(Image.new('RGB',(80,40),'#567080')))

    def flow_config(self):
        points=Vision.targets(self.board)
        return dict(region=(.716,.084,.978,.920),targets=points,start=(1626/1920,871/1080),count_region=(.9175,.263,.9476,.3016),exit_region=Vision.exit_region(points))

    def simulate(self, tickets=1, game_multiplier=1, initial_board=False,
                 switch_works=True, button_visible=True, readings=None, screenshot_count=None):
        config=self.flow_config();config['count']=tickets if screenshot_count is None else screenshot_count
        switch=(.94,.82)
        state={'screen':'board' if initial_board else 'home','left':tickets,
               'multiplier':game_multiplier,'completed':0,'time':0,'captures':0}
        actions=[];reports=[];readings=iter(readings) if readings is not None else None
        def capture():
            state['captures']+=1
            if state['captures']>150:raise AssertionError('Runner failed to finish or pause')
            return self.board if state['screen'] in ('board','reward') else self.home
        def tap(p):
            if state['screen']=='home':
                if p==switch:
                    actions.append('switch')
                    if switch_works:state['multiplier']=1
                else:
                    self.assertEqual(p,config['start'])
                    self.assertGreaterEqual(state['left'],state['multiplier'])
                    actions.append('start');state['left']-=state['multiplier'];state['screen']='board'
            elif state['screen']=='board':
                self.assertIn(p,config['targets']);actions.append('target');state['screen']='reward'
            else:
                actions.append('exit');state['screen']='home';state['completed']+=1
        class FakeText:
            def dismiss_point(self,im,region):
                return ((region[0]+region[2])/2,(region[1]+region[3])/2) if state['screen']=='reward' else None
            def text(self,im):return []
            def number(self,im):return next(readings,state['left']) if readings is not None else state['left']
            def low_balance_button(inner,im,c):
                self.assertLess(state['left'],5,'Normal rounds must not inspect the multiplier')
                return (state['multiplier'],switch) if button_visible else None
        def report(*a):
            reports.append(a)
            if a[0]=='paused':runner.stop()
        def now():state['time']+=3;return state['time']
        runner=Runner(config,self.home,self.board,capture,tap,report);runner.vision=FakeText()
        with patch('engine.time.monotonic',side_effect=now),patch.object(runner.stopped,'wait',return_value=False):runner.run()
        self.assertFalse(any(r[0]=='error' for r in reports),reports)
        return state,actions,reports,runner

    def test_one_ticket_finishes(self):
        state,actions,reports,runner=self.simulate()
        self.assertEqual(actions,['start','target','exit'])
        self.assertEqual((runner.remaining,runner.rounds),(0,1));self.assertEqual(reports[-1][0],'done')

    def test_five_then_four_switches_to_one_and_uses_all(self):
        state,actions,reports,runner=self.simulate(9,5)
        self.assertEqual(actions,['start','target','exit','switch']+['start','target','exit']*4)
        self.assertEqual((runner.remaining,runner.rounds),(0,5));self.assertEqual(reports[-1][0],'done')
        # While on the board, report the last observed count instead of subtracting an assumed cost.
        self.assertEqual(next(r[2] for r in reports if '射击' in r[1]),9)

    def test_four_already_one_does_not_toggle(self):
        state,actions,reports,runner=self.simulate(4)
        self.assertNotIn('switch',actions);self.assertEqual(runner.rounds,4)

    def test_exact_five_finishes_without_multiplier_check(self):
        state,actions,reports,runner=self.simulate(5,5)
        self.assertEqual(actions,['start','target','exit']);self.assertEqual(reports[-1][0],'done')

    def test_initial_board_is_completed_even_with_zero_tickets(self):
        state,actions,reports,runner=self.simulate(0,initial_board=True)
        self.assertEqual(actions,['target','exit']);self.assertEqual((runner.remaining,runner.rounds),(0,1))

    def test_live_count_replaces_stale_screenshot_in_both_directions(self):
        for actual,old in [(2,9),(5,0)]:
            with self.subTest(actual=actual):
                state,actions,reports,runner=self.simulate(actual,5 if actual==5 else 1,screenshot_count=old)
                self.assertEqual(runner.remaining,0);self.assertEqual(reports[-1][0],'done')

    def test_single_false_zero_does_not_finish(self):
        state,actions,reports,runner=self.simulate(1,readings=[0,1,1])
        self.assertEqual(actions,['start','target','exit']);self.assertEqual(runner.rounds,1)

    def test_unreadable_count_pauses_without_click(self):
        state,actions,reports,runner=self.simulate(4,readings=[None,None,None])
        self.assertEqual(actions,[]);self.assertTrue(any(r[0]=='paused' for r in reports))

    def test_unreadable_button_pauses_without_click(self):
        state,actions,reports,runner=self.simulate(4,5,button_visible=False)
        self.assertEqual(actions,[]);self.assertTrue(any(r[0]=='paused' for r in reports))

    def test_switch_not_acknowledged_never_starts(self):
        state,actions,reports,runner=self.simulate(4,5,switch_works=False)
        self.assertEqual(actions,['switch','switch']);self.assertTrue(any(r[0]=='paused' for r in reports))

    def test_resume_clears_old_confirmation(self):
        r=Runner({'count':1},self.home,self.board,lambda:self.home,lambda p:None,lambda *a:None)
        r.count_candidate=0;r.count_seen=2;r.switch_attempts=2;r.pause();r.resume()
        self.assertIsNone(r.count_candidate);self.assertEqual(r.count_seen,0);self.assertEqual(r.switch_attempts,0)

    def test_low_balance_color_and_cost_must_agree(self):
        c=self.flow_config();sx,sy=c['start'];dx=c['targets'][1][0]-c['targets'][0][0];dy=c['targets'][4][1]-c['targets'][0][1]
        for value in (1,5):
            im=Image.new('RGB',self.home.size,'#202020');d=ImageDraw.Draw(im)
            cx=(sx+1.35*dx)*im.width;cy=(sy+.2*dy)*im.height;r=dx*im.width*.25
            d.ellipse((cx-r,cy-r,cx+r,cy+r),fill='#43B52B' if value==1 else '#8028B6')
            # A compact glyph-like patch in the cost area; colored disc and cost are separate evidence.
            x=(sx+.1*dx)*im.width;y=(sy+.18*dy)*im.height
            d.rectangle((x,y,x+12,y+12),fill='white' if value==1 else '#E93232')
            found=Vision.low_balance_button(im,c)
            self.assertIsNotNone(found);self.assertEqual(found[0],value)
            d.rectangle((cx-r-2,cy-r-2,cx+r+2,cy+r+2),fill='black')
            self.assertIsNone(Vision.low_balance_button(im,c))

    @unittest.skipUnless(HAS_VIDEO,'Local video frames are not included in the source repository')
    def test_video_settlement_footer_and_animation(self):
        ps=[(x/720,y/1280) for y in [751,858,966] for x in [225,312,395,483]]
        v=Vision();r=v.exit_region(ps)
        for i,expected in [(2,False),(3,True),(4,True),(5,False)]:
            with self.subTest(frame=i):
                im=Image.open(FIXTURES/'video-review'/f'frame-{i}.jpg')
                self.assertEqual(v.dismiss_point(im,r) is not None,expected)

if __name__=='__main__':unittest.main()
