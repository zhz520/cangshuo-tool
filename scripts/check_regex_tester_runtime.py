# -*- coding: utf-8 -*-
"""V55 regex catalog/admin/statistics acceptance on an isolated candidate.

Python 2/3 stdlib. Reuses check_admin_statistics_runtime.run for temporary
database/user/container setup and finally cleanup. Only the isolated schema
receives catalog edits, seed replays and synthetic usage; no execution API is used.
Checks Chinese/English Android resources, the fixed metadata contract, admin
read/write guards, disable/maintenance/re-enable/soft-delete, replay preservation,
daily/all-time counters, public popularity and 7/30/90-day aggregate statistics.
Usage on the deployment host: python scripts/check_regex_tester_runtime.py <candidate-image>
"""
from __future__ import print_function
from datetime import datetime, timedelta
import json
import os
import sys
import xml.etree.ElementTree as ET
from check_admin_statistics_runtime import run


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODE = 'regex_tester'
CATALOG = 'tools?categoryCode=DEV&pageSize=100'
ADMIN_PATH = 'admin/tools/' + CODE
CONTRACT = {
    'code': CODE, 'name': u'正则表达式',
    'description': u'测试匹配、捕获分组与替换，支持选项、常用示例和结果复制。',
    'categoryCode': 'DEV', 'mode': 'LOCAL', 'icon': CODE, 'sortOrder': 640,
    'requiresLogin': False, 'featured': False, 'status': 'ENABLED', 'version': 1,
    'keywords': [u'正则', u'正则表达式', u'匹配', u'分组', u'替换', 'regex',
                 'regular expression', 'pattern', 'groups', 'replace'],
}


def check_android_metadata(check):
    resources = {}
    for locale in ('values', 'values-en'):
        path = os.path.join(ROOT, 'android/app/src/main/res', locale, 'strings.xml')
        resources[locale] = ET.parse(path).getroot()
        for field in ('name', 'description'):
            nodes = resources[locale].findall("string[@name='regex_tester_%s']" % field)
            check(len(nodes) == 1 and bool((nodes[0].text or '').strip()),
                  'Android ' + locale + ' has one nonempty regex ' + field)
            text = nodes[0].text
            check(text == CONTRACT[field] if locale == 'values' else text != CONTRACT[field],
                  'Android ' + locale + ' regex ' + field + ' matches its locale contract')
    arrays = resources['values'].findall("string-array[@name='regex_tester_keywords']")
    check(len(arrays) == 1, 'Android Chinese regex keywords resource exists once')
    check([item.text for item in arrays[0].findall('item')] == CONTRACT['keywords'],
          'Android Chinese keywords match the ordered regex contract')
    english = resources['values-en'].findall("string-array[@name='regex_tester_keywords']")
    check(len(english) <= 1 and (len(english) == 1 or arrays[0].get('translatable') == 'false'),
          'Android English keywords are explicit or use the shared nontranslatable array')
    effective = english[0] if english else arrays[0]
    # The API seed uses Chinese metadata; English may translate and reorder search aliases.
    aliases = [item.text for item in effective.findall('item')]
    check(bool(aliases) and all(item and item.strip() for item in aliases)
          and set(('regex', 'regular expression', 'groups', 'replace')).issubset(set(aliases))
          and any(item in aliases for item in ('match', 'pattern')),
          'Android English effective keywords cover regex matching, groups and replacement')


def check_tool_metadata(check, row, label, expected=None, public=False):
    """Public ToolResponse uses isFeatured; AdminToolResponse uses featured/configJson."""
    expected = CONTRACT if expected is None else expected
    display = ('name', 'description', 'keywords')
    registration = ('code', 'categoryCode', 'mode', 'icon', 'sortOrder', 'requiresLogin', 'status', 'version')
    check(all(row.get(key) == expected[key] for key in display), label + ' display metadata')
    check(all(row.get(key) == expected[key] for key in registration), label + ' registration')
    feature_key = 'isFeatured' if public else 'featured'
    check(row.get(feature_key) is expected['featured'], label + ' ' + feature_key)
    if public:
        check(not any(key in row for key in ('featured', 'id', 'categoryName', 'configJson', 'updatedAt')),
              label + ' public DTO fields')
    elif 'configJson' in expected:
        check(json.loads(row['configJson']) == json.loads(expected['configJson']), label + ' configuration')


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
          label + ' daily and all-time totals are consistent')


