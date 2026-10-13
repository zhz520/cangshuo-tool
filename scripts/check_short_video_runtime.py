"""V54 web media catalog, permissions, admin controls and usage acceptance.

Python 2/3 compatible. Runs inside the shared isolated DB/container harness.
Optional --live makes a few read-only requests for public test media; it reports
actual outcomes separately from deterministic acceptance checks.
"""
from __future__ import print_function
import os
import sys
from check_admin_statistics_runtime import run


def verify(check, request, sql, admin):
    code = 'short_video_parse'
    base = 'web-tools/' + code
    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='54' AND success=1") == '1', 'V54 applied')
    status, body, unused = request('tools?categoryCode=NETWORK&pageSize=100')
    row = next((item for item in body['data']['records'] if item['code'] == code), None)
    check(status == 200 and row is not None and row['mode'] == 'WEB' and row['icon'] == 'toolbox'
          and not row['requiresLogin'] and row['sortOrder'] == 630, 'WEB catalog metadata and native dynamic registration agree')
    check(sql("SELECT JSON_UNQUOTE(JSON_EXTRACT(config_json,'$.webPath')) FROM tool_definition WHERE tool_code='short_video_parse'") == '/tools/short_video_parse/', 'official web path stored')
    status, body, cache = request(base + '/capabilities')
    caps = body['data']
    check(status == 200 and caps['available'] and len(caps['platforms']) == 8 and caps['maxTextLength'] == 4096
          and caps['maxImages'] == 50 and caps['maxFileBytes'] == 209715200 and caps['maxZipBytes'] == 33554432, 'public capabilities match client budgets')
    check(cache == 'no-store', 'capabilities are never cached')
    check(request(base + '/parse', {'text':'not a URL'}, method='POST')[1]['code'] == 10001, 'invalid input rejected')
    check(request(base + '/parse', {'text':'https://v.douyin.com.evil.example/a'}, method='POST')[1]['code'] == 61001, 'spoofed platform rejected')
    check(request(base + '/parse', {'text':'https://u:p@v.douyin.com/a'}, method='POST')[1]['code'] == 10001, 'URL credentials rejected')
    check(request(base + '/parse', {'text':'a' * 4097}, method='POST')[0] == 400, 'oversized share text rejected')
    check(request(base + '/parse', {'text':'a' * 17000}, method='POST')[0] == 413, 'body budget enforced before parsing')
    unknown = 'a' * 32
    status, body, cache = request(base + '/assets/' + unknown + '/0')
    check(status == 410 and body['code'] == 61004 and cache == 'no-store', 'missing handles return no-store JSON errors')
    check(request(base + '/assets/not-a-token/0')[0] == 400, 'arbitrary download URL is not an endpoint')
    check(request(base + '/results/' + unknown, method='DELETE')[0] == 200, 'idempotent result clear works')
    check(request('admin/tools/' + code)[0] == 401, 'anonymous management denied')
    check(request('tools/' + code + '/usage', method='POST')[0] == 200, 'web usage recording accepted')
    stats = request('admin/statistics?days=7', token=admin)[1]['data']
    check(any(item['code'] == code and item['opens'] >= 1 for item in stats['ranking']), 'web tool appears in daily ranking')
    if '--live' in sys.argv:
        examples = [
            ('douyin', 'https://www.douyin.com/video/6961737553342991651'),
            ('xiaohongshu', 'https://www.xiaohongshu.com/explore/6411cf99000000001300b6d9'),
            ('bilibili', 'https://www.bilibili.com/video/BV1xx411c7mD'),
            ('tiktok', 'https://www.tiktok.com/@leenabhushan/video/6748451240264420610'),
        ]
        for platform, url in examples:
            status, body, unused = request(base + '/parse', {'text':url}, method='POST')
            if status == 200:
                result = body['data']
                counts = dict((kind, sum(asset['kind'] == kind for asset in result['assets'])) for kind in ('VIDEO','IMAGE','LIVE','COVER'))
                print('LIVE', platform, status, counts)
                request(base + '/results/' + result['id'], method='DELETE')
            else:
                print('LIVE', platform, status, 'code', body.get('code'))
    check(request('admin/tools/' + code + '/status', {'status':'MAINTENANCE'}, token=admin, method='PATCH')[0] == 200, 'admin pause works')
    check(not request(base + '/capabilities')[1]['data']['available'], 'paused service reports unavailable')
    check(request(base + '/parse', {'text':'https://v.douyin.com/a'}, method='POST')[0] == 503, 'paused parser does not run')
    check(request(base + '/assets/' + unknown + '/0')[0] == 503, 'paused downloads are stopped too')
    path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'server/src/main/resources/db/migration/V54__seed_short_video_parse_web_tool.sql')
    with open(path, 'rb') as stream:
        seed = stream.read().decode('utf-8')
    sql(seed)
    check(sql("SELECT status FROM tool_definition WHERE tool_code='short_video_parse'") == 'MAINTENANCE', 'seed replay preserves operator pause')
    check(request('admin/tools/' + code + '/status', {'status':'ENABLED'}, token=admin, method='PATCH')[0] == 200, 'admin re-enable works')
    check(request('admin/tools/' + code, token=admin, method='DELETE')[0] == 200, 'admin soft delete works')
    sql(seed)
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='short_video_parse' AND deleted_at IS NOT NULL") == '1', 'seed replay preserves deletion without duplicate rows')
    check(not request(base + '/capabilities')[1]['data']['available'], 'deleted tool is not callable')


if __name__ == '__main__':
    run(sys.argv[1], extra_checks=verify)
