# -*- coding: utf-8 -*-
"""V90 wooden fish contract and strictly isolated backend acceptance."""
import os
import re
import sys
import xml.etree.ElementTree as ET
sys.dont_write_bytecode = True
from check_admin_statistics_runtime import run
from check_color_picker_runtime import check_catalog_page, check_statistics, check_tool_metadata, read_source

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODE = 'wooden_fish'
CATALOG = 'tools?categoryCode=OTHER&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=OTHER&keyword=wooden_fish&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V90__seed_wooden_fish_tool.sql')
CONTRACT = {'code': CODE, 'name': '电子木鱼',
    'description': '轻触木鱼并聆听本机合成音效，支持每日目标、里程碑、自动节奏和计数重置。',
    'categoryCode': 'OTHER', 'mode': 'LOCAL', 'icon': CODE, 'sortOrder': 642,
    'requiresLogin': False, 'featured': False, 'status': 'ENABLED', 'version': 1,
    'keywords': ['木鱼', '电子木鱼', '静心', '敲击', '计数', 'wooden fish', 'meditation']}

def check_static(check):
    resource_root = os.path.join(ROOT, 'android/app/src/main/res')
    translations = []
    for locale in ('values', 'values-en'):
        nodes = ET.parse(os.path.join(resource_root, locale, 'fish.xml')).getroot()
        strings = {n.get('name'): ''.join(n.itertext()) for n in nodes.findall('string')}
        check(len(strings) == len(nodes.findall('string')) and all(strings.values()), locale + ' unique nonempty strings')
        translations.append(strings)
        milestones = [name for name in ('fish_achievement_first', 'fish_achievement_100', 'fish_achievement_500',
            'fish_achievement_1000', 'fish_achievement_3000', 'fish_achievement_10000', 'fish_achievement_30000') if name in strings]
        check(len(milestones) == 7, locale + ' has all seven milestones')
        if locale == 'values':
            check(strings['fish_name'] == CONTRACT['name'] and strings['fish_description'] == CONTRACT['description'], 'Android/server metadata agreement')
            check([n.text for n in nodes.find('string-array')] == CONTRACT['keywords'], 'Android/server keyword agreement')
    check(translations[0].keys() == translations[1].keys(), 'Bilingual key parity')
    check(all(sorted(re.findall(r'%[0-9]+\$[ds]', v)) == sorted(re.findall(r'%[0-9]+\$[ds]', translations[1][k])) for k,v in translations[0].items()), 'Bilingual format argument parity')
    seed = read_source(SEED_PATH)
    for value in (CODE, CONTRACT['name'], CONTRACT['description'], "code = 'OTHER'", 'ON DUPLICATE KEY UPDATE tool_code = tool_code'):
        check(value in seed, 'Migration metadata: ' + value[:40])
    check('CREATE TABLE' not in seed.upper(), 'No server storage for wooden fish samples')
    definition=read_source(os.path.join(ROOT,'android/app/src/main/java/com/cangshuo/toolbox/feature/fish/FishToolDefinition.kt'))
    check('sortOrder = 642' in definition and 'ToolCategory.OTHER' in definition and 'ToolMode.LOCAL' in definition, 'Registry metadata agreement')
    container=read_source(os.path.join(ROOT,'android/app/src/main/java/com/cangshuo/toolbox/ToolboxAppContainer.kt'))
    check(container.count('FishToolDefinition(resources,woodenFishFactory)') == 1, 'Registered exactly once')
    docs = read_source(os.path.join(ROOT, 'docs/WOODEN_FISH.md'))
    check('56e5d672044862ffe3882acdbe2c5829734f3b51' in docs and 'MIT' in docs,
          'Wooden fish documentation records the fixed MIT source review')
    manifest = read_source(os.path.join(ROOT, 'android/app/src/main/AndroidManifest.xml'))
    check('android.permission.VIBRATE' in manifest, 'Wooden fish haptics use the declared VIBRATE permission')
    release_gate = read_source(os.path.join(ROOT, 'scripts/check_release_artifacts.py'))
    check('android.permission.VIBRATE' in release_gate, 'Release gate includes VIBRATE')
    permissions_doc = read_source(os.path.join(ROOT, 'docs/PERMISSIONS.md'))
    check('| VIBRATE |' in permissions_doc, 'Permission catalog documents VIBRATE')
    app_strings = read_source(os.path.join(resource_root, 'values/strings.xml'))
    app_strings_en = read_source(os.path.join(resource_root, 'values-en/strings.xml'))
    check('振动：仅在你打开振动器' in app_strings and 'Vibration: the motor is used only' in app_strings_en,
          'In-app permission explanation discloses vibration')
    permission_page = read_source(os.path.join(ROOT, 'deploy/site/permissions/index.html'))
    check('2026-10-20' in permission_page and '振动' in permission_page and 'Vibration uses' in permission_page,
          'Public permission page discloses vibration')

