# -*- coding: utf-8 -*-
"""Read-only HTTPS acceptance for deployed V73 handheld_banner metadata.

The verifier permits only allowlisted GET paths and exactly one administrator
login. It never opens synthetic tools, edits production metadata or runs V73.
Usage: python -B scripts/verify_handheld_banner_deploy.py [--static]
"""
from __future__ import print_function
import json
import sys

sys.dont_write_bytecode = True
from check_handheld_banner_runtime import (
    ADMIN_FILTER, ADMIN_PATH, CATALOG, CODE, CONTRACT, check_catalog_page,
    check_static, check_statistics, check_tool_metadata, offline_acceptance,
)
from verify_date_deploy import command, get


ADMIN_CATEGORIES = 'admin/tools/categories'
READ_PATHS = frozenset((CATALOG, ADMIN_PATH, ADMIN_FILTER, 'health', ADMIN_CATEGORIES,
                        'tools/popular?limit=50')
                       + tuple('admin/statistics?days=' + str(days) for days in (7, 30, 90)))


class ReadOnlyApi(object):
    def __init__(self, transport=None):
        self.transport = get if transport is None else transport
        self.login_attempted = False

    def read(self, path, token=None):
        if path not in READ_PATHS:
            raise ValueError('production GET path is not allowlisted (path omitted)')
        return self.transport(path, token=token)

    def login(self, username, password):
        if self.login_attempted:
            raise RuntimeError('only one administrator login attempt is permitted')
        self.login_attempted = True
        return self.transport('admin/auth/login', payload={'username': username, 'password': password})


def administrator_credentials():
    source = json.loads(command(['docker', 'inspect', 'cangshuo-toolbox-production-server-1']))[0]
    environment = dict(pair.split('=', 1) for pair in source['Config']['Env'] if '=' in pair)
    username = environment.get('ADMIN_BOOTSTRAP_USERNAME')
    password = environment.get('ADMIN_BOOTSTRAP_PASSWORD')
    if not username or not password:
        raise RuntimeError('administrator bootstrap credentials unavailable; values omitted')
    return username, password


def verify(check, api, credentials):
    def read(path, token=None, code=0):
        body = api.read(path, token)
        check(body.get('code') == code, 'HTTPS GET ' + path)
        return body.get('data')

    check_static(check)
    check(read('health')['status'] == 'UP', 'HTTPS API healthy')
    catalog = check_catalog_page(check, read(CATALOG), 'HTTPS LIFE catalog')
    check(CODE in catalog, 'HTTPS LIFE catalog includes handheld_banner')
    check_tool_metadata(check, catalog[CODE], 'deployed public handheld banner',
                        expected=CONTRACT, public=True)
    guarded = (ADMIN_PATH, ADMIN_FILTER, ADMIN_CATEGORIES) + tuple(
        'admin/statistics?days=' + str(days) for days in (7, 30, 90))
    for path in guarded:
        read(path, code=10002)

    username, password = credentials()
    login = api.login(username, password)
    check(login.get('code') == 0, 'HTTPS administrator login')
    token = login['data']['accessToken']
    check(bool(token), 'HTTPS administrator access token available')
    detail = read(ADMIN_PATH, token)
    check_tool_metadata(check, detail, 'deployed admin handheld banner', expected=CONTRACT)
    check(isinstance(json.loads(detail['configJson']), dict),
          'deployed handheld banner configuration is a JSON object')
    rows = check_catalog_page(check, read(ADMIN_FILTER, token), 'HTTPS admin handheld banner filter')
    check(CODE in rows, 'HTTPS admin list finds the unique handheld banner')
    check_tool_metadata(check, rows[CODE], 'deployed admin list handheld banner', expected=CONTRACT)
    check(rows[CODE] == detail, 'HTTPS admin list and detail agree')
    categories = read(ADMIN_CATEGORIES, token)
    check(len([row for row in categories if row['code'] == 'LIFE' and row['enabled']]) == 1,
          'HTTPS admin editor offers the enabled LIFE category')
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
        ), 'HTTPS ' + str(days) + '-day handheld banner ranking valid when present')
        print('INFO', str(days) + '-day handheld banner ranking: '
              + ('present' if ranks else 'outside top 20'))
    popular = read('tools/popular?limit=50')
    check(len(popular) <= 50 and len(set(row['code'] for row in popular)) == len(popular)
          and all(row['useCount'] >= 1 for row in popular), 'HTTPS popularity is bounded, unique and used')
    values = [row for row in popular if row['code'] == CODE]
    check(len(values) <= 1 and all(
        all(row[key] == CONTRACT[key] for key in ('name', 'description', 'icon'))
        and row['useCount'] >= 1 for row in values
    ), 'HTTPS handheld banner popularity valid when present')
    print('INFO', 'handheld banner popularity: '
          + ('present' if values else 'unused or outside top 50'))


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
        return
    if len(sys.argv) != 1:
        raise SystemExit('Usage: python -B scripts/verify_handheld_banner_deploy.py [--static]')
    checks = [0]

    def check(condition, label):
        if not condition:
            raise AssertionError(label + ' (response content omitted)')
        checks[0] += 1
        print('PASS', checks[0], label)

    verify(check, ReadOnlyApi(), administrator_credentials)
    print('Handheld banner HTTPS acceptance:', checks[0],
          'checks passed; GETs and one admin login only; no synthetic opens or catalog changes')


if __name__ == '__main__':
    main()
