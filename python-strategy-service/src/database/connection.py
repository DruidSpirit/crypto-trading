import enum
import logging
import os
from typing import Optional

from dotenv import load_dotenv
from sqlalchemy import Column, DateTime, Enum, Integer, String, Text, create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.sql import func

load_dotenv()

logger = logging.getLogger(__name__)

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_DB_PATH = os.path.join(PROJECT_ROOT, "crypto_trading.db")
DEFAULT_DATABASE_URL = f"sqlite:///{DEFAULT_DB_PATH.replace(os.sep, '/')}"

DATABASE_URL = os.getenv("DATABASE_URL", DEFAULT_DATABASE_URL)

engine_options = {
    "echo": False,
    "pool_pre_ping": True,
}

if DATABASE_URL.startswith("sqlite"):
    engine_options["connect_args"] = {"check_same_thread": False}
else:
    engine_options.update(
        {
            "pool_size": 20,
            "max_overflow": 0,
            "pool_recycle": 3600,
        }
    )

engine = create_engine(DATABASE_URL, **engine_options)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


class StrategyStatus(enum.Enum):
    ACTIVE = "ACTIVE"
    INACTIVE = "INACTIVE"
    UPDATING = "UPDATING"
    ERROR = "ERROR"


class StrategyFile(Base):
    """Strategy file metadata stored by the Python strategy service."""

    __tablename__ = "strategy_files"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    filename = Column(String(255), nullable=False, unique=True)
    original_filename = Column(String(255), name="original_filename")
    file_path = Column(String(500), name="file_path")
    file_size = Column(Integer, name="file_size")
    description = Column(Text)
    display_name = Column(String(200), name="display_name")
    status = Column(Enum(StrategyStatus), nullable=False, default=StrategyStatus.INACTIVE)
    upload_time = Column(DateTime, name="upload_time", nullable=False, server_default=func.now())
    last_update_time = Column(DateTime, name="last_update_time", onupdate=func.now())


def get_db() -> Session:
    """Create a database session. The caller is responsible for closing it."""

    return SessionLocal()


def create_tables():
    """Create database tables when they do not exist."""

    try:
        Base.metadata.create_all(bind=engine)
        logger.info("Database tables are ready")
    except Exception as e:
        logger.error("Failed to create database tables: %s", e)
        raise


def test_connection() -> bool:
    """Check whether the configured database is reachable."""

    try:
        from sqlalchemy import text

        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
        logger.info("Database connection test passed: %s", DATABASE_URL)
        return True
    except Exception as e:
        logger.error("Database connection test failed: %s", e)
        return False
