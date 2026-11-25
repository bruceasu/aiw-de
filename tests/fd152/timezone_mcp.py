"""Read-only IANA time zone conversion for the FD-152 MCP acceptance."""
import argparse
from datetime import datetime
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from mcp.server import MCPServer

server = MCPServer("AIW Time Zone")


@server.tool()
def convert_time(instant: str, target_zone: str) -> dict[str, str]:
    """Convert an ISO 8601 timestamp with an explicit offset to an IANA time zone."""
    try:
        value = datetime.fromisoformat(instant)
        if value.utcoffset() is None:
            raise ValueError
        converted = value.astimezone(ZoneInfo(target_zone))
    except (ValueError, ZoneInfoNotFoundError, OverflowError):
        raise ValueError("Provide an ISO 8601 timestamp with an offset and a valid IANA time zone.") from None
    return {"converted": converted.isoformat(), "zone": target_zone}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    server.run(transport="streamable-http", host="127.0.0.1", port=args.port, json_response=True, stateless_http=True)
