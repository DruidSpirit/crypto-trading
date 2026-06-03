# Changelog

All notable changes to Crypto Trading will be documented in this file.

The project uses semantic versioning for future releases.

## Unreleased

- Added Docker Compose support for running the Java backend and Python strategy service together.
- Added persistent Docker volumes for Java H2 data, Python SQLite data, logs, and strategy files.
- Added README screenshots, project roadmap, and trading risk disclaimer.
- Added GitHub Actions CI, Dependabot, issue templates, pull request template, and open-source governance documents.

## 0.1.0

- Initial public open-source release.
- Spring Boot web console for dashboard, signal list, strategy management, backtesting, and settings.
- Exchange K-line data crawling support for Binance, OKX, Gate.io, and Bybit.
- Java strategy interface based on TA4J.
- Python FastAPI strategy service with built-in Elder strategy examples.
