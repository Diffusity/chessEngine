import unittest
from run_match import command
from prepare_rehearsal import rehearsal_config
from continue_validation import resume_command


class MatchConfigurationTest(unittest.TestCase):
    def test_experimental_gate_is_applied_only_to_candidate(self):
        cmd = command('fastchess.exe', 'java.exe', 10, candidate_full_soft_clock=True)
        engine_args = [x for x in cmd if x.startswith('args=')]
        self.assertIn('-Dtitan.experimental.fullSoftClock=true', engine_args[0])
        self.assertNotIn('fullSoftClock', engine_args[1])

    def test_local_opening_positions_keep_colour_pairs(self):
        cmd = command('fastchess.exe', 'java.exe', 10, 'positions.epd')
        self.assertIn('file=positions.epd', cmd)
        self.assertIn('format=epd', cmd)
        self.assertIn('order=sequential', cmd)
        self.assertEqual(cmd[cmd.index('-games')+1], '2')

    def test_resume_keeps_saved_results_and_logs(self):
        cmd = resume_command("fastchess.exe")
        self.assertIn("stats=true", cmd)
        self.assertIn("file=state.json", cmd)
        self.assertIn("append=true", cmd)
        self.assertIn("file=engine-resumed.log", cmd)

    def test_full_games_and_swapped_colours(self):
        cmd = command("fastchess.exe", "java.exe", 10)
        self.assertIn("tc=180+2", cmd)
        for flag in ["-draw", "-resign", "-maxmoves", "-tb", "-noswap", "-openings"]:
            self.assertNotIn(flag, cmd)
        self.assertEqual(cmd[cmd.index("-concurrency") + 1], "1")
        self.assertEqual(cmd[cmd.index("-rounds") + 1], "10")
        self.assertEqual(cmd[cmd.index("-games") + 1], "2")

    def test_rehearsal_is_casual_exact_control_and_allowlisted(self):
        original = {"token": "TEST_ONLY", "engine": {"online_moves": {"cloud": {"enabled": True}}},
                    "matchmaking": {"allow_matchmaking": True}}
        for base, inc in [(180, 2), (600, 5)]:
            cfg = rehearsal_config(original, "authorised-test-user", base, inc, "../engine", "titan.jar")
            self.assertEqual(cfg["token"], "TEST_ONLY")
            self.assertFalse(cfg["matchmaking"]["allow_matchmaking"])
            self.assertFalse(cfg["engine"]["online_moves"]["cloud"]["enabled"])
            challenge = cfg["challenge"]
            self.assertEqual(challenge["allow_list"], ["authorised-test-user"])
            self.assertEqual(challenge["modes"], ["casual"])
            self.assertEqual(challenge["min_base"], challenge["max_base"])
            self.assertEqual(challenge["min_increment"], challenge["max_increment"])
        self.assertTrue(original["matchmaking"]["allow_matchmaking"])

    def test_missing_opponent_or_wrong_control_is_rejected(self):
        for opponent, base, inc in [("", 180, 2), ("x", 180, 5)]:
            with self.assertRaises(ValueError):
                rehearsal_config({}, opponent, base, inc, "../engine", "titan.jar")


if __name__ == "__main__":
    unittest.main()
