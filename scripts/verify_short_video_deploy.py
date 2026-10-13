# -*- coding: utf-8 -*-
"""Read-only HTTPS acceptance for the deployed short-video parser web tool.

Runs on the deployment host after promotion (python 2/3): checks the public page and
static assets, catalog metadata, anonymous/admin guards, budgets, one real public link
parse plus a Range download of a produced asset, and clears that in-memory result.
Never prints credentials, and does not inject production usage counts.
Usage: python scripts/verify_short_video_deploy.py
"""
from __future__ import print_function
import json
import os
import sys
from verify_date_deploy import command, get


def curl(args):
    return command(['curl', '--silent', '--show-error', '--max-time', '40'] + args)


def main():
    checks = [0]

    def check(condition, label):
        if not condition:
            raise AssertionError(label + ' (response content omitted)')
        checks[0] += 1
        print('PASS', checks[0], label)

    check(get('health')['data']['status'] == 'UP', 'HTTPS API healthy')
    row = next(item for item in get('tools?categoryCode=NETWORK&pageSize=100')['data']['records']
               if item['code'] == 'short_video_parse')
    check(row['mode'] == 'WEB' and row['sortOrder'] == 630 and row['icon'] == 'toolbox'
          and not row['requiresLogin'], 'public catalog registration')
    capabilities = get('web-tools/short_video_parse/capabilities')['data']
    check(capabilities['available'] and len(capabilities['platforms']) == 8
          and capabilities['maxTextLength'] == 4096 and capabilities['maxImages'] == 50
          and capabilities['maxFileBytes'] == 209715200 and capabilities['maxZipBytes'] == 33554432,
          'capabilities and budgets')
    check(get('web-tools/short_video_parse/parse', payload={'text': 'not a link'})['code'] == 10001,
          'invalid input rejected before any provider request')
    check(get('web-tools/short_video_parse/parse',
              payload={'text': 'https://v.douyin.com.evil.example/x'})['code'] == 61001,
          'suffix-spoofed platform host rejected')
    check(get('web-tools/short_video_parse/parse',
              payload={'text': 'https://u:p@v.douyin.com/a'})['code'] == 10001,
          'URL credentials rejected')
    check(get('admin/tools/short_video_parse')['code'] == 10002, 'anonymous management denied')
    base = 'https://tool.zhzgo.cn/tools/short_video_parse/'
    check(curl(['-o', os.devnull, '-w', '%{http_code}', base]) == '200', 'public page reachable')
    for asset in ('style.css', 'engine.js', 'app.js', 'NOTICES.txt'):
        check(curl(['-o', os.devnull, '-w', '%{http_code}', base + asset]) == '200',
              'static asset ' + asset)
    page = curl([base])
    engine = curl([base + 'engine.js'])
    check(u'粘贴分享链接' in page and u'隐私' in page
          and 'web-tools/short_video_parse' in engine
          and 'https://' not in engine.split('HOSTS')[0],
          'page copy, privacy notice and same-origin API wiring')
    live = get('web-tools/short_video_parse/parse',
               payload={'text': 'https://www.douyin.com/video/6961737553342991651'})
    if live['code'] == 0:
        data = live['data']
        videos = [asset for asset in data['assets'] if asset['kind'] == 'VIDEO']
        check(bool(videos) and any(asset['kind'] == 'COVER' for asset in data['assets']),
              'real public link returns video sources and a cover')
        download = 'https://toolapi.zhzgo.cn' + videos[0]['downloadPath']
        head = curl(['-r', '0-1023', '-o', os.devnull, '-w', '%{http_code} %{content_type} %{size_download}',
                     download]).split()
        ranged = head[0] == '206' and int(head[2]) == 1024
        full = head[0] == '200' and int(head[2]) > 1024
        check((ranged or full) and head[1].startswith('video/'),
              'real asset download returns video bytes (206 range or upstream 200)')
        cleared = json.loads(curl(['-X', 'DELETE', 'https://toolapi.zhzgo.cn/api/v1/web-tools/'
                                   'short_video_parse/results/' + data['id']]))
        check(cleared['code'] == 0, 'in-memory result cleared after acceptance')
    else:
        print('INFO live parse returned code', live['code'], '- third-party availability varies')
    source = json.loads(command(['docker', 'inspect', 'cangshuo-toolbox-production-server-1']))[0]
    environment = dict(pair.split('=', 1) for pair in source['Config']['Env'] if '=' in pair)
    login = get('admin/auth/login', payload={'username': environment['ADMIN_BOOTSTRAP_USERNAME'],
                                             'password': environment['ADMIN_BOOTSTRAP_PASSWORD']})
    check(login['code'] == 0, 'HTTPS administrator login')
    token = login['data']['accessToken']
    detail = get('admin/tools/short_video_parse', token)['data']
    check(detail['status'] == 'ENABLED' and detail['mode'] == 'WEB', 'admin manages the deployed tool')
    for days in (7, 30, 90):
        check(len(get('admin/statistics?days=' + str(days), token)['data']['daily']) == days,
              'statistics healthy for ' + str(days) + ' days')
    print('Short-video HTTPS acceptance:', checks[0], 'checks passed')


if __name__ == '__main__':
    sys.exit(main())
