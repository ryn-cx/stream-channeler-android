# TODO: Validate
"""Saves a screenshot and the UI hierarchy of a connected device over and over until Ctrl+C stops the recording.

The files go to tools/dumps/<package name of the app in front>/<date and time>. Each screenshot and each UI hierarchy
is named after the time it was taken, such as 16-46-10-402.png and 16-46-10-402.xml.

The script only needs Python 3.9 or newer and adb. It runs the same way on Windows, macOS and Linux:

    python tools/record_dumps.py
    python tools/record_dumps.py --serial 192.168.0.2:5555
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime
from pathlib import Path

DUMPS_DIRECTORY = Path(__file__).resolve().parent / "dumps"
DEVICE_HIERARCHY_PATH = "/sdcard/record_dumps.xml"


# TODO: Validate
def find_adb_path() -> str:
    """Returns the adb on the PATH, or the adb in the folder where Android Studio installs the SDK."""
    adb_path_on_path = shutil.which("adb")
    if adb_path_on_path:
        return adb_path_on_path
    if sys.platform == "win32":
        sdk_directory = Path(os.environ["LOCALAPPDATA"]) / "Android" / "Sdk"
    elif sys.platform == "darwin":
        sdk_directory = Path.home() / "Library" / "Android" / "sdk"
    else:
        sdk_directory = Path.home() / "Android" / "Sdk"
    sdk_adb_path = shutil.which("adb", path=str(sdk_directory / "platform-tools"))
    if not sdk_adb_path:
        sys.exit("adb was not found. Install the Android platform tools and add them to the PATH.")
    return sdk_adb_path


# TODO: Validate
def run_adb(adb_command: list[str], adb_arguments: list[str]) -> bytes:
    """Runs adb and returns what it printed. The script stops with adb's error message when adb fails."""
    completed_adb = subprocess.run([*adb_command, *adb_arguments], capture_output=True, check=False)
    if completed_adb.returncode != 0:
        adb_error_message = (completed_adb.stderr or completed_adb.stdout).decode(errors="replace").strip()
        sys.exit(f"adb {' '.join(adb_arguments)} failed: {adb_error_message}")
    return completed_adb.stdout


# TODO: Validate
def get_focused_package_name(adb_command: list[str]) -> str:
    """Returns the package name of the app in front."""
    window_dump = run_adb(adb_command, ["shell", "dumpsys", "window"]).decode(errors="replace")
    # The focus line looks like `mCurrentFocus=Window{1a2b3c u0 com.example.app/com.example.app.MainActivity}`.
    focused_package_match = re.search(r"mCurrentFocus=.* u\d+ ([^/ ]+)/", window_dump)
    if not focused_package_match:
        sys.exit("Couldn't find the app in front. Check that the device is awake and unlocked.")
    return focused_package_match.group(1)


# TODO: Validate
def record_dumps(adb_command: list[str], recording_directory: Path) -> None:
    """Saves a screenshot and the UI hierarchy into the recording directory, again and again, without an end."""
    while True:
        dump_time = datetime.now().strftime("%H-%M-%S-%f")[:-3]
        screenshot = run_adb(adb_command, ["exec-out", "screencap", "-p"])
        (recording_directory / f"{dump_time}.png").write_bytes(screenshot)

        # When uiautomator can't read the screen, it still exits with code 0. It prints "dumped to" only when it wrote
        # the file.
        uiautomator_output = run_adb(adb_command, ["shell", "uiautomator", "dump", DEVICE_HIERARCHY_PATH])
        if b"dumped to" in uiautomator_output:
            hierarchy_xml = run_adb(adb_command, ["exec-out", "cat", DEVICE_HIERARCHY_PATH])
            (recording_directory / f"{dump_time}.xml").write_bytes(hierarchy_xml)
            print(f"{dump_time} screenshot and UI hierarchy")
        else:
            print(f"{dump_time} screenshot only")


# TODO: Validate
def main() -> None:
    argument_parser = argparse.ArgumentParser(description="Records screenshots and UI hierarchies until Ctrl+C.")
    argument_parser.add_argument(
        "--serial", help="The adb serial of the device to record. adb needs it when more than one device is connected."
    )
    serial = argument_parser.parse_args().serial

    adb_command = [find_adb_path()]
    if serial:
        adb_command += ["-s", serial]

    focused_package_name = get_focused_package_name(adb_command)
    recording_directory = DUMPS_DIRECTORY / focused_package_name / datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    recording_directory.mkdir(parents=True)
    print(f"Recording {focused_package_name} to {recording_directory}. Press Ctrl+C to stop.")

    try:
        record_dumps(adb_command, recording_directory)
    except KeyboardInterrupt:
        print("Stopped.")
    finally:
        subprocess.run([*adb_command, "shell", "rm", "-f", DEVICE_HIERARCHY_PATH], check=False)


if __name__ == "__main__":
    main()
