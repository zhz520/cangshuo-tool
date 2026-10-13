"""Read-only HTTPS checks for the deployed V53 stopwatch metadata and admin APIs."""
from __future__ import print_function
import json
import os
import xml.etree.ElementTree as ET
from verify_date_deploy import command, get


def main():
    checks = [0]
    def check(condition, label):
        if not condition:
            raise AssertionError(label + ' (response content omitted)')
        checks[0] += 1
        print('PASS', checks[0], label)
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    resources = ET.parse(os.path.join(root, 'android/app/src/main/res/values/strings.xml')).getroot()
    strings = dict((node.get('name'), node.text) for node in resources.findall('string'))
    keywords = dict((node.get('name'), [item.text for item in node]) for node in resources.findall('string-array'))
    check(get('health')['data']['status'] == 'UP', 'HTTPS API healthy')
    row = next(item for item in get('tools?categoryCode=LIFE&pageSize=100')['data']['records'] if item['code'] == 'stopwatch')
    check(row['mode'] == 'LOCAL' and row['sortOrder'] == 620 and row['categoryCode'] == 'LIFE'
          and row['icon'] == 'stopwatch' and not row['requiresLogin'], 'public tool registration correct')
    check(row['name'] == strings['stopwatch_name'] and row['description'] == strings['stopwatch_description']
          and row['keywords'] == keywords['stopwatch_keywords'], 'deployed Android and API metadata agree')
    check(get('admin/tools/stopwatch')['code'] == 10002, 'anonymous management denied')
    source = json.loads(command(['docker', 'inspect', 'cangshuo-toolbox-production-server-1']))[0]
    environment = dict(pair.split('=', 1) for pair in source['Config']['Env'] if '=' in pair)
    login = get('admin/auth/login', payload={'username': environment['ADMIN_BOOTSTRAP_USERNAME'],
                                          'password': environment['ADMIN_BOOTSTRAP_PASSWORD']})
    check(login['code'] == 0, 'HTTPS admin login')
    token = login['data']['accessToken']
    detail = get('admin/tools/stopwatch', token)['data']
    check(detail['code'] == 'stopwatch' and detail['status'] == 'ENABLED', 'admin can manage deployed tool')
    for days in (7, 30, 90):
        data = get('admin/statistics?days=' + str(days), token)['data']
        check(len(data['daily']) == days, 'statistics healthy for ' + str(days) + ' days')
    print('Stopwatch HTTPS acceptance:', checks[0], 'checks passed; no production usage submitted')


if __name__ == '__main__':
    main()
