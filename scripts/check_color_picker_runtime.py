# -*- coding: utf-8 -*-
"""V58 color_picker catalog/admin/statistics acceptance (Python 2/3 stdlib).

Offline: python -B scripts/check_color_picker_runtime.py --static
Deployment host: python -B scripts/check_color_picker_runtime.py <candidate-image>
Reuse check_admin_statistics_runtime.run: fresh schema, restricted DB user and
candidate container, with finally cleanup. Only that isolated database receives
catalog writes, migration replays and synthetic usage. This does not deploy an
image or verify Android image decoding, pixel accuracy, gestures or UI.

Contract: V58__seed_color_picker_tool.sql, bilingual Android resources and
ColorPickerToolDefinition.kt (IMAGE/LOCAL, sort 105). Source reviewed 2026-10-17:
https://github.com/skydoves/ColorPickerView/blob/main/colorpickerview/src/main/java/com/skydoves/colorpickerview/ColorPickerView.java
The moving main branch's onTouchReceived/getColorFromBitmap maps touch positions
to bounded bitmap pixels. This supports the image-picker metadata; no upstream
code, dependency or Android processing acceptance is included here.
The scripts create no temporary files; use -B to suppress import caches.
"""
from __future__ import print_function
from datetime import datetime, timedelta
import json
import os
import re
import sys
import uuid
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
from check_admin_statistics_runtime import run


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODE = 'color_picker'
CATALOG = 'tools?categoryCode=IMAGE&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=IMAGE&keyword=' + CODE + '&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V58__seed_color_picker_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
CONTRACT = {
    'code': CODE, 'name': u'取色器',
    'description': u'从图片中取色，点击或拖动取样并复制 HEX、RGB、HSL 颜色值。',
    'categoryCode': 'IMAGE', 'mode': 'LOCAL', 'icon': CODE, 'sortOrder': 105,
    'requiresLogin': False, 'featured': False, 'status': 'ENABLED', 'version': 1,
    'keywords': [u'取色', u'吸管', u'取色器', u'颜色', u'图片', 'color picker', 'eyedropper', 'hex'],
}
ENGLISH = {
    'name': 'Colour Picker',
    'description': 'Pick colours from an image and copy HEX, RGB or HSL values.',
    'keywords': ['colour picker', 'color picker', 'eyedropper', 'image', 'pixel', 'hex', 'rgb', 'hsl'],
}


def read_source(path):
    with open(path, 'rb') as stream:
        return stream.read().decode('utf-8')


def check_tool_metadata(check, row, label, expected=None, public=False):
    """The public DTO uses isFeatured; the administrator DTO uses featured."""
    expected = CONTRACT if expected is None else expected
    check(all(row.get(key) == expected[key] for key in ('name', 'description', 'keywords')),
          label + ' display metadata')
    check(all(row.get(key) == expected[key] for key in (
        'code', 'categoryCode', 'mode', 'icon', 'sortOrder', 'status', 'version'))
        and row.get('requiresLogin') is expected['requiresLogin'], label + ' registration')
    feature = 'isFeatured' if public else 'featured'
    check(row.get(feature) is expected['featured'], label + ' ' + feature)
    if public:
        check(not any(key in row for key in ('featured', 'id', 'categoryName', 'configJson', 'updatedAt')),
              label + ' public DTO omits administrator fields')
    elif 'configJson' in expected:
        check(json.loads(row['configJson']) == json.loads(expected['configJson']), label + ' configuration')


def check_catalog_page(check, page, label):
    rows = page['records']
    codes = [row['code'] for row in rows]
    check(page['page'] == 1 and page['pageSize'] == 100
          and len(codes) == len(set(codes)) == page['total'], label + ' complete and unique')
    check([(row['sortOrder'], row['code']) for row in rows]
          == sorted((row['sortOrder'], row['code']) for row in rows), label + ' stable order')
    return dict((row['code'], row) for row in rows)


