# -*- coding: utf-8 -*-
"""Read-only HTTPS acceptance for V92 Lanzou direct-link metadata."""
from __future__ import print_function
import json
import sys
try:
    from urllib.request import Request, urlopen
except ImportError:
    from urllib2 import Request, urlopen
from check_lanzou_runtime import (ADMIN_FILTER, ADMIN_PATH, CAPABILITIES, CATALOG, CODE, CONTRACT,
    PARSE, check_catalog_page, check_static, check_statistics, check_tool_metadata)
from verify_date_deploy import command, get

READ_PATHS = frozenset((CATALOG, ADMIN_PATH, ADMIN_FILTER, CAPABILITIES, 'health',
                        'admin/tools/categories', 'tools/popular?limit=50')
                       + tuple('admin/statistics?days=' + str(day) for day in (7, 30, 90)))


class ReadOnlyApi(object):
    def __init__(self):
        self.login_attempted = False
        self.posts = 0
    def read(self, path, token=None):
        if path not in READ_PATHS: raise ValueError('production GET path is not allowlisted')
        return get(path, token=token)
    def login(self, username, password):
        if self.login_attempted: raise RuntimeError('only one administrator login is permitted')
        self.login_attempted = True
        body = get('admin/auth/login', payload={'username': username, 'password': password})
        return body
    def invalid_parse(self):
        if self.posts: raise RuntimeError('only one invalid parse probe is permitted')
        self.posts += 1
        return get(PARSE, payload={'text': 'https://example.com/i1'})


def administrator_credentials():
    source = json.loads(command(['docker', 'inspect', 'cangshuo-toolbox-production-server-1']))[0]
    environment = dict(pair.split('=', 1) for pair in source['Config']['Env'] if '=' in pair)
    username = environment.get('ADMIN_BOOTSTRAP_USERNAME')
    password = environment.get('ADMIN_BOOTSTRAP_PASSWORD')
    if not username or not password: raise RuntimeError('administrator credentials unavailable; values omitted')
    return username, password


def verify(check, api, credentials):
    def read(path, token=None):
        body = api.read(path, token)
        check(body.get('code') == 0, 'HTTPS GET ' + path)
        return body.get('data')
    check_static(check)
    check(read('health')['status'] == 'UP', 'HTTPS API healthy')
    catalog = check_catalog_page(check, read(CATALOG), 'HTTPS NETWORK catalog')
    check(CODE in catalog, 'HTTPS NETWORK catalog includes lanzou_direct')
    check_tool_metadata(check, catalog[CODE], 'deployed public Lanzou', expected=CONTRACT, public=True)
    capabilities = read(CAPABILITIES)
    check(capabilities == {'available': True, 'maxTextLength': 4096, 'maxPasswordLength': 64,
          'cacheSeconds': 180, 'requestsPerMinute': 8, 'maxConcurrent': 2,
          'verificationMode': 'OFFICIAL_PAGE_FALLBACK', 'permanentLinksAvailable': True},
          'HTTPS capabilities are bounded')
    body = api.invalid_parse()
    check(body.get('code') == 62001, 'Invalid non-Lanzou link is rejected without parsing')
    body = get('web-tools/lanzou_direct/permanent-links', payload={'text': 'https://example.com/i1'})
    check(body.get('code') == 62001, 'Invalid permanent-link source is rejected')
    body = get('web-tools/lanzou_direct/download/not-a-valid-token')
    check(body.get('code') == 62001, 'Invalid permanent token is rejected')
    for path in (ADMIN_PATH, ADMIN_FILTER, 'admin/tools/categories') + tuple(
            'admin/statistics?days=' + str(day) for day in (7, 30, 90)):
        body = api.read(path)
        check(body.get('code') == 10002, 'Anonymous management denied: ' + path)
    username, password = credentials()
    login = api.login(username, password)
    check(login.get('code') == 0, 'HTTPS administrator login')
    token = login['data']['accessToken']
    detail = read(ADMIN_PATH, token)
    check_tool_metadata(check, detail, 'deployed admin Lanzou', expected=CONTRACT)
    check(json.loads(detail['configJson']).get('webPath') == '/tools/lanzou_direct/',
          'Deployed web path is exact')
    rows = check_catalog_page(check, read(ADMIN_FILTER, token), 'HTTPS admin Lanzou filter')
    check(CODE in rows and rows[CODE] == detail, 'Admin list and detail agree')
    for days in (7, 30, 90):
        data = read('admin/statistics?days=' + str(days), token)
        check_statistics(check, data, days)
        check(all(row['code'] != CODE or row['status'] == 'ENABLED' for row in data['ranking']),
              str(days) + '-day ranking metadata is valid when present')
    page = 'https://tool.zhzgo.cn/tools/lanzou_direct/'
    for suffix in ('', 'engine.js', 'app.js', 'style.css', 'NOTICES.txt'):
        response = urlopen(Request(page + suffix, headers={'User-Agent':'Lanzou-acceptance/1'}), timeout=20)
        try: payload = response.read(2_000_000)
        finally: response.close()
        check(response.status == 200 and len(payload) > 0, 'HTTPS page asset 200: ' + (suffix or 'index'))
    response = urlopen(Request(page, headers={'User-Agent':'Lanzou-acceptance/1'}), timeout=20)
    try: html = response.read(2_000_000).decode('utf-8')
    finally: response.close()
    check("frame-src 'none'" in html and "form-action 'none'" in html,
          'Production page retains restrictive CSP')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        from check_lanzou_runtime import offline_acceptance
        offline_acceptance(); return
    if len(sys.argv) != 1: raise SystemExit('Usage: python -B scripts/verify_lanzou_deploy.py [--static]')
    checks = [0]
    def check(condition, label):
        if not condition: raise AssertionError(label + ' (response content omitted)')
        checks[0] += 1; print('PASS', checks[0], label)
    verify(check, ReadOnlyApi(), administrator_credentials)
    print('Lanzou HTTPS acceptance:', checks[0],
          'checks passed; GETs, one invalid POST and one admin login; no synthetic usage or catalog changes')


if __name__ == '__main__': main()
