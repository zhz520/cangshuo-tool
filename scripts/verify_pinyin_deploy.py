"""Read-only HTTPS acceptance for the deployed V56 pinyin catalog and admin APIs.

Python 2/3 stdlib; run on the deployment host with the matching Android resources.
The sole POST authenticates the administrator, as in verify_regex_tester_deploy.
All acceptance requests are GETs: no production usage, catalog/configuration
changes, seed replay or conversion execution.
Usage: python scripts/verify_pinyin_deploy.py
"""
from __future__ import print_function
import json
from check_pinyin_runtime import (
    ADMIN_PATH, CATALOG, CODE, CONTRACT, check_android_metadata, check_bundled_table,
    check_statistics, check_tool_metadata,
)
from verify_date_deploy import command, get


def main():
    checks = [0]

    def check(condition, label):
        if not condition:
            raise AssertionError(label + ' (response content omitted)')
        checks[0] += 1
        print('PASS', checks[0], label)

    def read(path, token=None, code=0):
        body = get(path, token=token)
        check(body.get('code') == code, 'HTTPS GET ' + path)
        return body.get('data')

    check_bundled_table(check)
    check_android_metadata(check)
    check(read('health')['status'] == 'UP', 'HTTPS API healthy')
    catalog = dict((row['code'], row) for row in read(CATALOG)['records'])
    check(CODE in catalog, 'HTTPS TEXT catalog includes pinyin')
    check_tool_metadata(check, catalog[CODE], 'deployed public pinyin', public=True)
    for path in (ADMIN_PATH, 'admin/tools?categoryCode=TEXT', 'admin/statistics?days=7'):
        read(path, code=10002)

    source = json.loads(command(['docker', 'inspect', 'cangshuo-toolbox-production-server-1']))[0]
    environment = dict(pair.split('=', 1) for pair in source['Config']['Env'] if '=' in pair)
    login = get('admin/auth/login', payload={
        'username': environment['ADMIN_BOOTSTRAP_USERNAME'],
        'password': environment['ADMIN_BOOTSTRAP_PASSWORD']})
    check(login.get('code') == 0, 'HTTPS administrator login')
    token = login['data']['accessToken']
    check_tool_metadata(check, read(ADMIN_PATH, token), 'deployed admin pinyin')
    page = read('admin/tools?categoryCode=TEXT&keyword=pinyin&status=ENABLED&pageSize=100', token)
    admin_rows = dict((row['code'], row) for row in page['records'])
    check(CODE in admin_rows, 'HTTPS admin filters find pinyin')
    check_tool_metadata(check, admin_rows[CODE], 'deployed admin list pinyin')
    categories = read('admin/tools/categories', token)
    check(any(row['code'] == 'TEXT' and row['enabled'] for row in categories),
          'HTTPS admin editor offers TEXT category')
    for days in (7, 30, 90):
        data = read('admin/statistics?days=' + str(days), token)
        check_statistics(check, data, days)
        for group, group_code in (('categories', 'TEXT'), ('modes', 'LOCAL')):
            rows = [row for row in data[group] if row['code'] == group_code]
            check(len(rows) == 1 and rows[0]['count'] >= 1 and rows[0]['opens'] >= 0,
                  'HTTPS ' + str(days) + '-day ' + group + ' includes pinyin registration group')
        pinyin_rows = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(pinyin_rows) <= 1 and all(row['name'] == CONTRACT['name'] and row['status'] == 'ENABLED'
              and 0 <= row['opens'] <= row['allTimeOpens'] <= data['summary']['allTimeOpens'] for row in pinyin_rows),
              'HTTPS ' + str(days) + '-day pinyin ranking is valid when present in top 20')
    popular = [row for row in read('tools/popular?limit=50') if row['code'] == CODE]
    check(len(popular) <= 1 and all(row['name'] == CONTRACT['name'] and row['description'] == CONTRACT['description']
          and row['icon'] == CODE and row['useCount'] >= 1 for row in popular),
          'HTTPS pinyin popularity is valid when present in top 50')
    print('Pinyin HTTPS acceptance:', checks[0], 'checks passed; no production usage or configuration changes')


if __name__ == '__main__':
    main()
