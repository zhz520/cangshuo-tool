"""HTTPS acceptance without sending any valid third-party login or step submission."""
from __future__ import print_function
import json
import subprocess
from verify_date_deploy import main as verify_local_catalog


def fetch(path, payload=None):
    args = ['curl', '--silent', '--show-error', '--max-time', '20', '--proto', '=https',
            '-D', '-', 'https://tool.zhzgo.cn' + path]
    if payload is not None:
        args.extend(['-H', 'Content-Type: application/json', '--data-binary', json.dumps(payload)])
    process = subprocess.Popen(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    data, unused = process.communicate()
    if process.returncode:
        raise RuntimeError('HTTPS request failed; sensitive output omitted')
    text = data.decode('utf-8')
    header, body = text.split('\r\n\r\n', 1)
    # curl may include an interim HTTP/1.1 100 response before the real response.
    if header.splitlines()[0].split()[1] == '100':
        header, body = body.split('\r\n\r\n', 1)
    status = int(header.splitlines()[0].split()[1])
    return status, header.lower(), body


def main():
    verify_local_catalog()
    checks = [0]
    def check(condition, label):
        if not condition:
            raise AssertionError(label + ' (response omitted)')
        checks[0] += 1
        print('PASS Zepp', checks[0], label)
    for name in ('', 'style.css?v=1', 'engine.js?v=1', 'app.js?v=1'):
        status, header, body = fetch('/tools/zepp_steps/' + name)
        check(status == 200 and len(body) > 100, 'page/resource reachable ' + (name or 'index'))
    check('zepp_steps' in fetch('/')[2], 'public homepage entry')
    check('Zepp Life' in fetch('/privacy/')[2], 'privacy disclosure published')
    status, header, body = fetch('/api/v1/tools/zepp_steps')
    row = json.loads(body)['data']
    check(status == 200 and row['mode'] == 'WEB' and row['categoryCode'] == 'LIFE' and row['sortOrder'] == 510,
          'same-origin WEB catalog')
    status, header, body = fetch('/api/v1/web-tools/zepp_steps/capabilities')
    value = json.loads(body)
    check(status == 200 and value['code'] == 0 and value['data']['available'], 'configured anonymous capability')
    check('cache-control: no-store' in header and value['data']['maxSteps'] == 50000
          and value['data']['timeoutSeconds'] == 30, 'no-store and limits')
    for steps, consent in [(0, True), (1.5, True), ('10000', True), (10000, False), (10000, 'true')]:
        status, header, body = fetch('/api/v1/web-tools/zepp_steps/submit',
            {'account': 'test@example.invalid', 'password': 'never-contact-zepp', 'steps': steps, 'consent': consent})
        check(status == 400 and json.loads(body)['code'] == 10001 and 'cache-control: no-store' in header,
              'invalid input rejected before third-party login')
        check('never-contact-zepp' not in body and 'test@example.invalid' not in body,
              'error response excludes credentials')
    print('Zepp HTTPS acceptance:', checks[0], 'checks passed; real account sync unverified')


if __name__ == '__main__':
    main()
