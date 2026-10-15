"""Read-only HTTPS acceptance for the deployed V57 Chinese converter metadata.

Python 2/3 stdlib; run on the deployment host with the matching Android resources.
Reuse the V56 metadata/statistics assertions and the existing HTTPS transport.
The sole POST authenticates the administrator; all acceptance requests are GETs.
No catalog/configuration writes, synthetic opens, seed replay or conversion calls.
An unused tool may be absent from bounded popularity/top-20 rankings; validate it
when present. Actual conversion and Android UI remain separate acceptance items.
Usage: python -B scripts/verify_chinese_convert_deploy.py
"""
from __future__ import print_function
import json
from check_chinese_convert_runtime import (
    ADMIN_FILTER, ADMIN_PATH, CATALOG, CODE, CONTRACT, check_static,
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

    check_static(check)
    check(read('health')['status'] == 'UP', 'HTTPS API healthy')
    page = read(CATALOG)
    catalog = dict((row['code'], row) for row in page['records'])
    check(len(catalog) == len(page['records']) == page['total'], 'HTTPS TEXT catalog is complete and unique')
    check(CODE in catalog, 'HTTPS TEXT catalog includes the Chinese converter')
    check_tool_metadata(check, catalog[CODE], 'deployed public Chinese converter', public=True)
    for path in (ADMIN_PATH, ADMIN_FILTER, 'admin/tools/categories', 'admin/statistics?days=7'):
        read(path, code=10002)

    source = json.loads(command(['docker', 'inspect', 'cangshuo-toolbox-production-server-1']))[0]
    environment = dict(pair.split('=', 1) for pair in source['Config']['Env'] if '=' in pair)
    login = get('admin/auth/login', payload={
        'username': environment['ADMIN_BOOTSTRAP_USERNAME'],
        'password': environment['ADMIN_BOOTSTRAP_PASSWORD']})
    check(login.get('code') == 0, 'HTTPS administrator login')
    token = login['data']['accessToken']
    check_tool_metadata(check, read(ADMIN_PATH, token), 'deployed admin Chinese converter')
    page = read(ADMIN_FILTER, token)
    rows = dict((row['code'], row) for row in page['records'])
    check(len(rows) == len(page['records']) == page['total'] and CODE in rows,
          'HTTPS admin filter finds the unique Chinese converter')
    check_tool_metadata(check, rows[CODE], 'deployed admin list Chinese converter')
    categories = read('admin/tools/categories', token)
    check(any(row['code'] == 'TEXT' and row['enabled'] for row in categories), 'HTTPS admin editor offers TEXT')
    for days in (7, 30, 90):
        data = read('admin/statistics?days=' + str(days), token)
        check_statistics(check, data, days)
        for group, group_code in (('categories', 'TEXT'), ('modes', 'LOCAL')):
            groups = [row for row in data[group] if row['code'] == group_code]
            check(len(groups) == 1 and groups[0]['count'] >= 1 and groups[0]['opens'] >= 0,
                  'HTTPS ' + str(days) + '-day ' + group + ' includes the converter group')
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) <= 1 and all(row['name'] == CONTRACT['name'] and row['status'] == 'ENABLED'
              and 0 <= row['opens'] <= row['allTimeOpens'] <= data['summary']['allTimeOpens'] for row in ranks),
              'HTTPS ' + str(days) + '-day converter ranking is valid when present in top 20')
    popular = [row for row in read('tools/popular?limit=50') if row['code'] == CODE]
    check(len(popular) <= 1 and all(row['name'] == CONTRACT['name'] and row['description'] == CONTRACT['description']
          and row['icon'] == CODE and row['useCount'] >= 1 for row in popular),
          'HTTPS converter popularity is valid when present in top 50')
    print('Chinese converter HTTPS acceptance:', checks[0], 'checks passed; no synthetic opens or catalog changes')


if __name__ == '__main__':
    main()
