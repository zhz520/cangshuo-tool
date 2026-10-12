"""Read-only HTTPS acceptance for the V44/V45 calculation catalog.

Runs on the deployment host after promotion: verifies the five local calculation tools
match the Android resources, management is guarded and statistics windows stay healthy.
Never prints credentials or mutates production data.
Usage: python scripts/verify_date_deploy.py
"""
from __future__ import print_function
import json
import os
import subprocess
import xml.etree.ElementTree as ET


def command(args):
    process = subprocess.Popen(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out, error = process.communicate()
    if process.returncode:
        raise RuntimeError('Command failed; output omitted')
    return out.decode('utf-8')


def get(path, token=None, payload=None):
    args = ['curl', '--silent', '--show-error', '--max-time', '20',
            'https://toolapi.zhzgo.cn/api/v1/' + path]
    if token:
        args.extend(['-H', 'Authorization: Bearer ' + token])
    if payload is not None:
        args.extend(['-H', 'Content-Type: application/json', '--data-binary', json.dumps(payload)])
    return json.loads(command(args))


def main():
    checks = [0]

    def check(condition, label):
        if not condition:
            raise AssertionError(label + ' (response content omitted)')
        checks[0] += 1
        print('PASS', checks[0], label)

    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    health = get('health')
    check(health['code'] == 0 and health['data']['status'] == 'UP', 'HTTPS API health')
    resources = ET.parse(os.path.join(root, 'android/app/src/main/res/values/strings.xml')).getroot()
    strings = dict((node.get('name'), node.text) for node in resources.findall('string'))
    keywords = dict((node.get('name'), [item.text for item in node]) for node in resources.findall('string-array'))
    source = json.loads(command(['docker', 'inspect', 'cangshuo-toolbox-production-server-1']))[0]
    environment = dict(pair.split('=', 1) for pair in source['Config']['Env'] if '=' in pair)
    login = get('admin/auth/login', payload={
        'username': environment['ADMIN_BOOTSTRAP_USERNAME'],
        'password': environment['ADMIN_BOOTSTRAP_PASSWORD']})
    check(login['code'] == 0, 'HTTPS administrator login')
    token = login['data']['accessToken']
    rows = dict((row['code'], row) for row in get('tools?categoryCode=CALC&pageSize=100')['data']['records'])
    for code, prefix in [('bmi', 'bmi'), ('compound_interest', 'compound'), ('mortgage', 'mortgage'),
                         ('fuel', 'fuel'), ('date_calc', 'date_calc'), ('color_converter', 'color')]:
        check(code in rows, 'HTTPS public catalog ' + code)
        row = rows[code]
        check(row['name'] == strings[prefix + '_name'] and row['description'] == strings[prefix + '_description']
              and row['icon'] == code and row['keywords'] == keywords[prefix + '_keywords'] and row['mode'] == 'LOCAL',
              'deployed Android/API agreement ' + code)
        check(get('admin/tools/' + code)['code'] == 10002, 'anonymous management denied ' + code)
        check(get('admin/tools/' + code, token)['data']['status'] == 'ENABLED',
              'HTTPS administrative detail ' + code)
    for days in (7, 30, 90):
        check(len(get('admin/statistics?days=' + str(days), token)['data']['daily']) == days,
              'HTTPS statistics ' + str(days) + ' days')
    check(get('auth/me')['code'] == 10002, 'anonymous account access denied')
    command(['curl', '--fail', '--silent', '--show-error', '--max-time', '20',
             'https://tool.zhzgo.cn/admin/login'])
    check(True, 'admin static page reachable')
    print('Production read-only acceptance:', checks[0], 'checks passed')


if __name__ == '__main__':
    main()