def check_statistics(check, data, days):
    label = str(days) + '-day statistics'
    check(data['days'] == days and data['timezone'] == 'Asia/Shanghai' and len(data['daily']) == days,
          label + ' window and timezone')
    start = datetime.strptime(data['from'], '%Y-%m-%d')
    dates = [(start + timedelta(days=offset)).strftime('%Y-%m-%d') for offset in range(days)]
    check([row['date'] for row in data['daily']] == dates and data['to'] == dates[-1],
          label + ' contiguous dates')
    summary = data['summary']
    check(sum(row['opens'] for row in data['daily']) == summary['windowOpens']
          and data['daily'][-1]['opens'] == summary['todayOpens']
          and all(row['opens'] >= 0 for row in data['daily'])
          and summary['allTimeOpens'] >= summary['windowOpens'] >= summary['todayOpens'] >= 0,
          label + ' daily and all-time totals agree')
    check(all(summary[key] >= 0 for key in ('tools', 'enabledTools', 'disabledTools', 'maintenanceTools'))
          and summary['tools'] == sum(summary[key] for key in ('enabledTools', 'disabledTools', 'maintenanceTools')),
          label + ' tool state totals agree')
    ranking = data['ranking']
    check(len(ranking) <= 20 and len(set(row['code'] for row in ranking)) == len(ranking)
          and all(0 <= row['opens'] <= row['allTimeOpens'] <= summary['allTimeOpens'] for row in ranking),
          label + ' bounded unique ranking and counts')
    keys = [(-row['opens'], -row['allTimeOpens'], row['code']) for row in ranking]
    check(keys == sorted(keys), label + ' ranking order')
    for group in ('categories', 'modes'):
        rows = data[group]
        check(len(set(row['code'] for row in rows)) == len(rows)
              and all(row['count'] >= 0 and row['opens'] >= 0 for row in rows),
              label + ' unique nonnegative ' + group)


def check_seed_metadata(check):
    check(os.path.isfile(SEED_PATH), 'V58 catalog seed exists')
    body = '\n'.join(line for line in read_source(SEED_PATH).splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured'],
        'V58 only seeds existing catalog columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'IMAGE', CODE]
          + CONTRACT['keywords'] + ['LOCAL', 'ENABLED'], 'V58 display metadata and ordered keywords match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'IMAGE'\)", body) is not None,
          'V58 category resolves the IMAGE category by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*105\s*,\s*0\s*\)", body) is not None,
          'V58 mode, login, status, version, sort and featured defaults match')
    check(body.count(';') == 1 and re.search(
        r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
        'V58 is one metadata insert with a no-op duplicate-key replay')


def check_android_metadata(check):
    translations = {}
    for locale, expected in (('values', CONTRACT), ('values-en', ENGLISH)):
        resources = ET.parse(os.path.join(ROOT, 'android/app/src/main/res', locale, 'strings.xml')).getroot()
        strings = [node for node in resources.findall('string') if node.get('name', '').startswith(CODE + '_')]
        names = [node.get('name') for node in strings]
        check(len(names) == len(set(names)) and all(''.join(node.itertext()).strip() for node in strings),
              'Android ' + locale + ' picker strings are unique and nonempty')
        translations[locale] = dict((node.get('name'), ''.join(node.itertext())) for node in strings)
        for field in ('name', 'description'):
            check(translations[locale].get(CODE + '_' + field) == expected[field],
                  'Android ' + locale + ' picker ' + field + ' matches')
        arrays = resources.findall("string-array[@name='color_picker_keywords']")
        check(len(arrays) == 1, 'Android ' + locale + ' picker keywords exist once')
        keywords = [node.text for node in arrays[0].findall('item')]
        check(keywords == expected['keywords'] and len(set(keywords)) == len(keywords)
              and all(item and item.strip() for item in keywords),
              'Android ' + locale + ' ordered picker keywords match')
    check(set(translations['values']) == set(translations['values-en']),
          'Android picker Chinese and English string keys match')
    check(all(sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value)) == sorted(re.findall(
        r'%(?:\d+\$)?[a-zA-Z%]', translations['values-en'][name]))
        for name, value in translations['values'].items()), 'Android picker bilingual format placeholders match')


