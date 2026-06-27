# Deploying Titan Chess Engine to Lichess

This guide explains how to deploy the Titan Chess Engine to Lichess so users can play against it.
We use the official [lichess-bot](https://github.com/lichess-org/lichess-bot) Python client to act as a bridge between the Lichess API and Titan's UCI interface.

## Prerequisites

1. **Java 17+**: Required to run the Titan engine JAR.
2. **Python 3.7+**: Required to run `lichess-bot`.
3. **Lichess Account**: A dedicated Lichess account for the bot.

## Step 1: Create a Bot Account on Lichess

1. Create a new account on [lichess.org](https://lichess.org) (do **not** use your personal account).
2. Once created and logged in, upgrade the account to a BOT account by generating an API token:
   - Go to [lichess.org/account/oauth/token](https://lichess.org/account/oauth/token).
   - Create a token with the following scopes:
     - `Read incoming challenges`
     - `Create, accept, decline challenges`
     - `Play games with the bot API`
   - Copy the generated token. **Important**: The token is only shown once!

## Step 2: Build the Engine

Compile the Titan Chess Engine into a fat JAR:

```bash
mvn clean package
```

This will create `target/titan-chess-engine-1.0.0-SNAPSHOT.jar`.

## Step 3: Configure lichess-bot

A pre-configured `lichess-bot` directory is provided in the repository.

1. Navigate to the `lichess-bot` directory.
2. Install the required Python dependencies:
   ```bash
   pip install -r requirements.txt
   ```
3. Open `config.yml` and replace `YOUR_LICHESS_BOT_API_TOKEN` with the API token you generated in Step 1:
   ```yaml
   token: "YOUR_LICHESS_BOT_API_TOKEN"
   ```
4. Adjust the `challenge` settings in `config.yml` if you want to restrict the time controls or variants the bot accepts.

## Step 4: Run the Bot

Start the bot using the provided script (which builds the engine and runs the python bot), or manually:

### Option A: Manual

From the `lichess-bot` directory, run:

```bash
python run.py
```

### Option B: Background Service (Linux)

For 24/7 deployment, use `systemd` or a terminal multiplexer like `tmux` or `screen`.

Example with `tmux`:
```bash
tmux new -s titan-bot
cd lichess-bot
python run.py
# Press Ctrl+B, then D to detach
```

## Step 5: Challenge the Bot

Once the script is running, log in to Lichess with your personal account, go to the bot's profile, and click the crossed swords icon to challenge it to a game!
