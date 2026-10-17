# -*- coding: utf-8 -*-
"""Read-only HTTPS acceptance for deployed V76 drawing_board metadata."""
from __future__ import print_function
import json
import sys

from check_drawing_board_runtime import (
    ADMIN_FILTER, ADMIN_PATH, CATALOG, CODE, CONTRACT, check_catalog_page,
    check_static, check_statistics, check_tool_metadata, offline_acceptance,
)
from verify_date_deploy import command, get


ADMIN_CATEGORIES = 'admin/tools/categories'
READ_PATHS = frozenset((CATALOG, ADMIN_PATH, ADMIN_FILTER, 'health', ADMIN_CATEGORIES,
                        'tools/popular?limit=50')
                       + tuple('admin/statistics?days=' + str(days) for days in (7, 30, 90)))


class ReadOnlyApi(object):
    def __init__(self):
        self.login_attempted = False

    def read(self, path, token=None):
        if path not in READ_PATHS:
            raise ValueError('production GET path is not allowlisted (path omitted)')
        return get(path, token=token)

    def login(self, username, password):
        if self.login_attempted:
            raise RuntimeError('only one administrator login attempt is permitted')
        self.login_attempted = True
        return get('admin/auth/login', payload={'username': username, 'password': password})


def administrator_credentials():
    source = json.loads(command(['docker', 'inspect', 'cangshuo-toolbox-production-server-1']))[0]
    environment = dict(pair.split('=', 1) for pair in source['Config']['Env'] if '=' in pair)
    username = environment.get('ADMIN_BOOTSTRAP_USERNAME')
    password = environment.get('ADMIN_BOOTSTRAP_PASSWORD')
    if not username or not password:
        raise RuntimeError('administrator credentials unavailable; values omitted')
    return username, password


def verify(check, api, credentials):
    def read(path, token=None, code=0):
        body = api.read(path, token)
        check(body.get('code') == code, 'HTTPS GET ' + path)
        return body.get('data')

    check_static(check)
    check(read('health')['status'] == 'UP', 'HTTPS API healthy')
    catalog = check_catalog_page(check, read(CATALOG), 'HTTPS LIFE catalog')
    check(CODE in catalog, 'HTTPS LIFE catalog includes drawing_board')
    check_tool_metadata(check, catalog[CODE], 'deployed public drawing board',
                        expected=CONTRACT, public=True)
    for path in (ADMIN_PATH, ADMIN_FILTER, ADMIN_CATEGORIES) + tuple(
            'admin/statistics?days=' + str(days) for days in (7, 30, 90)):
        read(path, code=10002)
    username, password = credentials()
    login = api.login(username, password)
    check(login.get('code') == 0, 'HTTPS administrator login')
    token = login['data']['accessToken']
    detail = read(ADMIN_PATH, token)
    check_tool_metadata(check, detail, 'deployed admin drawing board', expected=CONTRACT)
    check(isinstance(json.loads(detail['configJson']), dict),
          'deployed drawing board configuration is a JSON object')
    rows = check_catalog_page(check, read(ADMIN_FILTER, token), 'HTTPS admin drawing board filter')
    check(CODE in rows, 'HTTPS admin list finds the drawing board')
    check_tool_metadata(check, rows[CODE], 'deployed admin list drawing board', expected=CONTRACT)
    check(rows[CODE] == detail, 'HTTPS admin list and detail agree')
    categories = read(ADMIN_CATEGORIES, token)
    check(len([row for row in categories if row['code'] == 'LIFE' and row['enabled']]) == 1,
          'HTTPS admin editor offers enabled LIFE category')
    for days in (7, 30, 90):
        data = read('admin/statistics?days=' + str(days), token)
        check_statistics(check, data, days)
        for group, group_code in (('categories', 'LIFE'), ('modes', 'LOCAL')):
            groups = [row for row in data[group] if row['code'] == group_code]
            check(len(groups) == 1 and groups[0]['count'] >= 1 and groups[0]['opens'] >= 0,
                  'HTTPS ' + str(days) + '-day ' + group + ' includes LIFE/LOCAL usage')
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) <= 1 and all(
            row['name'] == CONTRACT['name'] and row['status'] == 'ENABLED'
            and 0 <= row['opens'] <= row['allTimeOpens'] <= data['summary']['allTimeOpens']
            for row in ranks
        ), 'HTTPS ' + str(days) + '-day drawing board ranking valid when present')
    popular = read('tools/popular?limit=50')
    check(len(popular) <= 50 and len(set(row['code'] for row in popular)) == len(popular)
          and all(row['useCount'] >= 1 for row in popular), 'HTTPS popularity is bounded and unique')
    values = [row for row in popular if row['code'] == CODE]
    check(len(values) <= 1 and all(
        all(row[key] == CONTRACT[key] for key in ('name', 'description', 'icon'))
        and row['useCount'] >= 1 for row in values
    ), 'HTTPS drawing board popularity valid when present')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
        return
    if len(sys.argv) != 1:
        raise SystemExit('Usage: python -B scripts/verify_drawing_board_deploy.py [--static]')
    checks = [0]

    def check(condition, label):
        if not condition:
            raise AssertionError(label + ' (response content omitted)')
        checks[0] += 1
        print('PASS', checks[0], label)

    verify(check, ReadOnlyApi(), administrator_credentials)
    print('Drawing board HTTPS acceptance:', checks[0],
          'checks passed; GETs and one admin login only; no synthetic opens or catalog changes')


if __name__ == '__main__':
    main()
