import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch
from PIL import Image
from engine import Runner
from frame_guard import RetryFrame
from floating import FloatingControls


class FloatingTests(unittest.TestCase):
    def panel(self, excluded=True):
        panel=FloatingControls.__new__(FloatingControls)
        panel.closed=False;panel.dragging=False;panel.excluded=excluded;panel.hwnd=123
        panel.revision=0;panel.frame_revision=0
        panel.title_text='测试  ⠿ 拖动';panel.display_text=panel.title_text;panel.handle=Mock()
        panel.window=Mock();panel.window.winfo_x.return_value=0;panel.window.winfo_y.return_value=0
        panel.bounds=Mock(return_value=(20,20,290,132))
        return panel

    def test_capture_keeps_overlay_visible(self):
        panel=self.panel();capture=Mock(return_value='frame')
        with patch('window_io.u') as user32:
            self.assertEqual(panel.capture_call(capture,(0,0,540,1200)),'frame')
            user32.ShowWindow.assert_not_called();user32.ShowWindowAsync.assert_not_called()
        capture.assert_called_once()

    def test_input_keeps_overlay_visible_and_preserves_callback_guards(self):
        panel=self.panel();action=Mock(side_effect=RuntimeError('occluded'))
        with patch('window_io.u') as user32:
            with self.assertRaisesRegex(RuntimeError,'occluded'):panel.action_call(action,(30,40))
            user32.ShowWindow.assert_not_called();user32.ShowWindowAsync.assert_not_called()
        action.assert_called_once_with((30,40))

    def test_failed_exclusion_rejects_overlap_before_capture(self):
        panel=self.panel(False);capture=Mock()
        with self.assertRaisesRegex(RetryFrame,'拖到游戏画面外'):
            panel.capture_call(capture,lambda:(0,0,540,1200))
        capture.assert_not_called()

    def test_failed_exclusion_allows_panel_outside_capture_area(self):
        panel=self.panel(False);capture=Mock(return_value='frame')
        self.assertEqual(panel.capture_call(capture,(290,0,540,1200)),'frame')
        capture.assert_called_once()

    def test_closed_or_dragged_panel_does_not_capture_or_click(self):
        for state in ('closed','dragging'):
            panel=self.panel();setattr(panel,state,True);callback=Mock()
            with self.assertRaises(RuntimeError):panel.capture_call(callback,(0,0,540,1200))
            with self.assertRaises(RuntimeError):panel.action_call(callback,(10,10))
            callback.assert_not_called()

    def test_stop_during_capture_discards_frame(self):
        panel=self.panel()
        def capture():panel.closed=True;return 'stale frame'
        with self.assertRaisesRegex(RuntimeError,'任务已停止'):panel.capture_call(capture,(0,0,540,1200))

    def move(self,panel):
        panel.begin(SimpleNamespace(x_root=30,y_root=40));panel.end(None)

    def test_drag_finishing_during_capture_still_discards_frame(self):
        panel=self.panel()
        def capture():self.move(panel);return 'stale frame'
        with self.assertRaises(RetryFrame):panel.capture_call(capture,(0,0,540,1200))

    def test_drag_after_capture_blocks_old_action_until_recapture(self):
        panel=self.panel();action=Mock()
        panel.capture_call(lambda:'old',(0,0,540,1200));self.move(panel)
        with self.assertRaises(RetryFrame):panel.action_call(action)
        action.assert_not_called()
        panel.capture_call(lambda:'new',(0,0,540,1200));panel.action_call(action)
        action.assert_called_once()

    def test_own_overlay_blocks_input_until_moved_and_recaptured(self):
        panel=self.panel()
        with self.assertRaises(RetryFrame):panel.check_input_window(panel.hwnd)
        self.move(panel)
        with self.assertRaises(RetryFrame):panel.check_input_window(456)
        panel.capture_call(lambda:'new',(0,0,100,100));panel.check_input_window(456)

    def test_drag_after_input_does_not_turn_submitted_action_into_retry(self):
        panel=self.panel();submitted=[]
        def action():submitted.append('click');self.move(panel)
        panel.action_call(action)
        self.assertEqual(submitted,['click'])
        with self.assertRaises(RetryFrame):panel.action_call(action)
        self.assertEqual(submitted,['click'])

    def test_wait_message_is_short_deduplicated_and_restores_title_without_resize(self):
        panel=self.panel()
        for reason,expected in (
            ('请把悬浮条拖到游戏画面外，移开后继续','请拖到游戏画面外'),
            ('请把悬浮条移开目标按钮，移开后继续','请移开悬浮条 · 拖动'),
            ('正在移动悬浮条，松手后重新识别','移动中，松手后继续')):
            panel.handle.reset_mock();panel.message(reason);panel.message(reason)
            panel.handle.configure.assert_called_once_with(text=expected)
        panel.handle.reset_mock();panel.message();panel.message()
        panel.handle.configure.assert_called_once_with(text=panel.title_text)
        panel.window.geometry.assert_not_called()

    def test_repeated_input_wait_reports_once_until_action_succeeds(self):
        frame=Image.new('RGB',(100,100));reports=Mock()
        tap=Mock(side_effect=[RetryFrame('请把悬浮条移开目标按钮')]*3+[None])
        runner=Runner({'count':3},frame,frame,Mock(),tap,reports)
        for _ in range(3):self.assertFalse(runner.click((.5,.5)))
        self.assertTrue(runner.click((.5,.5)))
        self.assertEqual([call.args[0] for call in reports.call_args_list],['waiting','running'])
        self.assertIn('移开',reports.call_args_list[0].args[1])
        self.assertEqual(runner.wait_message,'');self.assertTrue(runner.gate.is_set())

    def run_partial_round(self,move_during_capture=False,move_during_analysis=False,hold_drag=False,own_overlay=False,unsupported_overlap=False):
        panel=self.panel();frame=Image.new('RGB',(100,100));actions=[];reports=[];state={'captures':0,'waits':0}
        if unsupported_overlap:panel.excluded=False
        config={'count':3,'region':(0,0,1,1),'exit_region':(0,.9,1,1),
                'start':(.8,.8),'targets':[(.2,.5),(.4,.5),(.6,.5)]}
        def raw_capture():
            state['captures']+=1
            if state['captures']>3:raise AssertionError('No recovery after dragging')
            if move_during_capture and state['captures']==1:self.move(panel)
            return frame
        def raw_tap(point):
            panel.check_input_window(panel.hwnd if own_overlay and state['captures']==1 else 456)
            self.assertFalse(panel.dragging)
            self.assertEqual(state['captures'],2 if move_during_capture or move_during_analysis or own_overlay else 1)
            self.assertTrue(runner.in_round);self.assertEqual(runner.rounds,4)
            self.assertEqual(runner.visited,{0,1});self.assertEqual(runner.pending,2)
            self.assertEqual(runner.retries,0,'A discarded frame must not consume a tap retry')
            actions.append(point);runner.stop()
        runner=Runner(config,frame,frame,lambda:panel.capture_call(raw_capture,(0,0,100,100)),
                      lambda point:panel.action_call(raw_tap,point),lambda *args:reports.append(args))
        runner.in_round=True;runner.rounds=4;runner.visited={0,1};runner.pending=2
        vision=Mock();vision.dismiss_point.return_value=None
        def text(_):
            if move_during_analysis and state['captures']==1:self.move(panel)
            return []
        vision.text.side_effect=text;runner.vision=vision
        if hold_drag:panel.begin(SimpleNamespace(x_root=30,y_root=40))
        def wait(_):
            state['waits']+=1
            if hold_drag and state['waits']==3:panel.end(None)
            if unsupported_overlap and state['waits']==3:
                self.move(panel);panel.bounds.return_value=(200,200,470,312)
            if state['waits']>10:raise AssertionError('Runner failed to resume')
            return False
        times=iter(range(100,200,3))
        similarities=iter([0,1,1,1]*3)  # board page; the pending third target is still present
        with patch('engine.time.monotonic',side_effect=lambda:next(times)), \
             patch('engine.Vision.similarity',side_effect=lambda *_:next(similarities)), \
             patch.object(runner.stopped,'wait',side_effect=wait):runner.run()
        self.assertEqual(actions,[config['targets'][2]])
        self.assertFalse(any(r[0] in ('error','paused') for r in reports),reports)
        self.assertEqual(sum(r[0]=='waiting' for r in reports),1,reports)
        self.assertEqual(sum(r[1]=='已恢复，继续当前进度' for r in reports),1,reports)

    def test_runner_preserves_partial_round_after_drag_during_capture(self):
        self.run_partial_round(move_during_capture=True)

    def test_runner_recaptures_if_drag_ends_after_image_analysis(self):
        self.run_partial_round(move_during_analysis=True)

    def test_runner_waits_until_drag_released_without_losing_partial_round(self):
        self.run_partial_round(hold_drag=True)

    def test_runner_recovers_from_own_overlay_without_losing_partial_round(self):
        self.run_partial_round(own_overlay=True)

    def test_runner_without_capture_exclusion_waits_until_overlay_leaves_game(self):
        self.run_partial_round(unsupported_overlap=True)


if __name__=='__main__':unittest.main()
