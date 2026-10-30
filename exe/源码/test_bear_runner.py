import unittest
from types import SimpleNamespace
from unittest.mock import Mock,patch
from bear_flow import Frame
from bear_runner import BearRunner
from floating import FloatingControls


class BearRunnerDragTests(unittest.TestCase):
    def run_send(self,when):
        panel=FloatingControls.__new__(FloatingControls)
        panel.closed=False;panel.dragging=False;panel.excluded=True
        panel.revision=0;panel.frame_revision=None;panel.hwnd=123
        panel.window=Mock();panel.window.winfo_x.return_value=0;panel.window.winfo_y.return_value=0
        state={'captures':0,'sent':False};actions=[];reports=[];saves=[]
        io=Mock();runner=BearRunner(io,{'dry':False},reports.append,lambda value:save(value))
        runner.flow.start(10000);runner.flow.phase='SEND';runner.flow.selected=2
        runner.flow.checkedAt=10000;runner.flow.remaining=3;runner.flow.nextPreset=2
        frame=Frame(formation=True,deploy=True,troops=(100,100),deployX=832,deployY=2301)

        def move():
            panel.begin(SimpleNamespace(x_root=30,y_root=40));panel.end(None)

        def raw_capture():
            state['captures']+=1
            if state['captures']>3:raise AssertionError('Runner failed to recover after moving')
            if not state['sent']:
                self.assertEqual(runner.flow.phase,'SEND');self.assertIsNone(runner.flow.pending)
                self.assertEqual((runner.flow.remaining,runner.flow.nextPreset),(3,2))
            else:
                # A drag after submission must not replay DEPLOY or undo its charge.
                self.assertEqual(runner.flow.phase,'VERIFY');self.assertIsNone(runner.flow.pending)
                self.assertEqual((runner.flow.remaining,runner.flow.nextPreset),(2,3))
                runner.stop_event.set()
            if when=='capture' and state['captures']==1:move()
            return object()

        def observe(_):
            if when=='analysis' and state['captures']==1:move()
            return frame

        def raw_action(decision):
            covered=when in ('overlay','overlay_repeat') and state['captures']<=(2 if when=='overlay_repeat' else 1)
            panel.check_input_window(panel.hwnd if covered else 456)
            self.assertFalse(panel.dragging);self.assertEqual(decision.kind,'DEPLOY')
            actions.append(decision.kind);state['sent']=True
            if when=='submitted':move()

        def save(value):
            saves.append(value)
            self.assertEqual((runner.flow.phase,runner.flow.remaining),('VERIFY',2))
            self.assertIsNone(runner.flow.pending)
            if when!='submitted':runner.stop_event.set()

        io.capture.side_effect=lambda:panel.capture_call(raw_capture,(0,0,100,100))
        io.action.side_effect=lambda decision:panel.action_call(raw_action,decision)
        with patch('bear_runner.BearVision') as vision,patch('bear_runner.time.monotonic',return_value=10), \
             patch.object(runner.stop_event,'wait',return_value=False):
            vision.return_value.observe.side_effect=observe;runner.run()
        self.assertEqual(actions,['DEPLOY']);self.assertEqual(saves,[3])
        self.assertEqual(state['captures'],3 if when=='overlay_repeat' else 2)
        self.assertFalse(any('已暂停' in report or '无法开始' in report for report in reports),reports)
        self.assertEqual(sum(report.startswith('等待悬浮条：') for report in reports),0 if when=='submitted' else 1,reports)
        self.assertEqual(sum(report.startswith('已恢复：') for report in reports),0 if when=='submitted' else 1,reports)

    def test_capture_drag_retries_without_resetting_current_batch(self):self.run_send('capture')
    def test_analysis_drag_restores_unsubmitted_decision_before_retry(self):self.run_send('analysis')
    def test_own_overlay_retries_without_leaving_pending_action(self):self.run_send('overlay')
    def test_drag_after_submitted_deploy_never_replays_or_recharges_it(self):self.run_send('submitted')
    def test_repeated_input_wait_is_reported_once_and_cleared_on_recovery(self):self.run_send('overlay_repeat')


if __name__=='__main__':unittest.main()
