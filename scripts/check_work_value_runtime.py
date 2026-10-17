# -*- coding: utf-8 -*-
"""V71 work_value static and isolated backend acceptance (Python 2/3)."""
from __future__ import print_function
import os
import re
import sys
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
from check_admin_statistics_runtime import run
from check_color_picker_runtime import check_catalog_page, check_statistics, check_tool_metadata, read_source


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODE = 'work_value'
CATALOG = 'tools?categoryCode=CALC&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
ADMIN_FILTER = 'admin/tools?categoryCode=CALC&keyword=' + CODE + '&status=ENABLED&pageSize=100'
SEED_PATH = os.path.join(ROOT, 'server/src/main/resources/db/migration/V71__seed_work_value_tool.sql')
JAVA_ROOT = os.path.join(ROOT, 'android/app/src/main/java')
RES_ROOT = os.path.join(ROOT, 'android/app/src/main/res')
FEATURE_ROOT = os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/workvalue')
KEYWORDS_ZH = [u'工作性价比', u'时薪', u'工资', u'月薪', u'通勤', u'加班', u'年收入', u'工作',
               'salary', 'hourly', 'commute', 'work']
KEYWORDS_EN = ['work value', 'hourly rate', 'salary', 'wage', 'commute', 'overtime',
               'annual income', 'work', u'工作性价比', u'时薪', u'工资', u'通勤']
CONTRACT = {
    'code': CODE,
    'name': u'工作性价比',
    'description': u'综合月薪、发薪月数、奖金福利、工作支出、工时与通勤，计算名义时薪和真实时薪。',
    'categoryCode': 'CALC',
    'mode': 'LOCAL',
    'icon': CODE,
    'sortOrder': 58,
    'requiresLogin': False,
    'featured': False,
    'status': 'ENABLED',
    'version': 1,
    'keywords': KEYWORDS_ZH,
}
ENGLISH = {
    'name': 'Work Value',
    'description': 'Combine salary, paid months, bonuses, benefits, work costs, hours and commuting to calculate '
                   'nominal and real hourly value.',
    'keywords': KEYWORDS_EN,
}


def read_optional(path):
    try:
        return read_source(path)
    except (IOError, OSError):
        return None


def check_seed(check):
    source = read_optional(SEED_PATH)
    check(source is not None, 'V71 catalog seed exists')
    if source is None:
        return
    body = '\n'.join(line for line in source.splitlines() if not line.lstrip().startswith('--'))
    strings = [item.replace("''", "'") for item in re.findall(r"'((?:''|[^'])*)'", body)]
    check(strings == [CODE, CONTRACT['name'], CONTRACT['description'], 'CALC', CODE]
          + KEYWORDS_ZH + ['LOCAL', 'ENABLED'],
          'V71 display metadata and ordered Chinese keywords match')
    check(re.search(r"\(SELECT\s+id\s+FROM\s+tool_category\s+WHERE\s+code\s*=\s*'CALC'\)", body)
          is not None, 'V71 category resolves CALC by code')
    check(re.search(r"'LOCAL'\s*,\s*0\s*,\s*'ENABLED'\s*,\s*1\s*,\s*58\s*,\s*0\s*\)", body)
          is not None, 'V71 mode, login, status, version, sort and featured defaults match')
    check(body.count('INSERT INTO tool_definition') == 1 and body.count(';') == 1,
          'V71 is one metadata insert')


def check_android(check):
    references = set()
    for current, unused, names in os.walk(FEATURE_ROOT):
        for name in names:
            if name.endswith('.kt'):
                source = read_optional(os.path.join(current, name)) or ''
                references.update(re.findall(r'R\.string\.(work_value_[A-Za-z0-9_]+)', source))
    check({'work_value_name', 'work_value_description'}.issubset(references),
          'Android work_value feature references metadata resources')
    translations = {}
    for locale, expected, keywords in (('values', CONTRACT, KEYWORDS_ZH), ('values-en', ENGLISH, KEYWORDS_EN)):
        source = read_optional(os.path.join(RES_ROOT, locale, 'strings.xml'))
        check(source is not None, 'Android ' + locale + ' work_value resources exist')
        if source is None:
            continue
        resources = ET.fromstring(source.encode('utf-8'))
        strings = [node for node in resources.findall('string') if node.get('name', '').startswith('work_value_')]
        names = [node.get('name') for node in strings]
        translations[locale] = dict((node.get('name'), ''.join(node.itertext())) for node in strings)
        check(len(names) == len(set(names)) and all(''.join(node.itertext()).strip() for node in strings),
              'Android ' + locale + ' work_value strings are unique and nonempty')
        check(translations[locale].get('work_value_name') == expected['name']
              and translations[locale].get('work_value_description') == expected['description'],
              'Android ' + locale + ' work_value metadata matches')
        arrays = resources.findall("string-array[@name='work_value_keywords']")
        actual = [(node.text or '').strip() for node in arrays[0].findall('item')] if arrays else []
        check(len(arrays) == 1 and actual == keywords and len(set(actual)) == len(actual),
              'Android ' + locale + ' ordered work_value keywords match')
        check(not sorted(references - set(translations[locale])),
              'Android ' + locale + ' covers referenced work_value strings')
    if 'values' in translations and 'values-en' in translations:
        check(set(translations['values']) == set(translations['values-en']),
              'Android work_value Chinese and English string keys match')

        def placeholders(value):
            return sorted(re.findall(r'%(?:\d+\$)?[a-zA-Z%]', value))

        check(all(placeholders(value) == placeholders(translations['values-en'][name])
                  for name, value in translations['values'].items()),
              'Android work_value bilingual placeholders match')


