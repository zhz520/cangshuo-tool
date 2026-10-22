# -*- coding: utf-8 -*-
"""V92 Lanzou direct-link contract and strictly isolated backend acceptance."""
import json
import os
import sys
import xml.etree.ElementTree as ET
sys.dont_write_bytecode = True
from check_admin_statistics_runtime import run
from check_color_picker_runtime import check_catalog_page, check_statistics, check_tool_metadata, read_source

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODE = 'lanzou_direct'
CATALOG = 'tools?categoryCode=NETWORK&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=NETWORK&keyword=lanzou_direct&status=ENABLED&pageSize=100'
CAPABILITIES = 'web-tools/lanzou_direct/capabilities'
PARSE = 'web-tools/lanzou_direct/parse'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V92__seed_lanzou_direct_web_tool.sql')
DESCRIPTION = '解析蓝奏云公开分享链接，显示文件名、大小、临时直链和官方回退页；支持分享密码与失败说明。'
KEYWORDS = ['蓝奏云', '蓝奏', '直链', '下载', '分享密码', 'lanzou', 'direct link']
CONTRACT = {'code': CODE, 'name': '蓝奏直链解析', 'description': DESCRIPTION,
    'categoryCode': 'NETWORK', 'mode': 'WEB', 'icon': 'link', 'sortOrder': 631,
    'requiresLogin': False, 'featured': False, 'status': 'ENABLED', 'version': 1,
    'keywords': KEYWORDS}


def check_static(check):
    resource_root = os.path.join(ROOT, 'android/app/src/main/res')
    bilingual = []
    for locale in ('values', 'values-en'):
        root = ET.parse(os.path.join(resource_root, locale, 'strings.xml')).getroot()
        strings = {n.get('name'): ''.join(n.itertext()) for n in root.findall('string')}
        check('lanzou_direct_name' in strings and 'lanzou_direct_description' in strings,
              locale + ' has Lanzou title and description')
        bilingual.append(strings)
    check(bilingual[0]['lanzou_direct_name'] == CONTRACT['name'], 'Android/server name agreement')
    check(bilingual[0]['lanzou_direct_description'] == DESCRIPTION, 'Android/server description agreement')
    check(all(bilingual[1][key] for key in ('lanzou_direct_name', 'lanzou_direct_description')),
          'English display metadata is nonempty')

    definition = read_source(os.path.join(ROOT,
        'android/app/src/main/java/com/cangshuo/toolbox/feature/webtools/LanzouDirectToolDefinition.kt'))
    for value in ('"lanzou_direct"', 'ToolCategory.NETWORK', 'ToolMode.WEB', 'sortOrder = 631',
                  'requiredPermissions = emptyList<String>()'):
        check(value in definition, 'Android definition: ' + value)
    container = read_source(os.path.join(ROOT, 'android/app/src/main/java/com/cangshuo/toolbox/ToolboxAppContainer.kt'))
    check(container.count('LanzouDirectToolDefinition(resources)') == 1, 'Registered exactly once')
    policy = read_source(os.path.join(ROOT,
        'android/app/src/main/java/com/cangshuo/toolbox/feature/webtools/domain/WebToolNavigationPolicy.kt'))
    check('fun allowsLanzouExternal' in policy and '127.0.0.1' in policy, 'External browser handoff is constrained')

    seed = read_source(SEED_PATH)
    check(CODE in seed and DESCRIPTION in seed and "code = 'NETWORK'" in seed and
          '/tools/lanzou_direct/' in seed and 'ON DUPLICATE KEY UPDATE tool_code = tool_code' in seed,
          'V92 seed metadata and replay semantics')
    check('CREATE TABLE' not in seed.upper() and 'password_hash' not in seed.lower()
          and 'INSERT INTO tool_definition' in seed, 'V92 stores metadata only')
    java_dir = os.path.join(ROOT, 'server/src/main/java/com/cangshuo/toolbox/lanzou')
    for name in ('LanzouController.java', 'LanzouService.java', 'LanzouParser.java', 'LanzouHttp.java',
                 'LanzouChallenge.java', 'LanzouHosts.java', 'LanzouInput.java'):
        check(os.path.isfile(os.path.join(java_dir, name)), 'Server class present: ' + name)
    server_source = ''.join(read_source(os.path.join(java_dir, name)) for name in os.listdir(java_dir)
                            if name.endswith('.java'))
    for forbidden in ('X-FORWARDED-FOR', 'CLIENT-IP', 'TrustAll', 'setEndpointIdentificationAlgorithm'):
        check(forbidden not in server_source, 'No unsafe transport escape: ' + forbidden)
    check('REQUESTS_PER_MINUTE = 8' in server_source and 'new Semaphore(2)' in server_source,
          'Client request and upstream concurrency budgets are present')
    check('CACHE_SECONDS = 180' in server_source and 'MessageDigest.getInstance("SHA-256")' in server_source,
          'Short cache and digest-only password key are present')
    check(os.path.isfile(os.path.join(ROOT, 'server/src/main/resources/licenses/lanzouapi-MIT.txt')),
          'Server distribution includes the reviewed MIT notice')

    web = os.path.join(ROOT, 'deploy/site/tools/lanzou_direct')
    for name in ('index.html', 'style.css', 'engine.js', 'app.js', 'NOTICES.txt'):
        check(os.path.getsize(os.path.join(web, name)) > 0, 'Web asset present: ' + name)
    page = read_source(os.path.join(web, 'index.html'))
    app = read_source(os.path.join(web, 'app.js'))
    for value in ("default-src 'self'", "frame-src 'none'", "form-action 'none'"):
        check(value in page, 'CSP: ' + value)
    check('web-tools/lanzou_direct' in app and 'http://' not in page and 'https://cdn' not in page,
          'Page uses only the dedicated same-origin API and local assets')
    check('localStorage' not in app and 'sessionStorage' not in app, 'Page does not persist links or passwords')
    check("$('password').value = ''" in app and 'controller.abort()' in app,
          'Password clearing and request cancellation are wired')

    docs = read_source(os.path.join(ROOT, 'docs/LANZOU_DIRECT.md'))
    for value in ('eb2d2eb25233d7e44645162e4f817abc0ec87d94',
                  '437b7d0389e9900e38770991da2d5506138f64fe',
                  'a009311702f16bf588e2264eb0f4abf13d8968ae', 'MIT', '防封和风控策略'):
        check(value in docs, 'Reference and boundary documentation: ' + value[:28])
    api = read_source(os.path.join(ROOT, 'docs/API.md'))
    check('web-tools/lanzou_direct/parse' in api and '62009' in api, 'API documentation updated')
    database = read_source(os.path.join(ROOT, 'docs/DATABASE.md'))
    check('### V92：蓝奏直链解析目录' in database, 'Database documentation updated')


