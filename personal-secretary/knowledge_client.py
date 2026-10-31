"""Compatibility boundary for the optional Knowledge Base package."""

try:
    from knowledge.client import KnowledgeClient, KnowledgeError, base_url
    from knowledge.settings import KnowledgeSettings
    from knowledge.tools import perform as _perform_knowledge_action
except ModuleNotFoundError as error:
    if error.name not in {"knowledge", "knowledge.client", "knowledge.settings", "knowledge.tools"}:
        raise
    class KnowledgeError(ValueError):
        def __init__(self, message, code=None):
            super().__init__(message)
            self.code = code

    class KnowledgeClient:
        def __init__(self, *args, **kwargs):
            raise KnowledgeError("Knowledge Base package is unavailable.")

    class KnowledgeSettings:
        def __init__(self, directory, value):
            self.value = value

        def bind(self, value):
            self.value = value

        def public(self):
            return {"available": False, "enabled": False,
                    "baseUrl": self.value.get("knowledgeBaseUrl", ""), "hasCredential": False}

    def base_url(value):
        raise KnowledgeError("Knowledge Base package is unavailable.")

    KNOWLEDGE_AVAILABLE = False
else:
    KNOWLEDGE_AVAILABLE = True
