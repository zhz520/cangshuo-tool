# -*- coding: utf-8 -*-
"""V68 world_clock static and isolated backend acceptance (Python 2/3 stdlib).

Offline: python -B scripts/check_world_clock_runtime.py --static
Deployment host: python -B scripts/check_world_clock_runtime.py <candidate-image>

The static mode verifies the V68 metadata, bilingual Android resources,
ToolDefinition contract, bundled registration, icon mapping and 24dp vector.
Candidate mode reuses check_admin_statistics_runtime.run for a fresh schema,
restricted database user and isolated server container with guaranteed cleanup.
It does not deploy an image or execute Android clock rendering, time-zone rules,
SavedState, copy/share, device UI or accessibility.
"""
from __future__ import print_function
import os
import re
import sys
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
from check_admin_statistics_runtime import run
from check_color_picker_runtime import check_catalog_page, check_statistics, check_tool_metadata, read_source


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODE = 'world_clock'
PREFIX = 'world_clock_'
CATALOG = 'tools?categoryCode=LIFE&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=LIFE&keyword=' + CODE + '&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V68__seed_world_clock_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
RES_ROOT = os.path.join(ROOT, 'android/app/src/main/res')
FEATURE_ROOT = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/worldclock')
KEYWORDS_ZH = [u'世界时间', u'时区', u'城市时间', u'世界时钟', 'UTC', u'换算', u'夏令时',
               'world clock', 'timezone', 'clock']
KEYWORDS_EN = ['world clock', 'timezone', 'time zone', 'utc', 'clock', 'daylight saving',
               'city time', u'世界时间', u'世界时钟', u'换算']
CONTRACT = {
    'code': CODE,
    'name': u'世界时间',
    'description': u'查看多时区实时时间、日期日差、UTC 偏移与夏令时，支持城市搜索、12/24 小时制和分享。',
    'categoryCode': 'LIFE',
    'mode': 'LOCAL',
    'icon': CODE,
    'sortOrder': 621,
    'requiresLogin': False,
    'featured': False,
    'status': 'ENABLED',
    'version': 1,
    'keywords': KEYWORDS_ZH,
}
ENGLISH = {
    'name': 'World Clock',
    'description': 'Compare live times across time zones with date differences, UTC offsets, daylight saving, '
                   'city search, a 12/24-hour switch and sharing.',
    'keywords': KEYWORDS_EN,
}


def read_optional(path):
    try:
        return read_source(path)
    except (IOError, OSError):
        return None


def check_seed_metadata(check):
    source = read_optional(SEED_PATH)
    check(source is not None, 'V68 catalog seed exists')
    if source is None:
        return
    body = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured'],
        'V68 only seeds existing catalog columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'LIFE', CODE]
          + KEYWORDS_ZH + ['LOCAL', 'ENABLED'],
          'V68 display metadata and ordered Chinese keywords match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'LIFE'\)", body) is not None,
          'V68 category resolves LIFE by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*621\s*,\s*0\s*\)", body) is not None,
          'V68 mode, login, status, version, sort and featured defaults match')
    check(body.count('INSERT INTO tool_definition') == 1 and body.count(';') == 1
          and re.search(r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
          'V68 is one metadata insert with a no-op duplicate-key replay')


def android_string_references():
    references = set()
    if not os.path.isdir(FEATURE_ROOT):
        return None
    for current, unused, names in os.walk(FEATURE_ROOT):
        for name in names:
            if not name.endswith('.kt'):
                continue
            source = read_optional(os.path.join(current, name)) or ''
            references.update(re.findall(r'R\.string\.(world_clock_[A-Za-z0-9_]+)', source))
    return references


def check_android_metadata(check):
    references = android_string_references()
    check(references is not None and {'world_clock_name', 'world_clock_description'}.issubset(references),
          'Android world_clock feature references bilingual metadata resources')
    translations = {}
    for locale, expected, keywords in (('values', CONTRACT, KEYWORDS_ZH), ('values-en', ENGLISH, KEYWORDS_EN)):
        path = os.path.join(RES_ROOT, locale, 'strings.xml')
        source = read_optional(path)
        check(source is not None, 'Android ' + locale + ' world_clock resources exist')
        if source is None:
            continue
        try:
            resources = ET.fromstring(source.encode('utf-8'))
        except ET.ParseError:
            check(False, 'Android ' + locale + ' strings.xml parses')
            continue
        check(True, 'Android ' + locale + ' strings.xml parses')
        strings = [node for node in resources.findall('string') if node.get('name', '').startswith(PREFIX)]
        names = [node.get('name') for node in strings]
        check(len(names) == len(set(names)) and all(''.join(node.itertext()).strip() for node in strings),
              'Android ' + locale + ' world_clock strings are unique and nonempty')
        translations[locale] = dict((node.get('name'), ''.join(node.itertext())) for node in strings)
        for field in ('name', 'description'):
            check(translations[locale].get(PREFIX + field) == expected[field],
                  'Android ' + locale + ' world_clock ' + field + ' matches')
        arrays = resources.findall("string-array[@name='world_clock_keywords']")
        check(len(arrays) == 1, 'Android ' + locale + ' world_clock keywords exist once')
        if len(arrays) == 1:
            actual = [(node.text or '').strip() for node in arrays[0].findall('item')]
            check(actual == keywords and len(set(actual)) == len(actual) and all(actual),
                  'Android ' + locale + ' ordered world_clock keywords match')
        if references is not None:
            check(not sorted(references - set(translations[locale])),
                  'Android ' + locale + ' covers every referenced world_clock string')
    if 'values' in translations and 'values-en' in translations:
        check(set(translations['values']) == set(translations['values-en']),
              'Android world_clock Chinese and English string keys match')

        def placeholders(value):
            return sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value))

        check(all(placeholders(value) == placeholders(translations['values-en'][name])
                  for name, value in translations['values'].items()),
              'Android world_clock bilingual format placeholders match')
        plural_zh = resources_plural_names('values')
        plural_en = resources_plural_names('values-en')
        check(plural_zh == plural_en, 'Android world_clock bilingual plural keys match')