def check_registration(check):
    definition = read_optional(os.path.join(FEATURE_ROOT, 'WorkValueToolDefinition.kt')) or ''
    for token in ('code = "work_value"', 'ToolCategory.CALC', 'ToolMode.LOCAL',
                  'icon = "work_value"', 'sortOrder = 58', 'isFeatured = false'):
        check(token in definition, 'Android work_value ToolDefinition contains ' + token)
    check('emptyList()' in definition, 'Android work_value declares no runtime permissions')
    container = read_optional(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/ToolboxAppContainer.kt')) or ''
    check(container.count('WorkValueToolDefinition(') == 1, 'Android registers work_value exactly once')
    icons = read_optional(os.path.join(JAVA_ROOT, 'com/cangshuo/toolbox/feature/home/ui/HomePresentation.kt')) or ''
    check(re.search(r'"work_value"\s*->\s*R\.drawable\.ic_work_value\b', icons) is not None,
          'Android work_value has one icon mapping')
    icon_path = os.path.join(RES_ROOT, 'drawable/ic_work_value.xml')
    check(os.path.exists(icon_path), 'Android work_value vector icon exists')
    domain = read_optional(os.path.join(FEATURE_ROOT, 'domain/WorkValueModels.kt')) or ''
    for token in ('annualCash', 'commuteHoursPerYear', 'nominalHourly', 'netRealHourly', 'minutesPerHundredCash'):
        check(token in domain, 'work_value domain computes ' + token)


def check_static(check):
    check_seed(check)
    check_android(check)
    check_registration(check)


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
    print('Work Value offline static acceptance:', checks[0] - len(failures), '/', checks[0],
          'checks passed;', len(failures), 'failed; no network, Docker, database or Android runtime')
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

    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='71' AND success=1") == '1', 'V71 applied')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='%s'" % CODE) == '1',
          'V71 creates one work_value row')
    check(CODE in records(), 'public CALC catalog includes work_value')
    check_tool_metadata(check, records()[CODE], 'public work_value', expected=defaults, public=True)
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'admin work_value detail', defaults)
    check_tool_metadata(check, records(ADMIN_FILTER, admin)[CODE], 'admin work_value list', defaults)
    edited = dict(defaults, name=u'工作性价比验收', description=u'后台编辑的工作性价比说明。',
                  icon=CODE + '_admin_check', keywords=[u'验收', 'work-value-admin'], sortOrder=59,
                  version=2, featured=True, requiresLogin=True,
                  configJson='{"acceptance":"work_value"}')
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'), 'edited work_value', edited)
    queries = ["SELECT * FROM tool_definition WHERE tool_code='%s'" % CODE,
               "SELECT * FROM flyway_schema_history WHERE version='71'",
               "SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE,
               "SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE]
    snapshot = [sql(query) for query in queries]
    sql(source)
    sql(source)
    check([sql(query) for query in queries] == snapshot,
          'V71 replay preserves operator edits, configuration, timestamps and usage')
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'),
                        'restored work_value', defaults)
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    daily = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='%s'" % CODE)
    total = sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_stat WHERE tool_code='%s'" % CODE)
    check(daily == total == '2', 'work_value increments daily and all-time usage')
    popular = dict((row['code'], row) for row in call('tools/popular?limit=50')).get(CODE)
    check(popular is not None and popular['useCount'] == 2, 'work_value participates in popularity')
    data = call('admin/statistics?days=7', token=admin)
    check_statistics(check, data, 7)
    ranks = [row for row in data['ranking'] if row['code'] == CODE]
    check(len(ranks) == 1 and ranks[0]['opens'] == 2, 'work_value participates in 7-day ranking')


def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--static':
        offline_acceptance()
    elif len(sys.argv) == 2 and not sys.argv[1].startswith('-'):
        run(sys.argv[1], extra_checks=verify)
    else:
        raise SystemExit('Usage: python -B scripts/check_work_value_runtime.py --static | <candidate-image>')


if __name__ == '__main__':
    main()
