"""V51 native light-meter catalog/admin/statistics checks on an isolated candidate.

Python 2/3 compatible. The shared harness creates and cleans its own temporary
database and container. No production settings or usage records are changed.
"""
from __future__ import print_function
import os
import sys
import xml.etree.ElementTree as ET
from check_admin_statistics_runtime import run


def verify(check, request, sql, admin):
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    resources = ET.parse(os.path.join(root, 'android/app/src/main/res/values/strings.xml')).getroot()
    strings = dict((node.get('name'), node.text) for node in resources.findall('string'))
    keywords = dict((node.get('name'), [item.text for item in node]) for node in resources.findall('string-array'))
    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='51' AND success=1") == '1', 'V51 applied')
    status, body, unused = request('tools?categoryCode=SENSOR&pageSize=100')
    records = dict((row['code'], row) for row in body['data']['records'])
    check(status == 200 and 'light_meter' in records, 'SENSOR catalog includes light_meter')
    row = records['light_meter']
    check(row['name'] == strings['light_name'] and row['description'] == strings['light_description']
          and row['keywords'] == keywords['light_keywords'], 'Android and API display metadata agree')
    check(row['mode'] == 'LOCAL' and row['categoryCode'] == 'SENSOR' and row['sortOrder'] == 340
          and row['icon'] == 'light_meter' and not row['requiresLogin'], 'native mode and registration agree')
    check(request('admin/tools/light_meter')[0] == 401, 'anonymous management denied')
    status, body, unused = request('admin/tools/light_meter', token=admin)
    check(status == 200 and body['data']['status'] == 'ENABLED', 'admin can inspect light meter')
    before = request('admin/statistics?days=7', token=admin)[1]['data']['summary']
    check(request('tools/light_meter/usage', method='POST')[0] == 200, 'usage reporting accepted')
    statistics = request('admin/statistics?days=7', token=admin)[1]['data']
    row = next(item for item in statistics['ranking'] if item['code'] == 'light_meter')
    check(row['opens'] == 1 and row['allTimeOpens'] == 1, 'daily and all-time ranking include light meter')
    check(statistics['summary']['allTimeOpens'] == before['allTimeOpens'] + 1
          and statistics['summary']['windowOpens'] == before['windowOpens'] + 1, 'aggregate statistics include usage')
    check(request('admin/tools/light_meter/status', {'status': 'MAINTENANCE'}, token=admin, method='PATCH')[0] == 200,
          'admin maintenance action works')
    codes = [item['code'] for item in request('tools?categoryCode=SENSOR&pageSize=100')[1]['data']['records']]
    check('light_meter' not in codes, 'maintained tool removed from public catalog')
    check(request('tools/light_meter/usage', method='POST')[0] != 200, 'maintained tool does not record usage')
    with open(os.path.join(root, 'server/src/main/resources/db/migration/V51__seed_light_meter_tool.sql'), 'rb') as stream:
        seed = stream.read().decode('utf-8')
    sql(seed)
    check(sql("SELECT status FROM tool_definition WHERE tool_code='light_meter'") == 'MAINTENANCE', 'seed replay preserves admin state')
    check(request('admin/tools/light_meter/status', {'status': 'ENABLED'}, token=admin, method='PATCH')[0] == 200,
          'admin re-enable action works')
    check(request('admin/tools/light_meter', token=admin, method='DELETE')[0] == 200, 'admin soft delete works')
    sql(seed)
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='light_meter' AND deleted_at IS NOT NULL") == '1',
          'seed replay preserves deletion without duplicate rows')
    check(request('admin/tools/light_meter', token=admin)[0] == 404, 'deleted tool stays deleted')


if __name__ == '__main__':
    run(sys.argv[1], extra_checks=verify)