def offline_acceptance():
    checks=[]
    def check(condition,label):
        if not condition: raise AssertionError(label)
        checks.append(label)
        print('PASS',len(checks),label)
    check_static(check)
    print('Wooden fish offline contract:',len(checks),'passed')

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

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='90' AND success=1") == '1', 'V90 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V90 creates one wooden fish row')
    check(CODE in records(), 'public OTHER catalog includes wooden_fish')
    check_tool_metadata(check, records()[CODE], 'public wooden fish test', expected=defaults, public=True)
    detail = call(ADMIN_PATH, token=admin)
    listing = records(ADMIN_FILTER, admin)[CODE]
    check_tool_metadata(check, detail, 'admin wooden fish detail', defaults)
    check_tool_metadata(check, listing, 'admin wooden fish list', defaults)
    check(detail == listing, 'admin wooden fish detail and list agree')
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'OTHER' and row['enabled']]) == 1,
          'admin editor offers enabled OTHER category')
    edited = dict(defaults, name=u'电子木鱼验收', description=u'后台编辑的电子木鱼说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'wooden fish-admin'],
                  sortOrder=643, version=2, featured=True, requiresLogin=True,
                  configJson='{"acceptance":"wooden_fish"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'),
                        'edited wooden fish metadata', edited)
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    queries = ["SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
               "SELECT * FROM flyway_schema_history WHERE version='90'",
               "SELECT * FROM tool_usage_daily WHERE tool_code='%s' ORDER BY usage_date" % CODE,
               "SELECT * FROM tool_usage_stat WHERE tool_code='%s'" % CODE]
    snapshot = [sql(query) for query in queries]
    source = read_source(SEED_PATH)
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot, 'V90 replay preserves operator edits and usage')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'seed replay preserves edited wooden fish', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'),
                        'restored wooden fish metadata', defaults)
    daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE)
    total = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE)
    check(daily == total == '2', 'Wooden fish increments daily and all-time usage')
    popular = dict((row['code'], row) for row in call('tools/popular?limit=50')).get(CODE)
    check(popular is not None and popular['useCount'] == 2
          and all(popular[key] == CONTRACT[key] for key in ('name', 'description', 'icon')),
          'Wooden fish participates in popularity')
    for days in (7, 30, 90):
        data = call('admin/statistics?days=' + str(days), token=admin)
        check_statistics(check, data, days)
        for group, group_code in (('categories', 'OTHER'), ('modes', 'LOCAL')):
            rows = [row for row in data[group] if row['code'] == group_code]
            check(len(rows) == 1 and rows[0]['count'] >= 1 and rows[0]['opens'] >= 2,
                  str(days) + '-day ' + group + ' includes wooden fish usage')
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2,
              'Wooden fish participates in ' + str(days) + '-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify, strict_isolation=True)
    else:
        raise SystemExit('Usage: python -B scripts/check_wooden_fish_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
