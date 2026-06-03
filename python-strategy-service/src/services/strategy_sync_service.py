import logging
from typing import List, Dict, Optional, Set
from sqlalchemy.orm import Session
from src.database.connection import get_db, StrategyFile, StrategyStatus
from src.strategies.strategy_factory import StrategyFactory
from datetime import datetime
import os
import inspect

logger = logging.getLogger(__name__)


class StrategySyncService:
    """Strategy sync service - responsible for syncing Python strategies to MySQL database"""

    def __init__(self):
        # Get strategies directory under project root
        current_dir = os.path.dirname(__file__)
        project_root = os.path.dirname(os.path.dirname(current_dir))
        self.strategies_dir = os.path.join(project_root, 'src', 'strategies')

    def sync_strategies_to_database(self) -> Dict[str, any]:
        """Sync Python project strategies to MySQL database"""
        try:
            logger.info("Starting strategy sync to database...")

            # Reload strategies to ensure getting the latest strategy list
            StrategyFactory.reload_strategies()

            # Get all currently available strategies
            available_strategies = StrategyFactory.get_available_strategies()
            logger.info(f"Found strategies: {available_strategies}")

            if not available_strategies:
                logger.warning("No strategy implementations found")
                return {
                    "success": False,
                    "message": "No strategy implementations found",
                    "strategies_found": 0,
                    "strategies_synced": 0
                }

            # Get strategy details
            strategy_details = []
            for strategy_name in available_strategies:
                try:
                    strategy_instance = StrategyFactory.create_strategy(strategy_name)
                    if strategy_instance:
                        # Get strategy file path and class info
                        strategy_class = strategy_instance.__class__
                        module_path = inspect.getfile(strategy_class)
                        file_name = os.path.basename(module_path)

                        strategy_info = {
                            'name': strategy_name,
                            'class_name': strategy_class.__name__,
                            'file_name': file_name,
                            'file_path': module_path,
                            'module': strategy_class.__module__,
                            'description': self._get_strategy_description(strategy_instance)
                        }
                        strategy_details.append(strategy_info)
                        logger.info(f"Strategy info collected: {strategy_info}")
                except Exception as e:
                    logger.error(f"Failed to get strategy info for {strategy_name}: {e}")
                    continue

            # Sync to database
            sync_result = self._sync_to_database(strategy_details)

            logger.info(f"Strategy sync complete: {sync_result}")
            return sync_result

        except Exception as e:
            logger.error(f"Strategy sync failed: {e}", exc_info=True)
            return {
                "success": False,
                "message": f"Strategy sync failed: {str(e)}",
                "error": str(e)
            }

    def _get_strategy_description(self, strategy_instance) -> str:
        """Get strategy description"""
        try:
            # Try to get description from docstring
            doc = strategy_instance.__class__.__doc__
            if doc:
                return doc.strip()
            return f"Auto-generated strategy: {strategy_instance.get_strategy_name()}"
        except:
            return f"Strategy: {strategy_instance.get_strategy_name()}"

    def _sync_to_database(self, strategy_details: List[Dict]) -> Dict[str, any]:
        """Sync strategy info to database"""
        db = get_db()
        try:
            synced_count = 0
            updated_count = 0
            created_count = 0
            current_strategy_names = set()

            for strategy_info in strategy_details:
                try:
                    strategy_name = strategy_info['name']
                    current_strategy_names.add(strategy_name)

                    # Find existing strategy
                    existing_strategy = db.query(StrategyFile).filter(
                        StrategyFile.display_name == strategy_name
                    ).first()

                    if existing_strategy:
                        # Update existing strategy
                        existing_strategy.filename = strategy_info['file_name']
                        existing_strategy.original_filename = strategy_info['file_name']
                        existing_strategy.file_path = strategy_info['file_path']
                        existing_strategy.description = strategy_info['description']
                        existing_strategy.last_update_time = datetime.now()
                        # If strategy status is ERROR, reset to INACTIVE
                        if existing_strategy.status == StrategyStatus.ERROR:
                            existing_strategy.status = StrategyStatus.INACTIVE

                        updated_count += 1
                        logger.info(f"Updated strategy: {strategy_name}")
                    else:
                        # Create new strategy
                        new_strategy = StrategyFile(
                            filename=strategy_info['file_name'],
                            original_filename=strategy_info['file_name'],
                            file_path=strategy_info['file_path'],
                            file_size=self._get_file_size(strategy_info['file_path']),
                            description=strategy_info['description'],
                            display_name=strategy_name,
                            status=StrategyStatus.INACTIVE,
                            upload_time=datetime.now(),
                            last_update_time=datetime.now()
                        )
                        db.add(new_strategy)
                        created_count += 1
                        logger.info(f"Created new strategy: {strategy_name}")

                    synced_count += 1

                except Exception as e:
                    logger.error(f"Failed to sync strategy {strategy_info.get('name', 'unknown')}: {e}")
                    continue

            # Mark strategies that no longer exist as ERROR status
            orphaned_count = self._mark_orphaned_strategies(db, current_strategy_names)

            # Commit changes
            db.commit()

            return {
                "success": True,
                "message": "Strategy sync successful",
                "strategies_found": len(strategy_details),
                "strategies_synced": synced_count,
                "created_count": created_count,
                "updated_count": updated_count,
                "orphaned_count": orphaned_count,
                "current_strategies": list(current_strategy_names)
            }

        except Exception as e:
            db.rollback()
            logger.error(f"Database sync failed: {e}")
            raise
        finally:
            db.close()

    def _get_file_size(self, file_path: str) -> Optional[int]:
        """Get file size"""
        try:
            if os.path.exists(file_path):
                return os.path.getsize(file_path)
        except:
            pass
        return None

    def _mark_orphaned_strategies(self, db: Session, current_strategies: Set[str]) -> int:
        """Mark strategies that no longer exist"""
        try:
            # Query all strategies in database
            all_db_strategies = db.query(StrategyFile).all()
            orphaned_count = 0

            for db_strategy in all_db_strategies:
                # If database strategy is not in current strategy list, mark as ERROR
                if db_strategy.display_name not in current_strategies:
                    if db_strategy.status != StrategyStatus.ERROR:
                        db_strategy.status = StrategyStatus.ERROR
                        db_strategy.last_update_time = datetime.now()
                        orphaned_count += 1
                        logger.info(f"Marked orphaned strategy: {db_strategy.display_name}")

            return orphaned_count

        except Exception as e:
            logger.error(f"Failed to mark orphaned strategies: {e}")
            return 0

    def get_database_strategies(self) -> List[Dict]:
        """Get strategy list from database"""
        db = get_db()
        try:
            strategies = db.query(StrategyFile).all()
            result = []
            for strategy in strategies:
                result.append({
                    "id": strategy.id,
                    "filename": strategy.filename,
                    "displayName": strategy.display_name,
                    "description": strategy.description,
                    "status": strategy.status.value,
                    "uploadTime": strategy.upload_time.isoformat() if strategy.upload_time else None,
                    "lastUpdateTime": strategy.last_update_time.isoformat() if strategy.last_update_time else None
                })
            return result
        finally:
            db.close()

    def delete_strategy_from_database(self, strategy_id: int) -> bool:
        """Delete strategy from database"""
        db = get_db()
        try:
            strategy = db.query(StrategyFile).filter(StrategyFile.id == strategy_id).first()
            if strategy:
                db.delete(strategy)
                db.commit()
                logger.info(f"Deleted strategy from database: {strategy.display_name}")
                return True
            return False
        except Exception as e:
            db.rollback()
            logger.error(f"Failed to delete strategy: {e}")
            return False
        finally:
            db.close()
