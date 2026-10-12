"""Read-only HTTPS checks for the deployed V51 light-meter metadata and admin APIs."""
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
    row = next(item for item in get('tools?categoryCode=SENSOR&pageSize=100')['data']['records'] if item['code'] == 'light_meter')
    check(row['mode'] == 'LOCAL' and row['sortOrder'] == 340 and row['categoryCode'] == 'SENSOR'
          and row['icon'] == 'light_meter' and not row['requiresLogin'], 'public tool registration correct')
    check(row['name'] == strings['light_name'] and row['description'] == strings['light_description']
          and row['keywords'] == keywords['light_keywords'], 'deployed Android and API metadata agree')
    check(get('admin/tools/light_meter')['code'] == 10002, 'anonymous management denied')
    source = json.loads(command(['docker', 'inspect', 'cangshuo-toolbox-production-server-1']))[0]
    environment = dict(pair.split('=', 1) for pair in source['Config']['Env'] if '=' in pair)
    login = get('admin/auth/login', payload={'username': environment['ADMIN_BOOTSTRAP_USERNAME'],
                                          'password': environment['ADMIN_BOOTSTRAP_PASSWORD']})
    check(login['code'] == 0, 'HTTPS admin login')
    token = login['data']['accessToken']
    detail = get('admin/tools/light_meter', token)['data']
    check(detail['code'] == 'light_meter' and detail['status'] == 'ENABLED', 'admin can manage deployed tool')
    for days in (7, 30, 90):
        data = get('admin/statistics?days=' + str(days), token)['data']
        check(len(data['daily']) == days, 'statistics healthy for ' + str(days) + ' days')
    print('Light meter HTTPS acceptance:', checks[0], 'checks passed; no production usage submitted')


if __name__ == '__main__':
    main()
