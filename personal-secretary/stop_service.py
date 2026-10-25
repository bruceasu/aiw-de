"""Stop an existing local secretary; never start one or fetch private records."""
from __future__ import annotations

import argparse
import socket
import urllib.parse

from desktop_client import PersonalClient, PersonalError, personal_origin


def main():
    parser = argparse.ArgumentParser(description="Stop the local secretary HTTP server")
    parser.add_argument("--service", default="http://127.0.0.1:8766")
    args = parser.parse_args()
    try:
        origin = personal_origin(args.service)
        target = urllib.parse.urlsplit(origin)
        try:
            with socket.create_connection((target.hostname, target.port), timeout=2):
                pass
        except ConnectionRefusedError:
            print("本地服务未监听，无需停止。")
            return 0
        PersonalClient(origin).shutdown_service()
        print("HttpServer 监听已关闭；后台清理可能仍需最多约 25 秒。记录和设置保留。")
        return 0
    except (PersonalError, OSError) as error:
        print(str(error))
        print("未确认停止。旧版本或无响应的服务可使用 stop.ps1 -Force；强制停止会中断当前操作。")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
