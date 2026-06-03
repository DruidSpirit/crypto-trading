import os
import importlib
import inspect
from typing import Dict, Type, Optional
from src.strategies.base_strategy import BaseTradeStrategy
import logging

logger = logging.getLogger(__name__)


class StrategyFactory:
    """Strategy factory class - supports automatic strategy loading"""

    _strategies: Dict[str, Type[BaseTradeStrategy]] = {}
    _initialized = False

    @classmethod
    def register_strategy(cls, strategy_name: str, strategy_class: Type[BaseTradeStrategy]):
        """Register strategy"""
        cls._strategies[strategy_name] = strategy_class
        logger.info(f"Registered strategy: {strategy_name} -> {strategy_class.__name__}")

    @classmethod
    def create_strategy(cls, strategy_name: str) -> Optional[BaseTradeStrategy]:
        """Create strategy instance"""
        if not cls._initialized:
            cls._load_all_strategies()

        strategy_class = cls._strategies.get(strategy_name)
        if strategy_class:
            return strategy_class()
        return None

    @classmethod
    def get_available_strategies(cls) -> list:
        """Get available strategy list"""
        if not cls._initialized:
            cls._load_all_strategies()
        return list(cls._strategies.keys())

    @classmethod
    def reload_strategies(cls):
        """Reload all strategies"""
        cls._strategies.clear()
        cls._initialized = False
        cls._load_all_strategies()
        logger.info("Strategies reloaded")

    @classmethod
    def _load_all_strategies(cls):
        """Auto-scan and load all strategy implementation classes under src.strategies package"""
        if cls._initialized:
            return

        try:
            strategies_package = "src.strategies"
            strategies_dir = os.path.join(os.path.dirname(__file__))

            logger.info(f"Scanning strategy directory: {strategies_dir}")

            # Iterate through all Python files in the strategies directory
            for filename in os.listdir(strategies_dir):
                if filename.endswith('.py') and filename not in ['__init__.py', 'base_strategy.py', 'strategy_factory.py']:
                    module_name = filename[:-3]  # Remove .py extension
                    module_path = f"{strategies_package}.{module_name}"

                    try:
                        # Dynamically import module
                        module = importlib.import_module(module_path)
                        logger.info(f"Successfully imported module: {module_path}")

                        # Check all classes in the module
                        for name, obj in inspect.getmembers(module, inspect.isclass):
                            # Check if it's a subclass of BaseTradeStrategy and not BaseTradeStrategy itself
                            if (issubclass(obj, BaseTradeStrategy) and
                                obj != BaseTradeStrategy and
                                obj.__module__ == module_path):

                                # Get strategy name
                                try:
                                    # Try to get strategy name via instance
                                    temp_instance = obj()
                                    strategy_name = temp_instance.get_strategy_name()
                                    cls.register_strategy(strategy_name, obj)
                                except Exception as e:
                                    # If unable to instantiate, use class name as strategy name
                                    logger.warning(f"Unable to get strategy name, using class name: {name}, error: {e}")
                                    cls.register_strategy(name, obj)

                    except Exception as e:
                        logger.error(f"Failed to load strategy module: {module_path}, error: {e}")
                        continue

            cls._initialized = True
            logger.info(f"Strategy loading complete, loaded {len(cls._strategies)} strategies: {list(cls._strategies.keys())}")

        except Exception as e:
            logger.error(f"Auto-loading strategies failed: {e}")
            cls._initialized = True  # Mark as initialized to avoid repeated attempts
