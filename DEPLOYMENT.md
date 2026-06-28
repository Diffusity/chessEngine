# Deploying Titan Chess Engine to Lichess

This guide explains how to deploy the Titan Chess Engine to Lichess so users can play against it.
We use the official [lichess-bot](https://github.com/lichess-bot-devs/lichess-bot) bridge to connect the engine's UCI interface to the Lichess Bot API.

## Prerequisites

1. **Java 17+**: Required to run the Titan engine JAR.
2. **Python 3.10+**: Required to run `lichess-bot`.
3. **Git**: Required to clone the `lichess-bot` repository.
4. **Lichess Account**: A **dedicated** Lichess account for the bot (do **not** use your personal account).

## Step 1: Create a Lichess API Token

1. Log in to the dedicated bot account on [lichess.org](https://lichess.org).
2. Go to [lichess.org/account/oauth/token](https://lichess.org/account/oauth/token).
3. Create a token with the following scopes:
   - `Read incoming challenges` (`challenge:read`)
   - `Send, accept and reject challenges` (`challenge:write`)
   - `Play games using the Bot API` (`bot:play`)
4. Copy the generated token. **Important**: The token is only shown once!

## Step 2: Upgrade to a BOT Account

A Lichess account must be upgraded to a BOT account before it can be used with `lichess-bot`.
**Warning**: This is irreversible — the account can no longer play games manually.

Run the following command, replacing `YOUR_TOKEN` with the token from Step 1:

```bash
curl -d "" https://lichess.org/api/bot/account/upgrade -H "Authorization: Bearer YOUR_TOKEN"
```

> **Note**: Make sure to use `https://` — plain `http://` will fail with a 301 redirect.

## Step 3: Build the Engine

Compile the Titan Chess Engine into a fat JAR:

```bash
mvn clean package
```

This will create `target/titan-chess-engine-1.0.0-SNAPSHOT.jar`.

## Step 4: Clone and Configure lichess-bot

1. From the project root, clone the official `lichess-bot` repository:
   ```bash
   git clone https://github.com/lichess-bot-devs/lichess-bot.git lichess-bot-repo
   ```

2. Navigate into the cloned directory and set up a Python virtual environment:
   ```bash
   cd lichess-bot-repo
   python -m venv venv
   ```

3. Activate the virtual environment:
   - **Windows (PowerShell)**: `venv\Scripts\activate`
   - **Linux / macOS**: `source venv/bin/activate`

4. Install the required Python dependencies:
   ```bash
   pip install -r requirements.txt
   ```

5. Copy the default configuration and edit it:
   ```bash
   cp config.yml.default config.yml
   ```

6. Open `config.yml` and make the following changes:

   **Set your API token and URL:**
   ```yaml
   token: "YOUR_LICHESS_BOT_API_TOKEN"
   url: "https://lichess.org/"
   ```

   **Configure the engine to use Titan's JAR via Java:**
   ```yaml
   engine:
     dir: "../target"
     name: "titan-chess-engine-1.0.0-SNAPSHOT.jar"
     interpreter: "java"
     interpreter_options:
       - "-jar"
     protocol: "uci"
   ```

   **Set only the UCI options that Titan supports** (`Hash` and `Threads`).
   Remove or comment out any others (e.g., `Move Overhead`, `SyzygyPath`, `UCI_ShowWDL`):
   ```yaml
   uci_options:
     Threads: 1
     Hash: 128
   ```

## Step 5: Run the Bot

From the `lichess-bot-repo` directory (with the virtual environment activated), run:

```bash
python lichess-bot.py
```

### Running in the Background (Linux)

For 24/7 deployment, use a terminal multiplexer like `tmux` or `screen`:

```bash
tmux new -s titan-bot
cd lichess-bot-repo
source venv/bin/activate
python lichess-bot.py
# Press Ctrl+B, then D to detach
```

## Step 6: Challenge the Bot

Once the script is running, log in to Lichess with your personal account, go to the bot's profile, and click the crossed swords icon to challenge it to a game!