def resources_plural_names(locale):
    source = read_optional(os.path.join(RES_ROOT, locale, 'strings.xml')) or ''
    try:
        resources = ET.fromstring(source.encode('utf-8'))
    except ET.ParseError:
        return set()
    return set(node.get('name') for node in resources.findall('plurals')
               if node.get('name', '').startswith(PREFIX))


def definition_owners():
    owners = []
    if not os.path.isdir(JAVA_ROOT):
        return owners
    for current, unused, names in os.walk(JAVA_ROOT):
        for name in names:
            if not name.endswith('ToolDefinition.kt'):
                continue
            path = os.path.join(current, name)
            source = read_optional(path) or ''
            owners.extend([os.path.normpath(path)] * len(re.findall(r'\bcode\s*=\s*"world_clock"', source)))
    return owners


def check_android_registration(check):
    owners = definition_owners()
    check(len(owners) == 1, 'Android world_clock code has exactly one ToolDefinition owner')
    if not owners:
        return
    definition_path = owners[0]
    definition = read_optional(definition_path)
    check(definition is not None, 'Android world_clock ToolDefinition is readable')
    if definition is None:
        return
    for field, pattern in (
        ('code', r'code\s*=\s*"world_clock"'),
        ('name', r'name\s*=\s*resources\.getString\(R\.string\.world_clock_name\)'),
        ('description', r'description\s*=\s*resources\.getString\(R\.string\.world_clock_description\)'),
        ('category', r'category\s*=\s*ToolCategory\.LIFE'),
        ('mode', r'mode\s*=\s*ToolMode\.LOCAL'),
        ('icon', r'icon\s*=\s*"world_clock"'),
        ('keywords', r'keywords\s*=\s*resources\.getStringArray\(R\.array\.world_clock_keywords\)\.toList\(\)'),
        ('sort', r'sortOrder\s*=\s*621\b'),
        ('featured', r'isFeatured\s*=\s*false\b'),
    ):
        check(len(re.findall(pattern, definition)) == 1,
              'Android world_clock ToolDefinition ' + field)
    check(re.search(r'override\s+val\s+requiredPermissions\s*:\s*List<String>\s*=\s*emptyList\(\)', definition)
          is not None, 'Android world_clock declares no extra runtime permissions')

    container_path = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt')
    container = read_optional(container_path)
    check(container is not None, 'Android bundled ToolRegistry source exists')
    if container is not None:
        registry = re.search(r'private fun bundledRegistry\b[\s\S]+?(?=private val registryStore)', container)
        class_name = os.path.splitext(os.path.basename(definition_path))[0]
        check(registry is not None and 'ToolRegistry(' in registry.group(0)
              and len(re.findall(r'\b' + re.escape(class_name) + r'\s*\(', registry.group(0))) == 1,
              'Android bundled ToolRegistry registers world_clock exactly once')
    icons_path = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/home/ui/HomePresentation.kt')
    icons = read_optional(icons_path)
    check(icons is not None and len(re.findall(r'"world_clock"\s*->', icons)) == 1
          and re.search(r'"world_clock"\s*->\s*R\.drawable\.ic_world_clock\b', icons) is not None,
          'Android world_clock has one explicit icon mapping')
    icon_path = os.path.join(RES_ROOT, 'drawable/ic_world_clock.xml')
    icon_source = read_optional(icon_path)
    check(icon_source is not None, 'Android world_clock vector icon exists')
    if icon_source is not None:
        try:
            icon = ET.fromstring(icon_source)
        except ET.ParseError:
            icon = None
        android = '{http://schemas.android.com/apk/res/android}'
        check(icon is not None and icon.tag == 'vector' and icon.get(android + 'width') == '24dp'
              and icon.get(android + 'height') == '24dp'
              and icon.get(android + 'viewportWidth') == '24'
              and icon.get(android + 'viewportHeight') == '24'
              and any(node.get(android + 'pathData', '').strip() for node in icon.iter('path')),
              'Android world_clock ships a nonempty 24dp vector icon')