def verify(check, request, sql, admin):
    check_android_metadata(check)
    check(sql("SELECT COUNT(*) FROM flyway_schema_history WHERE version='55' AND success=1") == '1',
          'V55 applied')
    with open(os.path.join(ROOT, 'server/src/main/resources/db/migration/V55__seed_regex_tester_tool.sql'), 'rb') as stream:
        seed = stream.read().decode('utf-8')

    def call(path, payload=None, token=None, method=None, status=200, code=0):
        actual, body, cache = request(path, payload, token=token, method=method)
        check(actual == status and body.get('code') == code, (method or 'GET') + ' ' + path)
        if path.startswith('admin/statistics') and status == 200:
            check(cache == 'no-store', 'statistics responses forbid caching')
        return body.get('data')

    def records(path=CATALOG, token=None):
        return dict((row['code'], row) for row in call(path, token=token)['records'])

    def statistics(days=7):
        return call('admin/statistics?days=' + str(days), token=admin)

    def usage_counts():
        return tuple(sql("SELECT COALESCE(SUM(use_count),0) FROM %s WHERE tool_code='regex_tester'" % table)
                     for table in ('tool_usage_daily', 'tool_usage_stat'))

    def replay(label):
        # Includes every catalog field, the primary key and creation/update/deletion timestamps.
        snapshot = sql("SELECT * FROM tool_definition WHERE tool_code='regex_tester'")
        sql(seed)
        sql(seed)
        check(sql("SELECT * FROM tool_definition WHERE tool_code='regex_tester'") == snapshot, label)
        check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='regex_tester'") == '1',
              'seed replay keeps exactly one regex row')

    catalog = records()
    check(CODE in catalog, 'public DEV catalog includes regex tester')
    check_tool_metadata(check, catalog[CODE], 'public regex', public=True)
    defaults = dict(CONTRACT, configJson='{}')
    for method, payload, path in (('GET', None, ADMIN_PATH), ('PUT', defaults, ADMIN_PATH),
                                  ('PATCH', {'status': 'MAINTENANCE'}, ADMIN_PATH + '/status'),
                                  ('DELETE', None, ADMIN_PATH)):
        call(path, payload, method=method, status=401, code=10002)
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'admin regex', defaults)
    categories = call('admin/tools/categories', token=admin)
    check(any(row['code'] == 'DEV' and row['enabled'] for row in categories), 'admin editor offers DEV category')
    filtered = 'admin/tools?categoryCode=DEV&keyword=regex_tester&status=ENABLED&pageSize=100'
    check(CODE in records(filtered, admin), 'admin filters find the enabled regex tester')
    replay('fresh seed replay preserves default metadata and timestamps')
    check(usage_counts() == ('0', '0'), 'new regex tool has no synthetic usage yet')
    before = dict((days, statistics(days)) for days in (7, 30, 90))
    for unused in range(2):
        call('tools/' + CODE + '/usage', method='POST')
    check(usage_counts() == ('2', '2'), 'daily and all-time regex counters each increment twice')
    check(sql("SELECT COALESCE(SUM(use_count),0) FROM tool_usage_daily WHERE tool_code='regex_tester' "
              "AND usage_date=DATE(UTC_TIMESTAMP(3)+INTERVAL 8 HOUR)") == '2', 'regex daily usage uses Beijing date')
    popular = dict((row['code'], row) for row in call('tools/popular?limit=50'))
    check(CODE in popular and popular[CODE]['useCount'] == 2, 'public popular list includes regex usage')
    for days in (7, 30, 90):
        data = statistics(days)
        check_statistics(check, data, days)
        ranking = dict((row['code'], row) for row in data['ranking'])
        check(CODE in ranking and ranking[CODE]['opens'] == 2 and ranking[CODE]['allTimeOpens'] == 2
              and ranking[CODE]['name'] == CONTRACT['name'] and ranking[CODE]['status'] == 'ENABLED',
              'regex daily and all-time ranking for ' + str(days) + ' days')
        check(all(data['summary'][key] == before[days]['summary'][key] + 2
                  for key in ('allTimeOpens', 'windowOpens', 'todayOpens')),
              'regex usage participates in aggregate totals for ' + str(days) + ' days')
        for group, group_code in (('categories', 'DEV'), ('modes', 'LOCAL')):
            old = next(row for row in before[days][group] if row['code'] == group_code)
            current = next(row for row in data[group] if row['code'] == group_code)
            check(current['opens'] == old['opens'] + 2 and current['count'] == old['count'],
                  'regex usage participates in ' + group + ' for ' + str(days) + ' days')

    edited = dict(defaults, name=u'正则目录验收', description=u'后台编辑的功能说明。',
                  icon='regex_admin_check', keywords=[u'验收', 'regex-admin'], sortOrder=641,
                  version=2, featured=True, requiresLogin=True, configJson='{"acceptance":"regex_tester"}')
    public_edit = dict((key, edited[key]) for key in CONTRACT)
    check_tool_metadata(check, call(ADMIN_PATH, edited, token=admin, method='PUT'), 'edited regex', edited)
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'persisted regex edit', edited)
    check_tool_metadata(check, records()[CODE], 'public regex edit', public_edit, public=True)
    replay('seed replay preserves operator edits and configuration')
    call(ADMIN_PATH + '/status', {'status': 'INVALID'}, token=admin, method='PATCH', status=400, code=10001)
    call(ADMIN_PATH, dict(edited, configJson='[]'), token=admin, method='PUT', status=400, code=10001)
    check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'invalid edits leave regex intact', edited)

    for state, counter in (('DISABLED', 'disabledTools'), ('MAINTENANCE', 'maintenanceTools')):
        state_metadata = dict(edited, status=state)
        check_tool_metadata(check, call(ADMIN_PATH + '/status', {'status': state}, token=admin, method='PATCH'),
                            'admin regex ' + state, state_metadata)
        check(CODE not in records(), state + ' regex hidden from public catalog')
        check(CODE not in [row['code'] for row in call('tools/popular?limit=50')], state + ' regex hidden from popularity')
        call('tools/' + CODE + '/usage', method='POST', status=404, code=10006)
        check(usage_counts() == ('2', '2'), state + ' regex rejects usage without changing either counter')
        replay('seed replay preserves ' + state + ' state and operator edits')
        check_tool_metadata(check, call(ADMIN_PATH, token=admin), 'persisted regex ' + state, state_metadata)
        state_filter = 'admin/tools?categoryCode=DEV&keyword=regex_tester&status=' + state + '&pageSize=100'
        check(CODE in records(state_filter, admin), 'admin can filter ' + state + ' regex')
        data = statistics()
        rank = next(row for row in data['ranking'] if row['code'] == CODE)
        check(rank['status'] == state and rank['opens'] == 2 and rank['allTimeOpens'] == 2,
              state + ' regex retains historical ranking')
        check(data['summary'][counter] == before[7]['summary'][counter] + 1
              and data['summary']['enabledTools'] == before[7]['summary']['enabledTools'] - 1,
              state + ' regex participates in tool state statistics')

    call(ADMIN_PATH + '/status', {'status': 'ENABLED'}, token=admin, method='PATCH')
    check_tool_metadata(check, records()[CODE], 're-enabled regex retains edits', public_edit, public=True)
    check(CODE in [row['code'] for row in call('tools/popular?limit=50')], 're-enabled regex returns to popularity')
    check_tool_metadata(check, call(ADMIN_PATH, defaults, token=admin, method='PUT'), 'restored regex', defaults)
    call(ADMIN_PATH, token=admin, method='DELETE')
    check(sql("SELECT COUNT(*) FROM tool_definition WHERE tool_code='regex_tester' AND deleted_at IS NOT NULL") == '1',
          'admin deletion is a soft delete')
    replay('seed replay preserves soft deletion and all catalog fields')
    call(ADMIN_PATH, token=admin, status=404, code=10006)
    call(ADMIN_PATH, defaults, token=admin, method='PUT', status=404, code=10006)
    call(ADMIN_PATH + '/status', {'status': 'ENABLED'}, token=admin, method='PATCH', status=404, code=10006)
    call('admin/tools', defaults, token=admin, method='POST', status=409, code=30004)
    check(CODE not in records(filtered, admin) and CODE not in records(), 'deleted regex absent from admin and public lists')
    check(CODE not in [row['code'] for row in call('tools/popular?limit=50')], 'deleted regex absent from popularity')
    call('tools/' + CODE + '/usage', method='POST', status=404, code=10006)
    check(usage_counts() == ('2', '2'), 'deleted regex keeps history and rejects new usage')
    data = statistics()
    check_statistics(check, data, 7)
    check(CODE not in [row['code'] for row in data['ranking']], 'deleted regex excluded from tool ranking')
    check(data['summary']['tools'] == before[7]['summary']['tools'] - 1
          and data['summary']['enabledTools'] == before[7]['summary']['enabledTools'] - 1
          and data['summary']['allTimeOpens'] == before[7]['summary']['allTimeOpens'] + 2
          and data['summary']['windowOpens'] == before[7]['summary']['windowOpens'] + 2
          and data['summary']['todayOpens'] == before[7]['summary']['todayOpens'] + 2,
          'soft deletion excludes the tool but retains aggregate historical opens')
    # Repository distributions join only non-deleted definitions; summary/daily retain all usage rows.
    for group, group_code in (('categories', 'DEV'), ('modes', 'LOCAL')):
        old = next(row for row in before[7][group] if row['code'] == group_code)
        current = next(row for row in data[group] if row['code'] == group_code)
        check(current['count'] == old['count'] - 1 and current['opens'] == old['opens'],
              'deleted regex excluded from ' + group + ' distribution')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: python scripts/check_regex_tester_runtime.py <candidate-image>')
    run(sys.argv[1], extra_checks=verify)
