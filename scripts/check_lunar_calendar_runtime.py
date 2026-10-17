# -*- coding: utf-8 -*-
"""V70 lunar_calendar static and isolated backend acceptance (Python 2/3)."""
from __future__ import print_function
import os
import re
import sys
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
from check_admin_statistics_runtime import run
from check_color_picker_runtime import check_catalog_page, check_statistics, check_tool_metadata, read_source


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODE = 'lunar_calendar'
PREFIX = 'calendar_'
CATALOG = 'tools?categoryCode=LIFE&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=LIFE&keyword=' + CODE + '&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V70__seed_lunar_calendar_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
RES_ROOT = os.path.join(ROOT, 'android/app/src/main/res')
FEATURE_ROOT = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/calendar')
KEYWORDS_ZH = [u'万年历', u'日历', u'农历', u'黄历', u'干支', u'生肖', u'节气', u'宜忌',
               'calendar', 'lunar', 'almanac', u'老黄历']
KEYWORDS_EN = ['calendar', 'lunar', 'almanac', 'ganzhi', 'zodiac', 'solar term', 'festival',
               'perpetual', u'万年历', u'农历', u'黄历', u'宜忌']
CONTRACT = {
    'code': CODE,
    'name': u'万年历',
    'description': u'查看公历月历、农历日期、干支生肖、节气、节日与每日宜忌，支持月份切换和日期详情。',
    'categoryCode': 'LIFE',
    'mode': 'LOCAL',
    'icon': CODE,
    'sortOrder': 623,
    'requiresLogin': False,
    'featured': False,
    'status': 'ENABLED',
    'version': 1,
    'keywords': KEYWORDS_ZH,
}
ENGLISH = {
    'name': 'Perpetual Calendar',
    'description': 'Browse Gregorian months with lunar dates, ganzhi and zodiac, solar terms, festivals and daily '
                   'almanac notes.',
    'keywords': KEYWORDS_EN,
}


def read_optional(path):
    try:
        return read_source(path)
    except (IOError, OSError):
        return None


def check_seed_metadata(check):
    source = read_optional(SEED_PATH)
    check(source is not None, 'V70 catalog seed exists')
    if source is None:
        return
    body = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured'],
        'V70 only seeds existing catalog columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'LIFE', CODE]
          + KEYWORDS_ZH + ['LOCAL', 'ENABLED'],
          'V70 display metadata and ordered Chinese keywords match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'LIFE'\)", body) is not None,
          'V70 category resolves LIFE by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*623\s*,\s*0\s*\)", body) is not None,
          'V70 mode, login, status, version, sort and featured defaults match')
    check(body.count('INSERT INTO tool_definition') == 1 and body.count(';') == 1
          and re.search(r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
          'V70 is one metadata insert with a no-op duplicate-key replay')


def android_references():
    references = set()
    if not os.path.isdir(FEATURE_ROOT):
        return None
    for current, unused, names in os.walk(FEATURE_ROOT):
        for name in names:
            if name.endswith('.kt'):
                source = read_optional(os.path.join(current, name)) or ''
                references.update(re.findall(r'R\.string\.(calendar_[A-Za-z0-9_]+)', source))
    return references


def check_android_metadata(check):
    references = android_references()
    check(references is not None and {'calendar_name', 'calendar_description'}.issubset(references),
          'Android calendar feature references bilingual resources')
    translations = {}
    for locale, expected, keywords in (('values', CONTRACT, KEYWORDS_ZH), ('values-en', ENGLISH, KEYWORDS_EN)):
        source = read_optional(os.path.join(RES_ROOT, locale, 'strings.xml'))
        check(source is not None, 'Android ' + locale + ' calendar resources exist')
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
              'Android ' + locale + ' calendar strings are unique and nonempty')
        translations[locale] = dict((node.get('name'), ''.join(node.itertext())) for node in strings)
        for field in ('name', 'description'):
            check(translations[locale].get(PREFIX + field) == expected[field],
                  'Android ' + locale + ' calendar ' + field + ' matches')
        arrays = resources.findall("string-array[@name='calendar_keywords']")
        check(len(arrays) == 1, 'Android ' + locale + ' calendar keywords exist once')
        if len(arrays) == 1:
            actual = [(node.text or '').strip() for node in arrays[0].findall('item')]
            check(actual == keywords and len(set(actual)) == len(actual) and all(actual),
                  'Android ' + locale + ' ordered calendar keywords match')
        check(not sorted(references - set(translations[locale])),
              'Android ' + locale + ' covers referenced calendar strings')
        for array_name, size in (('calendar_weekdays', 7), ('calendar_zodiac_names', 12)):
            nodes = resources.findall("string-array[@name='" + array_name + "']")
            check(len(nodes) == 1 and len(nodes[0].findall('item')) == size,
                  'Android ' + locale + ' ' + array_name + ' has ' + str(size) + ' items')
    if 'values' in translations and 'values-en' in translations:
        check(set(translations['values']) == set(translations['values-en']),
              'Android calendar Chinese and English string keys match')

        def placeholders(value):
            return sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value))

        check(all(placeholders(value) == placeholders(translations['values-en'][name])
                  for name, value in translations['values'].items()),
              'Android calendar bilingual format placeholders match')


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
            owners.extend([os.path.normpath(path)] * len(re.findall(r'\bcode\s*=\s*"lunar_calendar"', source)))
    return owners