def check_static(check):
    check_seed_metadata(check)
    check_android_metadata(check)
    check_android_registration(check)


def offline_acceptance():
    checks = [0]
    failures = []

    def check(condition, label):
        checks[0] += 1
        if condition:
            print('PASS', checks[0], label)
        else:
            failures.append(label)
            print('FAIL', checks[0], label)

    check_static(check)
    print('World Clock offline static acceptance:', checks[0] - len(failures), '/', checks[0],
          'checks passed;', len(failures), 'failed; no network, Docker or database access;'
          ' Android/runtime/device/deployment acceptance not executed')
    if failures:
        raise SystemExit(1)


def verify(check, request, sql, admin):
    """Mutations here are exclusively through the isolated harness callbacks."""
    check_static(check)
    source = read_source(SEED_PATH)
    defaults = dict(CONTRACT, configJson='{}')

    def call(path, payload=None, token=None, method=None, status=200, code=0):
        actual, body, cache = request(path, payload, token=token, method=method)
        check(actual == status and body.get('code') == code, (method or 'GET') + ' ' + path)
        if path.startswith('admin/statistics') and status == 200:
            check(cache == 'no-store', 'statistics responses forbid caching')
        return body.get('data')

    def records(path=CATALOG, token=None):
        return check_catalog_page(check, call(path, token=token), path)

    def usage_counts():
        return tuple(sql("SELECT COALESCE(SUM(use_count),0) FROM %s WHERE tool_code='%s'" % (table, CODE))
                     for table in ('tool_usage_daily', 'tool_usage_stat'))

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='68' AND success=1") == '1', 'V68 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V68 creates one world_clock row')
    check(CODE in records(), 'public LIFE catalog includes world_clock')
    check_tool_metadata(check, records()[CODE], 'public world_clock', expected=defaults, public=True)
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'admin world_clock detail', defaults)
    check_tool_metadata(check, records(ADMIN_FILTER, admin)[CODE], 'admin world_clock list', defaults)
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'LIFE' and row['enabled']]) == 1,
          'admin editor offers the enabled LIFE category')

    edited = dict(defaults, name=u'世界时间验收', description=u'后台编辑的世界时间说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'world-clock-admin'], sortOrder=622,
                  version=2, featured=True, requiresLogin=True,
                  configJson='{"acceptance":"world_clock"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'), 'edited world_clock', edited)
    queries = ["SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
               "SELECT * FROM flyway_schema_history WHERE version='68'",
               "SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE,
               "SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE]
    snapshot = [sql(query) for query in queries]
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot,
          'V68 replay preserves operator edits, configuration, timestamps and usage')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V68 replay keeps one world_clock row, including deleted rows')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'replayed world_clock', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'), 'restored world_clock', defaults)

    check(usage_counts() == ('0', '0'), 'fresh world_clock has no synthetic opens')
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    check(usage_counts() == ('2', '2'), 'world_clock increments daily and all-time counters twice')
    check(sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s' "
              "AND usage_date=DATE(UTC_TIMESTAMP(3)+INTERVAL 8 HOUR)" % CODE) == '2',
          'world_clock daily opens use the Beijing date')
    popular = call('tools/popular?limit=50')
    popular_map = dict((row['code'], row) for row in popular)
    row = popular_map.get(CODE)
    check(row is not None and row['useCount'] == 2
          and all(row[key] == defaults[key] for key in ('name', 'description', 'icon')),
          'world_clock popularity metadata and counters match')
    data = call('admin/statistics?days=7', token=admin)
    check_statistics(check, data, 7)
    ranks = [row for row in data['ranking'] if row['code'] == CODE]
    check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2
          and ranks[0]['name'] == defaults['name'] and ranks[0]['status'] == 'ENABLED',
          'world_clock participates in 7-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify)
    else:
        raise SystemExit('Usage: python -B scripts/check_world_clock_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
