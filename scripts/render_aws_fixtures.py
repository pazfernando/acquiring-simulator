#!/usr/bin/env python3
"""Render DynamoDB BatchWrite JSON, offline only. Never calls AWS or handles JWTs.

Usage: python3 scripts/render_aws_fixtures.py TABLE_NAME JWT_SUBJECT > fixtures.json
Review before any separately authorized write to an isolated AWS account.
"""
import json
from pathlib import Path
import re
import sys


def render(table, subject):
    profile = {}
    source = Path(__file__).resolve().parents[1] / 'src/main/resources/application.properties'
    for line in source.read_text().splitlines():
        match = re.match(r'simulator.profile\.([\w-]+)\[(\d+)\]\.([\w-]+)=(.*)', line)
        if not match:
            continue
        group, index, field, value = match.groups()
        group = re.sub(r'-([a-z])', lambda m: m[1].upper(), group)
        entries = profile.setdefault(group, [])
        while len(entries) <= int(index):
            entries.append({})
        entries[int(index)][field] = value.split(',') if field == 'routes' else value
    caller = {'acquirerRef': 'ACQ-SIM-001', 'merchantRef': 'MERCH-SIM-001',
              'callerId': subject, 'permittedRoutes': ['DOMESTIC', 'INTERNATIONAL']}
    def put(pk, payload):
        return {'PutRequest': {'Item': {'pk': {'S': pk}, 'sk': {'S': 'PROFILE'},
                                      'payload': {'S': json.dumps(payload)}}}}
    return {table: [put('CONFIG#ACTIVE', profile), put('CALLER#' + subject, caller)]}


if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    print(json.dumps(render(sys.argv[1], sys.argv[2]), indent=2))