def check_android_registration(check):
    directory = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox')
    definition_path = os.path.join(directory, 'feature/colorpicker/ColorPickerToolDefinition.kt')
    definition = read_source(definition_path)
    for field, pattern in (
        ('code', r'code\s*=\s*"color_picker"'),
        ('name', r'name\s*=\s*resources\.getString\(R\.string\.color_picker_name\)'),
        ('description', r'description\s*=\s*resources\.getString\(R\.string\.color_picker_description\)'),
        ('category', r'category\s*=\s*ToolCategory\.IMAGE'),
        ('mode', r'mode\s*=\s*ToolMode\.LOCAL'),
        ('icon', r'icon\s*=\s*"color_picker"'),
        ('keywords', r'keywords\s*=\s*resources\.getStringArray\(R\.array\.color_picker_keywords\)\.toList\(\)'),
        ('sort', r'sortOrder\s*=\s*105\b'),
        ('featured', r'isFeatured\s*=\s*false\b'),
    ):
        check(len(re.findall(pattern, definition)) == 1, 'Android picker ToolDefinition ' + field)
    defaults = read_source(os.path.join(directory, 'core/model/ToolMetadata.kt'))
    for field, default in (('requiresLogin', 'false'), ('status', 'ToolStatus.ENABLED'), ('version', '1')):
        value = re.search(r'\b' + field + r'\s*=\s*([^,\n]+)', definition)
        check(value.group(1).strip() == default if value else re.search(
            r'val\s+' + field + r'\s*:\s*\w+\s*=\s*' + re.escape(default) + r'\s*,', defaults) is not None,
            'Android picker ' + field + ' agrees with catalog defaults')
    check(re.search(r'override\s+val\s+requiredPermissions\s*:\s*List<String>\s*=\s*emptyList\(\)', definition) is not None,
          'Android picker declares no extra runtime permissions')
    owners = []
    for current, unused, names in os.walk(JAVA_ROOT):
        for name in names:
            if name.endswith('ToolDefinition.kt'):
                path = os.path.join(current, name)
                owners.extend([os.path.normpath(path)] * len(re.findall(r'\bcode\s*=\s*"color_picker"', read_source(path))))
    check(owners == [os.path.normpath(definition_path)], 'Android picker code has exactly one ToolDefinition owner')
    container = read_source(os.path.join(directory, 'ToolboxAppContainer.kt'))
    registry = re.search(r'private fun bundledRegistry\b[\s\S]+?(?=private val registryStore)', container)
    check(registry is not None and 'ToolRegistry(' in registry.group(0)
          and len(re.findall(r'\bColorPickerToolDefinition\s*\(', registry.group(0))) == 1,
          'Android bundled ToolRegistry registers the picker exactly once')
    icons = read_source(os.path.join(directory, 'feature/home/ui/HomePresentation.kt'))
    check(len(re.findall(r'"color_picker"\s*->', icons)) == 1 and re.search(
        r'"color_picker"\s*->\s*R\.drawable\.ic_color_picker\b', icons) is not None,
        'Android picker has one explicit icon mapping')
    icon = ET.parse(os.path.join(ROOT, 'android/app/src/main/res/drawable/ic_color_picker.xml')).getroot()
    android = '{http://schemas.android.com/apk/res/android}'
    check(icon.tag == 'vector' and icon.get(android + 'width') == '24dp'
          and icon.get(android + 'height') == '24dp'
          and icon.get(android + 'viewportWidth') == '24' and icon.get(android + 'viewportHeight') == '24'
          and any(node.get(android + 'pathData', '').strip() for node in icon.iter('path')),
          'Android picker ships a nonempty 24dp vector icon')


def check_static(check):
    check_seed_metadata(check)
    check_android_metadata(check)
    check_android_registration(check)