def check_android_registration(check):
    owners = definition_owners()
    check(len(owners) == 1, 'Android lunar_calendar code has exactly one ToolDefinition owner')
    if not owners:
        return
    definition_path = owners[0]
    definition = read_optional(definition_path)
    check(definition is not None, 'Android lunar_calendar ToolDefinition is readable')
    if definition is None:
        return
    for field, pattern in (
        ('code', r'code\s*=\s*"lunar_calendar"'),
        ('name', r'name\s*=\s*resources\.getString\(R\.string\.calendar_name\)'),
        ('description', r'description\s*=\s*resources\.getString\(R\.string\.calendar_description\)'),
        ('category', r'category\s*=\s*ToolCategory\.LIFE'),
        ('mode', r'mode\s*=\s*ToolMode\.LOCAL'),
        ('icon', r'icon\s*=\s*"lunar_calendar"'),
        ('keywords', r'keywords\s*=\s*resources\.getStringArray\(R\.array\.calendar_keywords\)\.toList\(\)'),
        ('sort', r'sortOrder\s*=\s*623\b'),
        ('featured', r'isFeatured\s*=\s*false\b'),
    ):
        check(len(re.findall(pattern, definition)) == 1,
              'Android lunar_calendar ToolDefinition ' + field)
    check(re.search(r'override\s+val\s+requiredPermissions\s*:\s*List<String>\s*=\s*emptyList\(\)', definition)
          is not None, 'Android lunar_calendar declares no extra runtime permissions')
    container = read_optional(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt'))
    check(container is not None, 'Android bundled ToolRegistry source exists')
    if container is not None:
        registry = re.search(r'private fun bundledRegistry\b[\s\S]+?(?=private val registryStore)', container)
        check(registry is not None and 'ToolRegistry(' in registry.group(0)
              and len(re.findall(r'\bLunarCalendarToolDefinition\s*\(', registry.group(0))) == 1,
              'Android bundled ToolRegistry registers lunar_calendar exactly once')
    icons = read_optional(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/home/ui/HomePresentation.kt'))
    check(icons is not None and len(re.findall(r'"lunar_calendar"\s*->', icons)) == 1
          and re.search(r'"lunar_calendar"\s*->\s*R\.drawable\.ic_lunar_calendar\b', icons) is not None,
          'Android lunar_calendar has one explicit icon mapping')
    icon_source = read_optional(os.path.join(RES_ROOT, 'drawable/ic_lunar_calendar.xml'))
    check(icon_source is not None, 'Android lunar_calendar vector icon exists')
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
              'Android lunar_calendar ships a nonempty 24dp vector icon')


def check_domain(check):
    catalog = read_optional(os.path.join(ROOT, 'android/gradle/libs.versions.toml')) or ''
    check(re.search(r'^lunar\s*=\s*"1\.7\.7"', catalog, re.M) is not None,
          'Android pins lunar-java 1.7.7')
    repository = read_optional(os.path.join(FEATURE_ROOT, 'data/LunarCalendarRepository.kt')) or ''
    for token in ('Solar.fromYmd', 'lunar.dayYi', 'lunar.dayJi', 'lunar.festivals', 'lunar.jieQi',
                  'yearInGanZhi', 'monthInGanZhi', 'dayInGanZhi', 'yearShengXiao'):
        check(token in repository, 'calendar repository uses ' + token)
    models = read_optional(os.path.join(FEATURE_ROOT, 'domain/CalendarModels.kt')) or ''
    check('MIN_YEAR = 1900' in models and 'MAX_YEAR = 2100' in models and 'GRID_DAYS = 42' in models,
          'calendar range and six-week grid contract are explicit')
    check('ZoneId.of("Asia/Shanghai")' in models, 'calendar today uses Beijing calendar zone')


def check_static(check):
    check_seed_metadata(check)
    check_android_metadata(check)
    check_android_registration(check)
    check_domain(check)


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
    print('Lunar Calendar offline static acceptance:', checks[0] - len(failures), '/', checks[0],
          'checks passed;', len(failures), 'failed; no network, Docker or database access;'
          ' Android/runtime/device/deployment acceptance not executed')
    if failures:
        raise SystemExit(1)


def verify(check, request, sql, admin):
    check_static(check)
    source = read_source(SEED_PATH)
    defaults = dict(CONTRACT, configJson='{}')

    def call(path, payload=None, token=None, method=None, status=200, code=0):
        actual, body, cache = request(path, payload, token=token, method=method)
        check(actual == status and body.get('code') == code, (method or 'GET') + ' ' + path)
        return body.get('data')

    def records(path=CATALOG, token=None):
        return check_catalog_page(check, call(path, token=token), path)

    def usage_counts():
        return tuple(sql("SELECT COALESCE(SUM(use_count),0) FROM %s WHERE tool_code='%s'" % (table, CODE))
                     for table in ('tool_usage_daily', 'tool_usage_stat'))

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='70' AND success=1") == '1', 'V70 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V70 creates one lunar_calendar row')
    check(CODE in records(), 'public LIFE catalog includes lunar_calendar')
    check_tool_metadata(check, records()[CODE], 'public lunar_calendar', expected=defaults, public=True)
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'admin lunar_calendar detail', defaults)
    check_tool_metadata(check, records(ADMIN_FILTER, admin)[CODE], 'admin lunar_calendar list', defaults)
    edited = dict(defaults, name=u'万年历验收', description=u'后台编辑的万年历说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'calendar-admin'], sortOrder=624,
                  version=2, featured=True, requiresLogin=True,
                  configJson='{"acceptance":"lunar_calendar"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'), 'edited lunar_calendar', edited)
    queries = ["SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
               "SELECT * FROM flyway_schema_history WHERE version='70'",
               "SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE,
               "SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE]
    snapshot = [sql(query) for query in queries]
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot,
          'V70 replay preserves operator edits, configuration, timestamps and usage')
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'),
                        'restored lunar_calendar', defaults)
    check(usage_counts() == ('0', '0'), 'fresh lunar_calendar has no synthetic opens')
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    check(usage_counts() == ('2', '2'), 'lunar_calendar increments daily and all-time counters twice')
    popular = call('tools/popular?limit=50')
    row = dict((entry['code'], entry) for entry in popular).get(CODE)
    check(row is not None and row['useCount'] == 2
          and all(row[key] == defaults[key] for key in ('name', 'description', 'icon')),
          'lunar_calendar popularity metadata and counters match')
    data = call('admin/statistics?days=7', token=admin)
    check_statistics(check, data, 7)
    ranks = [row for row in data['ranking'] if row['code'] == CODE]
    check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2,
          'lunar_calendar participates in 7-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify)
    else:
        raise SystemExit('Usage: python -B scripts/check_lunar_calendar_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
