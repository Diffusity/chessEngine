import unittest
from match_guard import check_interval, MatchGuard
from unittest.mock import patch, Mock


class MatchGuardTest(unittest.TestCase):
    def test_healthy_tick(self):
        check_interval(100, 10, 101, 11, True)

    def test_suspension_or_scheduler_gap_invalidates_run(self):
        for wall, mono in [(160, 70), (160, 11), (101, 70), (99, 11)]:
            with self.subTest(wall=wall, mono=mono), self.assertRaises(RuntimeError):
                check_interval(100, 10, wall, mono, True)

    def test_unplug_invalidates_run(self):
        with self.assertRaises(RuntimeError):
            check_interval(100, 10, 101, 11, False)

    def test_refuses_to_start_on_battery(self):
        with patch('match_guard.ac_power', return_value=False):
            with self.assertRaises(RuntimeError):
                with MatchGuard(): pass

    def test_releases_temporary_request_after_failure(self):
        kernel = Mock()
        with patch('match_guard.os.name', 'nt'), patch('match_guard.ac_power', return_value=True), patch('match_guard.ctypes.windll', create=True) as win:
            win.kernel32 = kernel
            with self.assertRaises(ValueError):
                with MatchGuard(): raise ValueError('test')
            self.assertEqual(kernel.SetThreadExecutionState.call_args_list[-1].args, (0x80000000,))
