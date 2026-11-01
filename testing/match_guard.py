"""Temporary Windows idle-sleep prevention and benchmark interruption checks."""
import ctypes
import os
import time
import subprocess


def check_interval(previous_wall, previous_monotonic, wall, monotonic, ac_online):
    wall_gap, mono_gap = wall - previous_wall, monotonic - previous_monotonic
    if not ac_online:
        raise RuntimeError('AC power disconnected; timed run invalidated')
    if not 0 <= wall_gap <= 5 or not 0 <= mono_gap <= 5 or abs(wall_gap-mono_gap) > 2:
        raise RuntimeError(f'Clock/scheduler interruption: wall={wall_gap:.3f}s monotonic={mono_gap:.3f}s')


def ac_power():
    if os.name != 'nt':
        return True
    class PowerStatus(ctypes.Structure):
        _fields_ = [('ac', ctypes.c_ubyte), ('flag', ctypes.c_ubyte),
                    ('percent', ctypes.c_ubyte), ('reserved', ctypes.c_ubyte),
                    ('seconds', ctypes.c_ulong), ('full_seconds', ctypes.c_ulong)]
    status = PowerStatus()
    if not ctypes.windll.kernel32.GetSystemPowerStatus(ctypes.byref(status)):
        raise OSError('Cannot verify AC power')
    return status.ac == 1


class MatchGuard:
    def __enter__(self):
        if not ac_power():
            raise RuntimeError('Connect AC power before starting a timed match')
        self.windows = os.name == 'nt'
        if self.windows:
            # Continuous + system required. No global power setting is changed.
            if not ctypes.windll.kernel32.SetThreadExecutionState(0x80000001):
                raise OSError('Cannot request temporary sleep prevention')
        return self

    def __exit__(self, *args):
        if self.windows:
            ctypes.windll.kernel32.SetThreadExecutionState(0x80000000)

    def wait(self, process, log):
        import json
        previous_wall, previous_mono = time.time(), time.monotonic()
        while True:
            try:
                result = process.wait(timeout=1)
            except subprocess.TimeoutExpired:
                result = None
            wall, mono = time.time(), time.monotonic()
            online = ac_power()
            log.write(json.dumps({'wall':wall, 'monotonic':mono,
                                  'wall_gap':wall-previous_wall,
                                  'monotonic_gap':mono-previous_mono,
                                  'ac_online':online})+'\n')
            log.flush()
            check_interval(previous_wall, previous_mono, wall, mono, online)
            previous_wall, previous_mono = wall, mono
            if result is not None:
                return result
