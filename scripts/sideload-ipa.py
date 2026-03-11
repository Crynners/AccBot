#!/usr/bin/env python3
"""Install .ipa on iPad via Sideloader CLI using wexpect PTY emulation.

Usage:
    python scripts/sideload-ipa.py <apple_id> <password>
    # Triggers 2FA, then polls 2fa-code.txt for the code.
    # Write "resend" to 2fa-code.txt to request SMS resend.
"""

import os
import sys
import re
import time
import wexpect

LOCALAPPDATA = os.environ["LOCALAPPDATA"]
SIDELOADER = os.path.join(LOCALAPPDATA, "Sideloader", "sideloader-cli-x86_64-windows-msvc.exe")
SCRIPT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IPA = os.path.join(SCRIPT_DIR, "ipa-download", "AccBot-unsigned.ipa")
TFA_FILE = os.path.join(SCRIPT_DIR, "2fa-code.txt")

os.environ["SIDELOADER_ANISETTE_URL"] = "http://localhost:6969"

if len(sys.argv) < 3:
    print(f"Usage: {sys.argv[0]} <apple_id> <password>")
    sys.exit(1)

apple_id = sys.argv[1]
password = sys.argv[2]

if not os.path.isfile(SIDELOADER):
    print(f"ERROR: Sideloader not found: {SIDELOADER}")
    sys.exit(1)
if not os.path.isfile(IPA):
    print(f"ERROR: IPA not found: {IPA}")
    sys.exit(1)

# Clean up old 2FA file
if os.path.exists(TFA_FILE):
    os.remove(TFA_FILE)

cmd = f'"{SIDELOADER}" install -i "{IPA}"'
print(f"Running Sideloader...")
sys.stdout.flush()

child = wexpect.spawn(cmd, timeout=300)
all_output = ""

def wait_and_log(patterns, timeout=60):
    global all_output
    idx = child.expect(patterns, timeout=timeout)
    all_output += str(child.before or "")
    return idx

# Apple ID
idx = wait_and_log(["Apple ID:", wexpect.EOF, wexpect.TIMEOUT], timeout=30)
if idx == 0:
    child.sendline(apple_id)
    print("Sent Apple ID")
    sys.stdout.flush()
else:
    print(f"Failed at Apple ID prompt")
    sys.exit(1)

# Password
idx = wait_and_log(["password", "Password", wexpect.EOF, wexpect.TIMEOUT], timeout=30)
if idx in (0, 1):
    child.sendline(password)
    print("Sent password")
    sys.stdout.flush()
else:
    print(f"Failed at password prompt")
    sys.exit(1)

# Check if 2FA or direct success
idx = wait_and_log([
    "code",                                   # 0 - 2FA
    "DeveloperSession created successfully",  # 1 - no 2FA
    "correct password",                       # 2
    wexpect.EOF,                              # 3
    wexpect.TIMEOUT                           # 4
], timeout=60)

if idx == 0:
    print("2FA_NEEDED")
    sys.stdout.flush()

    # Poll for 2FA code file — support "resend" command
    print(f"Waiting for 2FA code in {TFA_FILE} ...")
    sys.stdout.flush()
    deadline = time.time() + 180  # 3 min timeout
    tfa_code = None
    while time.time() < deadline:
        if os.path.exists(TFA_FILE):
            with open(TFA_FILE, "r") as f:
                content = f.read().strip()
            if content:
                os.remove(TFA_FILE)
                if content.lower() == "resend":
                    print("Sending 'resend' to Sideloader...")
                    sys.stdout.flush()
                    child.sendline("resend")
                    # Wait for re-send confirmation, then keep polling
                    time.sleep(3)
                    print("Resend requested. Waiting for new code...")
                    sys.stdout.flush()
                    continue
                else:
                    tfa_code = content
                    break
        time.sleep(1)

    if not tfa_code:
        print("ERROR: 2FA code not provided within 3 minutes.")
        sys.exit(2)

    print(f"Sending 2FA code...")
    sys.stdout.flush()
    child.sendline(tfa_code)

    # Wait for login success
    idx2 = wait_and_log([
        "DeveloperSession created successfully",
        "correct password",
        "ERROR",
        wexpect.EOF,
        wexpect.TIMEOUT
    ], timeout=60)
    if idx2 != 0:
        print(f"Login failed after 2FA.\n{all_output}")
        sys.exit(1)
    print("Login successful!")
    sys.stdout.flush()
elif idx == 1:
    print("Login successful (no 2FA)!")
    sys.stdout.flush()
elif idx == 2:
    print("ERROR: Wrong password!")
    sys.exit(1)
else:
    print(f"Login failed.\n{all_output}")
    sys.exit(1)

# Wait for install to complete
print("Installing (signing + uploading to device)...")
sys.stdout.flush()
try:
    child.expect(wexpect.EOF, timeout=300)
    all_output += str(child.before or "")
except (wexpect.TIMEOUT, wexpect.EOF):
    all_output += str(child.before or "")

# Print cleaned output
clean = re.sub(r'\x1b\[[0-9;]*m', '', all_output)
clean = re.sub(r'\x1b\[\?25[lh]', '', clean)
clean = re.sub(r'\x1b\[K', '', clean)
for line in clean.split('\n'):
    line = line.strip()
    if line:
        print(f"  {line}")

if "error" in all_output.lower() and "installed" not in all_output.lower():
    print("\nInstallation failed.")
    sys.exit(1)
else:
    print("\nDone!")
