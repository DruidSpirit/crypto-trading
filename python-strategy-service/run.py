#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
Strategy service startup script
"""

import sys
import os
import uvicorn
from dotenv import load_dotenv

# Add project root directory to Python path
project_root = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, project_root)

# Load environment variables
load_dotenv()

if __name__ == "__main__":
    host = os.getenv("HOST", "0.0.0.0")
    port = int(os.getenv("PORT", 8001))

    print(f"Starting strategy service on {host}:{port}")

    uvicorn.run(
        "main:app",
        host=host,
        port=port,
        reload=True,
        log_level="info"
    )
