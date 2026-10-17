# -*- coding: utf-8 -*-
"""V69 holiday_countdown static and isolated backend acceptance (Python 2/3).

Offline: python -B scripts/check_holiday_countdown_runtime.py --static
Deployment host: python -B scripts/check_holiday_countdown_runtime.py <candidate-image>
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
CODE = 'holiday_countdown'
PREFIX = 'holiday_'
CATALOG = 'tools?categoryCode=LIFE&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=LIFE&keyword=' + CODE + '&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V69__seed_holiday_countdown_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
RES_ROOT = os.path.join(ROOT, 'android/app/src/main/res')
FEATURE_ROOT = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/holiday')
LICENSE_PATH = os.path.join(ROOT, 'android/app/src/main/assets/licenses/lunar-java.txt')
KEYWORDS_ZH = [u'节日', u'倒数', u'倒计时', u'农历', u'春节', u'中秋', u'国庆', u'圣诞节',
               'holiday', 'countdown', 'festival', 'lunar']
KEYWORDS_EN = ['holiday', 'countdown', 'festival', 'lunar', 'spring festival', 'mid-autumn',
               'new year', 'christmas', u'节日', u'倒数', u'春节', u'中秋']
CONTRACT = {
    'code': CODE,
    'name': u'节日倒数',
    'description': u'倒数春节、中秋、国庆等中外节日，显示下一次日期、星期、农历、未来三次与实时倒计时。',
    'categoryCode': 'LIFE',
    'mode': 'LOCAL',
    'icon': CODE,
    'sortOrder': 622,
    'requiresLogin': False,
    'featured': False,
    'status': 'ENABLED',
    'version': 1,
    'keywords': KEYWORDS_ZH,
}
ENGLISH = {
    'name': 'Holiday Countdown',
    'description': 'Count down to Chinese and international holidays with the next date, weekday, lunar date, '
                   'three future occurrences and a live timer.',
    'keywords': KEYWORDS_EN,
}


def read_optional(path):
    try:
        return read_source(path)
    except (IOError, OSError):
        return None


def check_seed_metadata(check):
    source = read_optional(SEED_PATH)
    check(source is not None, 'V69 catalog seed exists')
    if source is None:
        return
    body = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('--'))
    columns = re.search(r'\A\s*INSERT INTO tool_definition\s*\(([^)]+)\)\s*VALUES\s*\(', body)
    check(columns is not None and [item.strip() for item in columns.group(1).split(',')] == [
        'tool_code', 'name', 'description', 'category_id', 'icon', 'keywords_json',
        'mode', 'requires_login', 'status', 'version', 'sort_order', 'is_featured'],
        'V69 only seeds existing catalog columns')
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'LIFE', CODE]
          + KEYWORDS_ZH + ['LOCAL', 'ENABLED'],
          'V69 display metadata and ordered Chinese keywords match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'LIFE'\)", body) is not None,
          'V69 category resolves LIFE by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*622\s*,\s*0\s*\)", body) is not None,
          'V69 mode, login, status, version, sort and featured defaults match')
    check(body.count('INSERT INTO tool_definition') == 1 and body.count(';') == 1
          and re.search(r'ON DUPLICATE KEY UPDATE\s+tool_code\s*=\s*tool_code\s*;\s*\Z', body) is not None,
          'V69 is one metadata insert with a no-op duplicate-key replay')


def android_string_references():
    references = set()
    if not os.path.isdir(FEATURE_ROOT):
        return None
    for current, unused, names in os.walk(FEATURE_ROOT):
        for name in names:
            if not name.endswith('.kt'):
                continue
            source = read_optional(os.path.join(current, name)) or ''
            references.update(re.findall(r'R\.string\.(holiday_[A-Za-z0-9_]+)', source))
    return references


def check_android_metadata(check):
    references = android_string_references()
    check(references is not None and {'holiday_name', 'holiday_description'}.issubset(references),
          'Android holiday feature references bilingual metadata resources')
    translations = {}
    for locale, expected, keywords in (('values', CONTRACT, KEYWORDS_ZH), ('values-en', ENGLISH, KEYWORDS_EN)):
        path = os.path.join(RES_ROOT, locale, 'strings.xml')
        source = read_optional(path)
        check(source is not None, 'Android ' + locale + ' holiday resources exist')
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
              'Android ' + locale + ' holiday strings are unique and nonempty')
        translations[locale] = dict((node.get('name'), ''.join(node.itertext())) for node in strings)
        for field in ('name', 'description'):
            check(translations[locale].get(PREFIX + field) == expected[field],
                  'Android ' + locale + ' holiday ' + field + ' matches')
        arrays = resources.findall("string-array[@name='holiday_keywords']")
        check(len(arrays) == 1, 'Android ' + locale + ' holiday keywords exist once')
        if len(arrays) == 1:
            actual = [(node.text or '').strip() for node in arrays[0].findall('item')]
            check(actual == keywords and len(set(actual)) == len(actual) and all(actual),
                  'Android ' + locale + ' ordered holiday keywords match')
        check(not sorted(references - set(translations[locale])),
              'Android ' + locale + ' covers every referenced holiday string')
    if 'values' in translations and 'values-en' in translations:
        check(set(translations['values']) == set(translations['values-en']),
              'Android holiday Chinese and English string keys match')

        def placeholders(value):
            return sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value))

        check(all(placeholders(value) == placeholders(translations['values-en'][name])
                  for name, value in translations['values'].items()),
              'Android holiday bilingual format placeholders match')
        check(plural_names('values') == plural_names('values-en'),
              'Android holiday bilingual plural keys match')


def plural_names(locale):
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
            owners.extend([os.path.normpath(path)] * len(re.findall(r'\bcode\s*=\s*"holiday_countdown"', source)))
    return owners


def check_android_registration(check):
    owners = definition_owners()
    check(len(owners) == 1, 'Android holiday_countdown code has exactly one ToolDefinition owner')
    if not owners:
        return
    definition_path = owners[0]
    definition = read_optional(definition_path)
    check(definition is not None, 'Android holiday_countdown ToolDefinition is readable')
    if definition is None:
        return
    for field, pattern in (
        ('code', r'code\s*=\s*"holiday_countdown"'),
        ('name', r'name\s*=\s*resources\.getString\(R\.string\.holiday_name\)'),
        ('description', r'description\s*=\s*resources\.getString\(R\.string\.holiday_description\)'),
        ('category', r'category\s*=\s*ToolCategory\.LIFE'),
        ('mode', r'mode\s*=\s*ToolMode\.LOCAL'),
        ('icon', r'icon\s*=\s*"holiday_countdown"'),
        ('keywords', r'keywords\s*=\s*resources\.getStringArray\(R\.array\.holiday_keywords\)\.toList\(\)'),
        ('sort', r'sortOrder\s*=\s*622\b'),
        ('featured', r'isFeatured\s*=\s*false\b'),
    ):
        check(len(re.findall(pattern, definition)) == 1,
              'Android holiday_countdown ToolDefinition ' + field)
    check(re.search(r'override\s+val\s+requiredPermissions\s*:\s*List<String>\s*=\s*emptyList\(\)', definition)
          is not None, 'Android holiday_countdown declares no extra runtime permissions')
    container = read_optional(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt'))
    check(container is not None, 'Android bundled ToolRegistry source exists')
    if container is not None:
        registry = re.search(r'private fun bundledRegistry\b[\s\S]+?(?=private val registryStore)', container)
        check(registry is not None and 'ToolRegistry(' in registry.group(0)
              and len(re.findall(r'\bHolidayCountdownToolDefinition\s*\(', registry.group(0))) == 1,
              'Android bundled ToolRegistry registers holiday_countdown exactly once')
    icons = read_optional(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/home/ui/HomePresentation.kt'))
    check(icons is not None and len(re.findall(r'"holiday_countdown"\s*->', icons)) == 1
          and re.search(r'"holiday_countdown"\s*->\s*R\.drawable\.ic_holiday_countdown\b', icons) is not None,
          'Android holiday_countdown has one explicit icon mapping')
    icon_source = read_optional(os.path.join(RES_ROOT, 'drawable/ic_holiday_countdown.xml'))
    check(icon_source is not None, 'Android holiday_countdown vector icon exists')
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
              'Android holiday_countdown ships a nonempty 24dp vector icon')


def check_dependency_and_domain(check):
    catalog = read_optional(os.path.join(ROOT, 'android/gradle/libs.versions.toml')) or ''
    check(re.search(r'^lunar\s*=\s*"1\.7\.7"', catalog, re.M) is not None
          and 'cn.6tail' in catalog and 'name = "lunar"' in catalog,
          'Android pins lunar-java 1.7.7')
    build = read_optional(os.path.join(ROOT, 'android/app/build.gradle.kts')) or ''
    check('implementation(libs.lunar.calendar)' in build, 'Android app includes lunar-java')
    license_source = read_optional(LICENSE_PATH) or ''
    check('MIT License' in license_source and '2018 6tail' in license_source,
          'lunar-java MIT license is bundled')
    licenses = read_optional(os.path.join(RES_ROOT, 'values', 'strings.xml')) or ''
    check('lunar-java 1.7.7' in licenses and 'assets/licenses/lunar-java.txt' in licenses,
          'license page names lunar-java and bundled text')
    models = read_optional(os.path.join(FEATURE_ROOT, 'domain/HolidayModels.kt')) or ''
    enum_body = re.search(r'enum class HolidayId\([\s\S]+?\n\}', models)
    entries = re.findall(r'^\s{4}([A-Z][A-Z0-9_]+)\(', enum_body.group(0), re.M) if enum_body else []
    check(len(entries) == len(set(entries)) == 22, 'Android ships 22 unique built-in holidays')
    for required in ('SPRING_FESTIVAL', 'QINGMING', 'MID_AUTUMN_FESTIVAL', 'NEW_YEAR', 'CHRISTMAS'):
        check(required in entries, 'Android holiday whitelist contains ' + required)
    check('ZoneId.of("Asia/Shanghai")' in models, 'holiday countdown uses Beijing calendar zone')
    repository = read_optional(os.path.join(FEATURE_ROOT, 'data/LunarHolidayRepository.kt')) or ''
    check('Lunar.fromYmd' in repository and 'jieQiTable' in repository and 'solar' in repository,
          'holiday repository uses lunar-java conversion and solar-term table')


def check_static(check):
    check_seed_metadata(check)
    check_android_metadata(check)
    check_android_registration(check)
    check_dependency_and_domain(check)


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
    print('Holiday Countdown offline static acceptance:', checks[0] - len(failures), '/', checks[0],
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

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='69' AND success=1") == '1', 'V69 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V69 creates one holiday_countdown row')
    check(CODE in records(), 'public LIFE catalog includes holiday_countdown')
    check_tool_metadata(check, records()[CODE], 'public holiday_countdown', expected=defaults, public=True)
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'admin holiday_countdown detail', defaults)
    check_tool_metadata(check, records(ADMIN_FILTER, admin)[CODE], 'admin holiday_countdown list', defaults)
    categories = call('admin/tools/categories', token=admin)
    check(len([row for row in categories if row['code'] == 'LIFE' and row['enabled']]) == 1,
          'admin editor offers the enabled LIFE category')
    edited = dict(defaults, name=u'节日倒数验收', description=u'后台编辑的节日倒数说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'holiday-admin'], sortOrder=623,
                  version=2, featured=True, requiresLogin=True,
                  configJson='{"acceptance":"holiday_countdown"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'),
                        'edited holiday_countdown', edited)
    queries = ["SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
               "SELECT * FROM flyway_schema_history WHERE version='69'",
               "SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE,
               "SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE]
    snapshot = [sql(query) for query in queries]
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot,
          'V69 replay preserves operator edits, configuration, timestamps and usage')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V69 replay keeps one holiday_countdown row, including deleted rows')
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'replayed holiday_countdown', edited)
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'),
                        'restored holiday_countdown', defaults)
    check(usage_counts() == ('0', '0'), 'fresh holiday_countdown has no synthetic opens')
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    check(usage_counts() == ('2', '2'), 'holiday_countdown increments daily and all-time counters twice')
    check(sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s' "
              "AND usage_date=DATE(UTC_TIMESTAMP(3)+INTERVAL 8 HOUR)" % CODE) == '2',
          'holiday_countdown daily opens use the Beijing date')
    popular = call('tools/popular?limit=50')
    row = dict((entry['code'], entry) for entry in popular).get(CODE)
    check(row is not None and row['useCount'] == 2
          and all(row[key] == defaults[key] for key in ('name', 'description', 'icon')),
          'holiday_countdown popularity metadata and counters match')
    data = call('admin/statistics?days=7', token=admin)
    check_statistics(check, data, 7)
    ranks = [row for row in data['ranking'] if row['code'] == CODE]
    check(len(ranks) == 1 and ranks[0]['opens'] == ranks[0]['allTimeOpens'] == 2
          and ranks[0]['name'] == defaults['name'] and ranks[0]['status'] == 'ENABLED',
          'holiday_countdown participates in 7-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify)
    else:
        raise SystemExit('Usage: python -B scripts/check_holiday_countdown_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
