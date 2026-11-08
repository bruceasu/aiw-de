"""Unambiguous JSON objects before any SDK tool can execute."""
import json
import math


def parse_tool_arguments(text):
    def pairs(values):
        result = {}
        for key, value in values:
            if key in result:
                raise ValueError("duplicate tool argument")
            result[key] = value
        return result

    def number(value):
        result = float(value)
        if not math.isfinite(result):
            raise ValueError("nonfinite tool argument")
        return result

    def constant(_value):
        raise ValueError("nonfinite tool argument")

    result = json.loads(text, object_pairs_hook=pairs, parse_float=number, parse_constant=constant)
    if not isinstance(result, dict):
        raise ValueError("tool arguments must be an object")
    return result
