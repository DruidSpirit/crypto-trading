# Python Strategy Service

FastAPI service used by the Java trading application to execute Python trading strategies.

## Runtime

- Host: `0.0.0.0`
- Port: `8001`
- Database: SQLite
- Database file: `crypto_trading.db` in this directory

## Start

```bash
pip install -r requirements.txt
python run.py
```

The service is available at:

```text
http://localhost:8001
```

## API

- `POST /api/strategy/execute` - execute a strategy
- `GET /api/strategy/strategies` - list available strategies
- `GET /api/strategy/health` - strategy API health check
- `GET /health` - service health check

## Structure

```text
src/
  api/          API routers
  database/     SQLite connection and strategy metadata model
  models/       request and response DTOs
  services/     service layer
  strategies/   trading strategies
  utils/        shared utilities
main.py          FastAPI application
run.py           service startup script
requirements.txt Python dependencies
```

## Configuration

The service reads optional environment variables from `.env`.

```text
HOST=0.0.0.0
PORT=8001
DATABASE_URL=sqlite:///crypto_trading.db
```

If `DATABASE_URL` is not set, the service uses `crypto_trading.db` in this directory.
