"""
Titan Chess Engine — Lichess Bridge
Usage: python run.py
Prerequisites: pip install lichess-bot
"""
import subprocess
import sys
import os

def main():
    # Ensure we're in the lichess-bot directory
    script_dir = os.path.dirname(os.path.abspath(__file__))
    os.chdir(script_dir)
    
    # Check if config exists
    if not os.path.exists("config.yml"):
        print("ERROR: config.yml not found. Copy config.yml.example and add your API token.")
        sys.exit(1)
    
    # Build the engine JAR first
    print("Building Titan Chess Engine...")
    build_result = subprocess.run(
        ["mvn", "-f", "../pom.xml", "package", "-DskipTests"],
        capture_output=True, text=True
    )
    if build_result.returncode != 0:
        print("Build failed!")
        print(build_result.stderr)
        sys.exit(1)
    print("Build successful.")
    
    # Launch lichess-bot
    print("Starting Lichess Bot...")
    subprocess.run([sys.executable, "-m", "lichess_bot", "--config", "config.yml"])

if __name__ == "__main__":
    main()
