import threading,unittest
from unittest.mock import Mock,patch
from bear_runner import BearRunner
from bear_flow import Decision
from window_io import WindowIO

class LifecycleTests(unittest.TestCase):
    def runner(self):return BearRunner(Mock(),{},Mock(),Mock())
    def test_stop_during_countdown_prevents_start(self):
        r=self.runner();r.stop()
        with patch('bear_runner.BearVision') as vision:r.start();vision.assert_not_called()
        self.assertIsNone(r.thread);r.io.capture.assert_not_called()
    def test_pause_resume_before_start_has_no_deadline_error(self):
        r=self.runner();r.pause();r.resume();self.assertTrue(r.gate.is_set());self.assertIsNone(r.thread)
    def test_initialization_failure_stops_and_reports(self):
        r=self.runner()
        with patch('bear_runner.BearVision',side_effect=ValueError('bad template')):
            r.start();r.thread.join(2)
        self.assertFalse(r.thread.is_alive());self.assertTrue(r.stop_event.is_set())
        self.assertTrue(any('bad template' in c.args[0] for c in r.report.call_args_list));r.io.capture.assert_not_called()
    def test_start_is_idempotent_and_stop_blocks_late_observation(self):
        r=self.runner();entered=threading.Event();release=threading.Event()
        def observe(_):entered.set();release.wait(2);return Mock()
        with patch('bear_runner.BearVision') as vision:
            vision.return_value.observe.side_effect=observe
            r.start();self.assertTrue(entered.wait(2));first=r.thread;r.start();self.assertIs(r.thread,first)
            r.stop();release.set();r.thread.join(2);self.assertFalse(r.thread.is_alive());r.io.action.assert_not_called()
    def test_resume_preserves_session_deadline(self):
        r=self.runner();r.flow.start(0);r.thread=Mock();deadline=r.flow.deadline;r.pause();r.resume()
        self.assertEqual(r.flow.deadline,deadline)
    def io(self):
        io=WindowIO(123);io.rect=Mock(return_value=(0,0,540,1200));io.last_rect=(0,0,540,1200);io.active=Mock(return_value=True);return io
    def test_scroll_does_not_press_through_foreign_overlay(self):
        io=self.io()
        with patch('window_io.u') as user32,patch('pynput.mouse.Controller') as mouse:
            user32.GetAncestor.return_value=456
            with self.assertRaises(RuntimeError):io.action(Decision('SCROLL'))
            mouse.return_value.press.assert_not_called()
    def test_scroll_releases_button_if_overlay_appears(self):
        io=self.io()
        with patch('window_io.u') as user32,patch('pynput.mouse.Controller') as mouse:
            user32.GetAncestor.side_effect=[123,456]
            with self.assertRaises(RuntimeError):io.action(Decision('SCROLL'))
            mouse.return_value.press.assert_called_once();mouse.return_value.release.assert_called_once()

if __name__=='__main__':unittest.main()