def offline_acceptance():
    checks = [0]

    def check(condition, label):
        if not condition:
            raise AssertionError(label + ' (content omitted)')
        checks[0] += 1
        print('PASS', checks[0], label)

    check_static(check)
    print('Color picker offline static acceptance:', checks[0],
          'checks passed; no network, Docker or database access; Android/runtime/deployment acceptance not executed')


def verify(check, request, sql, admin):
    """Mutations here are exclusively through the isolated harness callbacks."""
    check_static(check)
    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='58' AND success=1") == '1', 'V58 applied')
    seed = read_source(SEED_PATH)
    defaults = dict(CONTRACT, configJson='{}')

    def call(path, payload=None, token=None, method=None, status=200, code=0):
        actual, body, cache = request(path, payload, token=token, method=method)
        check(actual == status and body.get('code') == code, (method or 'GET') + ' ' + path)
        if path.startswith('admin/statistics') and status == 200:
            check(cache == 'no-store', 'statistics responses forbid caching')
        return body.get('data')

    def records(path=CATALOG, token=None):
        return check_catalog_page(check, call(path, token=token), path)

    def statistics(days):
        data = call('admin/statistics?days=' + str(days), token=admin)
        check_statistics(check, data, days)
        return data

    def usage_counts():
        return tuple(sql("SELECT COALESCE(SUM(use_count),0) FROM %s WHERE tool_code='%s'" % (table, CODE))
                     for table in ('tool_usage_daily', 'tool_usage_stat'))

    def replay(label):
        queries = ["SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
                   "SELECT * FROM flyway_schema_history WHERE version='58'",
                   "SELECT * FROM tool_usage_daily WHERE tool_code='%s' ORDER BY usage_date" % CODE,
                   "SELECT * FROM tool_usage_stat WHERE tool_code='%s'" % CODE]
        snapshot = [sql(query) for query in queries]
        sql(seed)
        sql(seed)
        check([sql(query) for query in queries] == snapshot, label)
        check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
              'V58 replay keeps one picker row, including deleted rows')

    def popular():
        rows = call('tools/popular?limit=50')
        check(len(rows) <= 50 and len(set(row['code'] for row in rows)) == len(rows)
              and all(row['useCount'] >= 1 for row in rows), 'popular tools are bounded, unique and used')
        return dict((row['code'], row) for row in rows)

    check(CODE in records(), 'public IMAGE catalog includes picker')
    check_tool_metadata(check, records()[CODE], 'public picker', public=True)
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'admin picker detail', defaults)
    check_tool_metadata(check, records(ADMIN_FILTER, admin)[CODE], 'admin picker list', defaults)
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'IMAGE' and row['enabled']]) == 1,
          'admin editor offers the enabled IMAGE category')

    account = {'email': 'picker-' + uuid.uuid4().hex + '@example.invalid',
               'password': uuid.uuid4().hex, 'nickname': 'catalog-acceptance'}
    user = call('auth/register', account, method='POST', status=201)['accessToken']
    denied = dict(defaults, code=CODE + '_denied_check')
    try:
        check_tool_metadata(check, records(token=user)[CODE], 'ordinary user public picker', public=True)
        probes = [('GET', None, ADMIN_PATH), ('GET', None, ADMIN_FILTER),
                  ('GET', None, 'admin/tools/categories'), ('POST', denied, 'admin/tools'),
                  ('PUT', defaults, ADMIN_PATH), ('PATCH', {'status': 'MAINTENANCE'}, ADMIN_PATH + '/status'),
                  ('DELETE', None, ADMIN_PATH)]
        probes.extend(('GET', None, 'admin/statistics?days=' + str(days)) for days in (7, 30, 90))
        snapshot = sql("SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE)
        for token, status, error in ((None, 401, 10002), (user, 403, 10005)):
            for method, payload, path in probes:
                call(path, payload, token=token, method=method, status=status, code=error)
        check(sql("SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE) == snapshot
              and sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % denied['code']) == '0',
              'rejected admin reads/writes neither edit nor create picker rows')
    finally:
        call('auth/me', {'email': account['email'], 'password': account['password']}, token=user, method='DELETE')

    probe = dict(defaults, code=CODE + '_admin_check')
    probe_path = 'admin/tools/' + probe['code']
    call('admin/tools', dict(probe, categoryCode='NOPE'), token=admin, method='POST', status=404, code=30005)
    call('admin/tools', dict(probe, configJson='[]'), token=admin, method='POST', status=400, code=10001)
    check_tool_metadata(check, call('admin/tools', probe, token=admin, method='POST', status=201),
                        'admin-created picker probe', probe)
    check_tool_metadata(check, call(probe_path, token=admin), 'persisted picker probe', probe)
    check_tool_metadata(check, records(ADMIN_FILTER, admin)[probe['code']], 'admin list created picker probe', probe)
    check_tool_metadata(check, records()[probe['code']], 'public created picker probe', probe, public=True)
    call('admin/tools', probe, token=admin, method='POST', status=409, code=30004)
    call('admin/tools', defaults, token=admin, method='POST', status=409, code=30004)
    call(probe_path, token=admin, method='DELETE')
    call(probe_path, token=admin, status=404, code=10006)
    check(probe['code'] not in records(), 'deleted creation probe hidden from catalog')
    replay('fresh V58 replay preserves defaults, timestamps, Flyway history and usage')
    check(usage_counts() == ('0', '0') and CODE not in popular(), 'fresh picker has no synthetic opens or popularity')
    before = dict((days, statistics(days)) for days in (7, 30, 90))
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    check(usage_counts() == ('2', '2'), 'picker increments daily and all-time counters twice')
    check(sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s' "
              "AND usage_date=DATE(UTC_TIMESTAMP(3)+INTERVAL 8 HOUR)" % CODE) == '2',
          'picker daily opens use the Beijing date')
    row = popular()[CODE]
    check(all(row[key] == CONTRACT[key] for key in ('code', 'name', 'description', 'icon'))
          and row['useCount'] == 2, 'picker popularity metadata and counters match')
    for days in (7, 30, 90):
        data = statistics(days)
        ranks = [row for row in data['ranking'] if row['code'] == CODE]
        check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2
              and ranks[0]['name'] == CONTRACT['name'] and ranks[0]['status'] == 'ENABLED',
              'picker participates in ' + str(days) + '-day ranking')
        check(all(data['summary'][key] == before[days]['summary'][key] + 2
                  for key in ('allTimeOpens', 'windowOpens', 'todayOpens')),
              'picker contributes two opens to ' + str(days) + '-day totals')
        for group, code in (('categories', 'IMAGE'), ('modes', 'LOCAL')):
            old = next(row for row in before[days][group] if row['code'] == code)
            current = next(row for row in data[group] if row['code'] == code)
            check(current['opens'] == old['opens'] + 2 and current['count'] == old['count'],
                  'picker contributes to ' + str(days) + '-day ' + group)

    edited = dict(defaults, name=u'取色目录验收', description=u'后台编辑的图片取色说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'picker-admin'], sortOrder=106,
                  version=2, featured=True, requiresLogin=True, configJson='{"acceptance":"color_picker"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'), 'edited picker', edited)
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'persisted picker edit', edited)
    check_tool_metadata(check, records(ADMIN_FILTER, admin)[CODE], 'admin list picker edit', edited)
    check_tool_metadata(check, records()[CODE], 'public picker edit', edited, public=True)
    replay('V58 replay preserves operator edits, configuration, timestamps and usage')
    call(ADMIN_PATH + '/status', {'status': 'INVALID'}, token=admin, method='PATCH', status=400, code=10001)
    call(ADMIN_PATH, dict(edited, configJson='[]'), token=admin, method='PUT', status=400, code=10001)
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'invalid edits preserve picker', edited)

    for state, counter in (('DISABLED', 'disabledTools'), ('MAINTENANCE', 'maintenanceTools')):
        expected = dict(edited, status=state)
        check_tool_metadata(check, call(ADMIN_PATH + '/status', {'status': state}, token=admin, method='PATCH'),
                            'admin picker ' + state, expected)
        check(CODE not in records() and CODE not in popular(), state + ' picker hidden from catalog and popularity')
        call('tools/' + CODE + '/usage', method='POST', status=404, code=10006)
        check(usage_counts() == ('2', '2'), state + ' picker rejects opens without counter changes')
        replay('V58 replay preserves ' + state + ', edits, timestamps and usage')
        state_filter = ADMIN_FILTER.replace('status=ENABLED', 'status=' + state)
        check_tool_metadata(check, records(state_filter, admin)[CODE], 'admin filtered ' + state, expected)
        check(CODE not in records(ADMIN_FILTER, admin), state + ' picker excluded from ENABLED admin filter')
        for days in (7, 30, 90):
            data = statistics(days)
            ranks = [row for row in data['ranking'] if row['code'] == CODE]
            check(len(ranks) == 1 and ranks[0]['status'] == state and ranks[0]['name'] == edited['name']
                  and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2,
                  state + ' picker retains ' + str(days) + '-day historical ranking')
            check(data['summary'][counter] == before[days]['summary'][counter] + 1
                  and data['summary']['enabledTools'] == before[days]['summary']['enabledTools'] - 1
                  and data['summary']['tools'] == before[days]['summary']['tools'],
                  state + ' picker participates in ' + str(days) + '-day state totals')

    check_tool_metadata(check, call(ADMIN_PATH + '/status', {'status': 'ENABLED'}, token=admin, method='PATCH'),
                        're-enabled admin picker', edited)
    check_tool_metadata(check, records()[CODE], 're-enabled public picker', edited, public=True)
    row = popular()[CODE]
    check(row['useCount'] == 2 and all(row[key] == edited[key] for key in ('name', 'description', 'icon')),
          're-enabled picker returns to popularity with edited metadata')
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'), 'restored picker', defaults)
    call(ADMIN_PATH, token=admin, method='DELETE')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s' AND deleted_at IS NOT NULL" % CODE) == '1',
          'admin picker deletion is a soft delete')
    replay('V58 replay preserves soft deletion, catalog fields, timestamps and historical usage')
    for method, payload, path in (('GET', None, ADMIN_PATH), ('PUT', defaults, ADMIN_PATH),
                                  ('PATCH', {'status': 'ENABLED'}, ADMIN_PATH + '/status'),
                                  ('DELETE', None, ADMIN_PATH), ('POST', None, 'tools/' + CODE + '/usage')):
        call(path, payload, token=admin if path.startswith('admin/') else None,
             method=method, status=404, code=10006)
    call('admin/tools', defaults, token=admin, method='POST', status=409, code=30004)
    check(CODE not in records(ADMIN_FILTER, admin) and CODE not in records() and CODE not in popular(),
          'deleted picker absent from admin/public lists and popularity')
    check(usage_counts() == ('2', '2'), 'deleted picker retains both historical counters')
    for days in (7, 30, 90):
        data = statistics(days)
        old_summary = before[days]['summary']
        check(CODE not in [row['code'] for row in data['ranking']], 'deleted picker excluded from ranking')
        check(data['summary']['tools'] == old_summary['tools'] - 1
              and data['summary']['enabledTools'] == old_summary['enabledTools'] - 1
              and all(data['summary'][key] == old_summary[key] + 2
                      for key in ('allTimeOpens', 'windowOpens', 'todayOpens')),
              'soft deletion retains aggregate history for ' + str(days) + ' days')
        for group, code in (('categories', 'IMAGE'), ('modes', 'LOCAL')):
            old = next(row for row in before[days][group] if row['code'] == code)
            current = next(row for row in data[group] if row['code'] == code)
            check(current['count'] == old['count'] - 1 and current['opens'] == old['opens'],
                  'deleted picker excluded from ' + str(days) + '-day ' + group + ' distribution')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify)
    else:
        raise SystemExit('Usage: python -B scripts/check_color_picker_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
