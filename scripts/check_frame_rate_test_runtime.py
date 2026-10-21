# -*- coding: utf-8 -*-
"""V91 frame rate contract and strictly isolated backend acceptance."""
import os
import re
import sys
import xml.etree.ElementTree as ET
sys.dont_write_bytecode = True
from check_admin_statistics_runtime import run
from check_color_picker_runtime import check_catalog_page, check_statistics, check_tool_metadata, read_source

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODE = 'frame_rate_test'
CATALOG = 'tools?categoryCode=OTHER&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=OTHER&keyword=frame_rate_test&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V91__seed_frame_rate_test_tool.sql')
CONTRACT = {'code': CODE, 'name': '可视帧率测试',
    'description': '观察动态画布的帧回调频率、帧间隔和长帧，支持多档测试、趋势、历史与 CSV 分享。',
    'categoryCode': 'OTHER', 'mode': 'LOCAL', 'icon': CODE, 'sortOrder': 643,
    'requiresLogin': False, 'featured': False, 'status': 'ENABLED', 'version': 1,
    'keywords': ['帧率', '流畅度', '帧间隔', '长帧', 'fps', 'frame rate', 'refresh rate']}

def check_static(check):
    resource_root = os.path.join(ROOT, 'android/app/src/main/res')
    translations = []
    for locale in ('values', 'values-en'):
        nodes = ET.parse(os.path.join(resource_root, locale, 'fps.xml')).getroot()
        strings = {n.get('name'): ''.join(n.itertext()) for n in nodes.findall('string')}
        check(len(strings) == len(nodes.findall('string')) and all(strings.values()), locale + ' unique nonempty strings')
        translations.append(strings)
        if locale == 'values':
            check(strings['fps_name'] == CONTRACT['name'] and strings['fps_description'] == CONTRACT['description'], 'Android/server metadata agreement')
            check([n.text for n in nodes.find('string-array')] == CONTRACT['keywords'], 'Android/server keyword agreement')
    check(translations[0].keys() == translations[1].keys(), 'Bilingual key parity')
    check(all(sorted(re.findall(r'%[0-9]+\$[ds]', v)) == sorted(re.findall(r'%[0-9]+\$[ds]', translations[1][k])) for k,v in translations[0].items()), 'Bilingual format argument parity')
    seed = read_source(SEED_PATH)
    for value in (CODE, CONTRACT['name'], CONTRACT['description'], "code = 'OTHER'", 'ON DUPLICATE KEY UPDATE tool_code = tool_code'):
        check(value in seed, 'Migration metadata: ' + value[:40])
    check('CREATE TABLE' not in seed.upper(), 'No server storage for timing samples')
    definition=read_source(os.path.join(ROOT,'android/app/src/main/java/com/cangshuo/toolbox/feature/fps/FpsToolDefinition.kt'))
    check('sortOrder = 643' in definition and 'ToolCategory.OTHER' in definition and 'ToolMode.LOCAL' in definition, 'Registry metadata agreement')
    check('requiredPermissions: List<String> = emptyList()' in definition, 'No permission requested by the FPS tool')
    container=read_source(os.path.join(ROOT,'android/app/src/main/java/com/cangshuo/toolbox/ToolboxAppContainer.kt'))
    check(container.count('FpsToolDefinition(resources,fpsFactory)') == 1, 'Registered exactly once')
    docs=read_source(os.path.join(ROOT,'docs/FRAME_RATE_TEST.md'))
    check('dd3eb554b0c256218b49741455e391f8e4462ca6' in docs and 'Apache-2.0' in docs, 'Fixed source and license documented')
    check('不提供跨应用悬浮' in translations[0]['fps_scope'] and 'not' in translations[1]['fps_scope'], 'Scope and limitations are disclosed in both languages')

def offline_acceptance():
    checks=[]
    def check(condition,label):
        if not condition: raise AssertionError(label)
        checks.append(label)
        print('PASS',len(checks),label)
    check_static(check)
    print('Frame rate offline contract:',len(checks),'passed')

def verify(check, request, sql, admin):
    check_static(check)
    defaults = dict(CONTRACT, configJson='{}')

    def call(path, payload=None, token=None, method=None, status=200, code=0):
        actual, body, cache = request(path, payload, token=token, method=method)
        check(actual == status and body.get('code') == code, (method or 'GET') + ' ' + path)
        if path.startswith('admin/statistics') and status == 200:
            check(cache == 'no-store', 'statistics responses forbid caching')
        return body.get('data')

    def records(path=CATALOG, token=None):
        return check_catalog_page(check, call(path, token=token), path)

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='91' AND success=1") == '1', 'V91 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V91 creates one frame rate row')
    check(CODE in records(), 'public OTHER catalog includes frame_rate_test')
    check_tool_metadata(check, records()[CODE], 'public frame rate test', expected=defaults, public=True)
    detail = call(ADMIN_PATH, token=admin)
    listing = records(ADMIN_FILTER, admin)[CODE]
    check_tool_metadata(check, detail, 'admin frame rate detail', defaults)
    check_tool_metadata(check, listing, 'admin frame rate list', defaults)
    check(detail == listing, 'admin frame rate detail and list agree')
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'OTHER' and row['enabled']]) == 1,
          'admin editor offers enabled OTHER category')
    edited = dict(defaults, name=u'可视帧率测试验收', description=u'后台编辑的可视帧率测试说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'frame rate-admin'],
                  sortOrder=644, version=2, featured=True, requiresLogin=True,
                  configJson='{"acceptance":"frame_rate_test"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'),
                        'edited frame rate metadata', edited)
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    queries = ["SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
               "SELECT * FROM flyway_schema_history WHERE version='91'",
               "SELECT * FROM tool_usage_daily WHERE tool_code='%s' ORDER BY usage_date" % CODE,
               "SELECT * FROM tool_usage_stat WHERE tool_code='%s'" % CODE]
    snapshot = [sql(query) for query in queries]
    source = read_source(SEED_PATH)
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot, 'V91 replay preserves operator edits and usage')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'seed replay preserves edited frame rate', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'),
                        'restored frame rate metadata', defaults)
    daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE)
    total = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE)
    check(daily == total == '2', 'Frame rate increments daily and all-time usage')
    popular = dict((row['code'], row) for row in call('tools/popular?limit=50')).get(CODE)
    check(popular is not None and popular['useCount'] == 2
          and all(popular[key] == CONTRACT[key] for key in ('name', 'description', 'icon')),
          'Frame rate participates in popularity')
    for days in (7, 30, 90):
        data = call('admin/statistics?days=' + str(days), token=admin)
        check_statistics(check, data, days)
        for group, group_code in (('categories', 'OTHER'), ('modes', 'LOCAL')):
            rows = [row for row in data[group] if row['code'] == group_code]
            check(len(rows) == 1 and rows[0]['count'] >= 1 and rows[0]['opens'] >= 2,
                  str(days) + '-day ' + group + ' includes frame rate usage')
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2,
              'Frame rate participates in ' + str(days) + '-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify, strict_isolation=True)
    else:
        raise SystemExit('Usage: python -B scripts/check_frame_rate_test_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