def offline_acceptance():
    checks = []
    def check(condition, label):
        if not condition: raise AssertionError(label)
        checks.append(label); print('PASS', len(checks), label)
    check_static(check)
    print('Lanzou offline contract:', len(checks), 'passed')


def verify(check, request, sql, admin):
    check_static(check)
    defaults = dict(CONTRACT, configJson='{"webPath":"/tools/lanzou_direct/"}')
    def call(path, payload=None, token=None, method=None, status=200, code=0):
        actual, body, cache = request(path, payload, token=token, method=method)
        check(actual == status and body.get('code') == code, (method or 'GET') + ' ' + path)
        if status == 200 and path.startswith('web-tools/lanzou_direct/'):
            check(cache == 'no-store', 'Lanzou API response forbids caching: ' + path)
        return body.get('data')
    def records(path=CATALOG, token=None):
        return check_catalog_page(check, call(path, token=token), path)

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='92' AND success=1") == '1', 'V92 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1', 'V92 creates one row')
    check(CODE in records(), 'Public NETWORK catalog includes lanzou_direct')
    check_tool_metadata(check, records()[CODE], 'public Lanzou metadata', expected=defaults, public=True)
    detail = call(ADMIN_PATH, token=admin)
    listing = records(ADMIN_FILTER, admin)[CODE]
    check_tool_metadata(check, detail, 'admin Lanzou detail', defaults)
    check_tool_metadata(check, listing, 'admin Lanzou list', defaults)
    check(detail == listing, 'Admin list and detail agree')
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'NETWORK' and row['enabled']]) == 1,
          'Admin editor offers enabled NETWORK category')

    capabilities = call(CAPABILITIES)
    check(capabilities == {'available': True, 'maxTextLength': 4096, 'maxPasswordLength': 64,
          'cacheSeconds': 180, 'requestsPerMinute': 8, 'maxConcurrent': 2,
          'verificationMode': 'OFFICIAL_PAGE_FALLBACK'}, 'Capabilities are explicit and bounded')
    call(PARSE, {'text': 'not a link'}, method='POST', status=400, code=10001)
    call(PARSE, {'text': 'https://example.com/i1'}, method='POST', status=400, code=62001)
    call(PARSE, {'text': 'https://www.lanzouf.com/i1', 'password': 'bad\r\nHost: evil'},
         method='POST', status=400, code=10001)

    edited = dict(defaults, name=u'蓝奏直链验收', description=u'后台编辑的直链工具说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'lanzou-admin'],
                  sortOrder=632, version=2, featured=True, requiresLogin=True,
                  configJson='{"webPath":"/tools/lanzou_direct/","acceptance":true}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'), 'edited Lanzou metadata', edited)
    for unused in range(2): call('tools/' + CODE + '/usage', method='POST')
    queries = ["SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
               "SELECT * FROM flyway_schema_history WHERE version='92'",
               "SELECT * FROM tool_usage_daily WHERE tool_code='%s' ORDER BY usage_date" % CODE,
               "SELECT * FROM tool_usage_stat WHERE tool_code='%s'" % CODE]
    snapshot = [sql(query) for query in queries]
    source = read_source(SEED_PATH); sql(source); sql(source)
    check([sql(query) for query in queries] == snapshot, 'V92 replay preserves operator edits and usage')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'seed replay preserves edited metadata', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'), 'restored metadata', defaults)
    daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE)
    total = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE)
    check(daily == total == '2', 'Usage increments once per explicit call')
    popular = dict((row['code'], row) for row in call('tools/popular?limit=50')).get(CODE)
    check(popular is not None and popular['useCount'] == 2, 'Lanzou participates in popularity')
    for days in (7, 30, 90):
        data = call('admin/statistics?days=' + str(days), token=admin)
        check_statistics(check, data, days)
        for group, group_code in (('categories', 'NETWORK'), ('modes', 'WEB')):
            rows = [row for row in data[group] if row['code'] == group_code]
            check(len(rows) == 1 and rows[0]['count'] >= 1 and rows[0]['opens'] >= 2,
                  str(days) + '-day ' + group + ' includes Lanzou usage')
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2,
              'Lanzou participates in ' + str(days) + '-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static': offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify, strict_isolation=True)
    else: raise SystemExit('Usage: python -B scripts/check_lanzou_runtime.py --static | <candidate-image>')


if __name__ == '__main__': main()
