"""Optional Knowledge Base API integration package."""

from .client import KnowledgeClient, KnowledgeError, base_url

__all__ = ["KnowledgeClient", "KnowledgeError", "base_url"]
