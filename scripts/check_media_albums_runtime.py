"""Real user-link regressions plus isolated catalog/admin acceptance (Python 2/3).

Only run deliberately: three public platform requests with operator-provided
internal browser service; no accounts/captcha automation, raw links or cookies in logs.
Usage: python scripts/check_media_albums_runtime.py <image> <browser-service-url> <network>
"""
from __future__ import print_function
import sys
from check_admin_statistics_runtime import run
from check_short_video_runtime import verify as catalog_checks


def verify(check, request, sql, admin):
    base = 'web-tools/short_video_parse/'
    examples = [
        ('douyin_album_first', 'https://v.douyin.com/EZjus5lN3A4/', 'IMAGE', None),
        ('kuaishou_long_video', 'https://v.kuaishou.com/nTj2iJHt', 'VIDEO', None),
        ('douyin_album_20', 'https://v.douyin.com/UhBKL-uRlgk/', 'IMAGE', 20),
    ]
    for label, link, kind, expected in examples:
        status, body, unused = request(base + 'parse', {'text': link}, method='POST')
        data = body.get('data') or {}
        counts = dict((key, sum(asset['kind'] == key for asset in data.get('assets') or []))
                      for key in ('VIDEO', 'IMAGE', 'LIVE', 'COVER'))
        print('LIVE', label, status, 'code', body.get('code'), counts)
        check(status == 200 and counts[kind] > 0 and counts['COVER'] == 1,
              label + ' returns real media and cover')
        if expected is not None:
            check(counts[kind] == expected, label + ' keeps full album count')
        first = body['data']['id']
        second = request(base + 'parse', {'text': link}, method='POST')[1]['data']
        check(second['id'] != first and second['assets'][0]['directUrl'] == body['data']['assets'][0]['directUrl'],
              label + ' coalesced metadata keeps independent result handles')
        request(base + 'results/' + first, method='DELETE')
        check(request(base + 'assets/' + first + '/0')[1]['code'] == 61004,
              label + ' cleared result stays expired')
        request(base + 'results/' + second['id'], method='DELETE')
    catalog_checks(check, request, sql, admin)


if __name__ == '__main__':
    run(sys.argv[1], extra_checks=verify, candidate_environment={'MEDIA_BROWSER_URL': sys.argv[2]},
        candidate_networks=(sys.argv[3],), request_timeout=40)
