# Contributing

Thanks for helping improve Crypto Trading. This project is a Spring Boot
application with a Python strategy service, so most changes should keep both
services in mind.

## Development setup

Requirements:

- Java 17 or later
- Maven 3.9 or the bundled Maven wrapper
- Python 3.9 or later

Run the Java service:

```bash
mvn spring-boot:run
```

Run the Python strategy service:

```bash
cd python-strategy-service
pip install -r requirements.txt
python run.py
```

The Java application listens on `http://localhost:5567`. The Python strategy
service listens on `http://localhost:8001`.

## Checks before opening a pull request

Run the Java tests:

```bash
mvn test
```

Run a Python syntax check:

```bash
python -m compileall python-strategy-service
```

## Strategy contributions

Java strategies should extend `AbstractTradeStrategy` and return a
`TradeStrategyDTO`.

Python strategies should extend `BaseTradeStrategy`, implement `execute()`,
and provide a stable `get_strategy_name()`.

For trading strategy changes, include:

- The intended market condition.
- The input timeframes.
- Signal entry and exit assumptions.
- Any backtest caveats.

## Pull request guidelines

- Keep changes focused and avoid unrelated refactors.
- Do not commit local databases, generated logs, cache files, or release zips.
- Include screenshots or concise notes for user interface changes.
- Treat trading output as research signals, not financial advice.
