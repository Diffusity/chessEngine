# Titan Chess Engine

Titan is a UCI-compatible chess engine written in Java.

## Features
* **Board Representation:** Bitboards and Magic Bitboards
* **Move Generation:** Pseudo-legal move generator with Make/Unmake
* **Search:** Negamax, Alpha-Beta Pruning, Quiescence Search, Iterative Deepening
* **Evaluation:** Material, Piece-Square Tables (PST), Pawn Structure, Mobility, King Safety
* **Heuristics:** Transposition Table (TT), Null Move Pruning (NMP), Late Move Reductions (LMR), Killer Moves, History Heuristics

## Building
Titan requires Java 17+ and Maven.

```bash
mvn clean package
```
This produces an executable JAR in the `target` directory.

Alternatively, you can use the provided Makefile:
```bash
make
```

## Running
Run the engine via the JAR file:
```bash
java -jar target/titan-chess-engine-1.0.0-SNAPSHOT.jar
```

Or via Makefile:
```bash
make run
```

### Benchmarking
Run the built-in benchmark (perft and search):
```bash
java -jar target/titan-chess-engine-1.0.0-SNAPSHOT.jar bench 8
```

Or via Makefile:
```bash
make bench
```

## UCI Commands Supported
- `uci`
- `isready`
- `ucinewgame`
- `position startpos [moves ...]`
- `position fen ... [moves ...]`
- `go [depth <x>] [wtime <x>] [btime <x>] [winc <x>] [binc <x>]`
- `setoption name Hash value <x>`
- `stop`
- `quit`

## License
MIT License
